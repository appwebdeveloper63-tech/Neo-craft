package com.neocraft.game.world

import android.content.Context
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class World {
    private val chunkData   = ConcurrentHashMap<Long, ByteArray>()
    private val chunkDirty  = ConcurrentHashMap<Long, Boolean>()
    private val generating  = ConcurrentHashMap<Long, Boolean>()
    private val scope       = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var context: Context? = null
    var worldName: String = "default"

    companion object {
        fun chunkKey(cx: Int, cz: Int): Long =
            (cx.toLong() and 0xFFFFF) or ((cz.toLong() and 0xFFFFF) shl 20)
        fun worldToChunk(w: Int) = Math.floorDiv(w, CHUNK_WIDTH)
        fun worldToLocal(w: Int) = Math.floorMod(w, CHUNK_WIDTH)
    }

    // ── Block access ──────────────────────────────────────────────────────
    fun getBlock(wx: Int, wy: Int, wz: Int): Int {
        if (wy < 0) return BlockType.BEDROCK
        if (wy >= CHUNK_HEIGHT) return BlockType.AIR
        val data = chunkData[chunkKey(worldToChunk(wx), worldToChunk(wz))] ?: return BlockType.AIR
        return WorldGen.get(data, worldToLocal(wx), wy, worldToLocal(wz))
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, type: Int) {
        if (wy < 0 || wy >= CHUNK_HEIGHT) return
        val cx = worldToChunk(wx); val cz = worldToChunk(wz)
        val data = chunkData[chunkKey(cx, cz)] ?: return
        WorldGen.set(data, worldToLocal(wx), wy, worldToLocal(wz), type)
        // Dirty neighbouring chunks (for seamless mesh updates)
        for (dcx in -1..1) for (dcz in -1..1)
            chunkDirty[chunkKey(cx+dcx, cz+dcz)] = true
        // Schedule gravity check for blocks above
        if (BlockType.isGravity(getBlock(wx, wy+1, wz)))
            scheduleGravity(wx, wy+1, wz)
    }

    // ── Gravity simulation ─────────────────────────────────────────────────
    private val gravityQueue = ConcurrentHashMap<Long, Triple<Int,Int,Int>>()

    private fun gravKey(wx: Int, wy: Int, wz: Int): Long =
        ((wx.toLong() and 0xFFFFF) shl 40) or ((wy.toLong() and 0xFF) shl 20) or (wz.toLong() and 0xFFFFF)

    private fun scheduleGravity(wx: Int, wy: Int, wz: Int) {
        gravityQueue[gravKey(wx,wy,wz)] = Triple(wx,wy,wz)
    }

    fun tickGravity() {
        if (gravityQueue.isEmpty()) return
        val snapshot = gravityQueue.values.toList()
        gravityQueue.clear()
        for ((wx,wy,wz) in snapshot) {
            val blk = getBlock(wx, wy, wz)
            if (!BlockType.isGravity(blk)) continue
            val below = wy - 1
            if (below >= 0 && !BlockType.isSolid(getBlock(wx, below, wz))) {
                setBlock(wx, wy,    wz, BlockType.AIR)
                setBlock(wx, below, wz, blk)
                if (below > 0) scheduleGravity(wx, below, wz)
            }
        }
    }

    // ── Chunk management ──────────────────────────────────────────────────
    fun ensureChunkLoaded(cx: Int, cz: Int) {
        val key = chunkKey(cx, cz)
        if (chunkData.containsKey(key) || generating[key] == true) return
        generating[key] = true
        scope.launch {
            // Try loading from disk first
            val ctx = context
            val saved = if (ctx != null && SaveSystem.chunkExists(ctx, worldName, cx, cz))
                SaveSystem.loadChunk(ctx, worldName, cx, cz) else null
            chunkData[key] = saved ?: WorldGen.generateChunk(cx, cz)
            chunkDirty[key] = true
            generating.remove(key)
        }
    }

    fun saveAllChunks() {
        val ctx = context ?: return
        for ((key, data) in chunkData) {
            val cz = ((key shr 20) and 0xFFFFF).toInt().let { if (it > 0x7FFFF) it - 0x100000 else it }
            val cx = (key and 0xFFFFF).toInt().let { if (it > 0x7FFFF) it - 0x100000 else it }
            SaveSystem.saveChunk(ctx, worldName, cx, cz, data)
        }
    }

    fun unloadDistantChunks(pcx: Int, pcz: Int, maxDist: Int = RENDER_DISTANCE + 3) {
        val toRemove = chunkData.keys.filter { key ->
            val cz = ((key shr 20) and 0xFFFFF).toInt().let { if (it > 0x7FFFF) it - 0x100000 else it }
            val cx = (key and 0xFFFFF).toInt().let { if (it > 0x7FFFF) it - 0x100000 else it }
            abs(cx - pcx) > maxDist || abs(cz - pcz) > maxDist
        }
        toRemove.forEach { chunkData.remove(it); chunkDirty.remove(it) }
    }

    fun isChunkReady(cx: Int, cz: Int)  = chunkData.containsKey(chunkKey(cx, cz))
    fun isChunkDirty(cx: Int, cz: Int)  = chunkDirty[chunkKey(cx, cz)] == true
    fun clearDirty(cx: Int, cz: Int)    { chunkDirty.remove(chunkKey(cx, cz)) }
    fun getChunkData(cx: Int, cz: Int)  = chunkData[chunkKey(cx, cz)]

    // ── Raycast ───────────────────────────────────────────────────────────
    data class RaycastResult(
        val hit: Boolean,
        val bx: Int=0, val by: Int=0, val bz: Int=0,
        val prevX: Int=0, val prevY: Int=0, val prevZ: Int=0,
        val faceNX: Int=0, val faceNY: Int=0, val faceNZ: Int=0
    )

    fun raycast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, maxDist: Float=6f): RaycastResult {
        var px=ox; var py=oy; var pz=oz
        var lx=Int.MIN_VALUE; var ly=0; var lz=0
        val step=0.04f; var d=0f
        while (d < maxDist) {
            val bx=Math.floor(px.toDouble()).toInt()
            val by=Math.floor(py.toDouble()).toInt()
            val bz=Math.floor(pz.toDouble()).toInt()
            if (BlockType.isSolid(getBlock(bx,by,bz)))
                return RaycastResult(true, bx,by,bz, lx,ly,lz, lx-bx, ly-by, lz-bz)
            lx=bx; ly=by; lz=bz
            px+=dx*step; py+=dy*step; pz+=dz*step; d+=step
        }
        return RaycastResult(false)
    }

    fun isSolidAt(x: Float, y: Float, z: Float) =
        BlockType.isSolid(getBlock(
            Math.floor(x.toDouble()).toInt(),
            Math.floor(y.toDouble()).toInt(),
            Math.floor(z.toDouble()).toInt()
        ))

    fun isLiquidAt(x: Float, y: Float, z: Float) =
        BlockType.isLiquid(getBlock(
            Math.floor(x.toDouble()).toInt(),
            Math.floor(y.toDouble()).toInt(),
            Math.floor(z.toDouble()).toInt()
        ))

    fun getBlockWorld(wx: Int, wy: Int, wz: Int) = getBlock(wx, wy, wz)

    fun destroy() { scope.cancel() }
}
