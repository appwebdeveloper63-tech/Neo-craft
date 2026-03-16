package com.neocraft.game.world

import android.content.Context
import java.io.*

/**
 * World persistence — saves/loads chunk data and player state to internal storage.
 *
 * Layout: <filesDir>/worlds/<worldName>/
 *   chunks/  c_<cx>_<cz>.bin   – raw ByteArray per chunk (CHUNK_WIDTH² × CHUNK_HEIGHT)
 *   player.dat                  – binary player state
 *   world.dat                   – world metadata (seed, time, version)
 */
object SaveSystem {

    private const val TAG = "NeoCraftSave"

    // ── World metadata ────────────────────────────────────────────────────
    data class WorldMeta(
        val seed: Long      = 42L,
        val totalTime: Float = 0f,
        val version: Int    = VERSION_CODE
    )

    // ── Player state ──────────────────────────────────────────────────────
    data class PlayerState(
        val x: Float = 8.5f, val y: Float = 80f, val z: Float = 8.5f,
        val yaw: Float = 0f, val pitch: Float = 0f,
        val health: Int = 20, val hunger: Int = 20,
        val xpLevel: Int = 0, val xpPoints: Int = 0,
        val selectedSlot: Int = 0,
        val inventoryData: IntArray = IntArray(0)
    )

    private fun worldDir(ctx: Context, name: String) =
        File(ctx.filesDir, "worlds/$name").also { it.mkdirs() }
    private fun chunkDir(ctx: Context, name: String) =
        File(worldDir(ctx, name), "chunks").also { it.mkdirs() }
    private fun chunkFile(dir: File, cx: Int, cz: Int) = File(dir, "c_${cx}_${cz}.bin")

    // ── Chunk I/O ─────────────────────────────────────────────────────────
    fun saveChunk(ctx: Context, worldName: String, cx: Int, cz: Int, data: ByteArray) {
        try {
            val f = chunkFile(chunkDir(ctx, worldName), cx, cz)
            DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { dos ->
                dos.writeInt(data.size)
                dos.write(data)
            }
        } catch (e: Exception) { /* non-fatal */ }
    }

    fun loadChunk(ctx: Context, worldName: String, cx: Int, cz: Int): ByteArray? {
        return try {
            val f = chunkFile(chunkDir(ctx, worldName), cx, cz)
            if (!f.exists()) return null
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { dis ->
                val size = dis.readInt()
                val buf  = ByteArray(size)
                dis.readFully(buf)
                buf
            }
        } catch (e: Exception) { null }
    }

    fun chunkExists(ctx: Context, worldName: String, cx: Int, cz: Int): Boolean =
        chunkFile(chunkDir(ctx, worldName), cx, cz).exists()

    // ── Player I/O ────────────────────────────────────────────────────────
    fun savePlayer(ctx: Context, worldName: String, state: PlayerState) {
        try {
            val f = File(worldDir(ctx, worldName), "player.dat")
            DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { dos ->
                dos.writeFloat(state.x); dos.writeFloat(state.y); dos.writeFloat(state.z)
                dos.writeFloat(state.yaw); dos.writeFloat(state.pitch)
                dos.writeInt(state.health); dos.writeInt(state.hunger)
                dos.writeInt(state.xpLevel); dos.writeInt(state.xpPoints)
                dos.writeInt(state.selectedSlot)
                dos.writeInt(state.inventoryData.size)
                for (v in state.inventoryData) dos.writeInt(v)
            }
        } catch (e: Exception) { /* non-fatal */ }
    }

    fun loadPlayer(ctx: Context, worldName: String): PlayerState? {
        return try {
            val f = File(worldDir(ctx, worldName), "player.dat")
            if (!f.exists()) return null
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { dis ->
                val x = dis.readFloat(); val y = dis.readFloat(); val z = dis.readFloat()
                val yaw = dis.readFloat(); val pitch = dis.readFloat()
                val hp = dis.readInt(); val hunger = dis.readInt()
                val xpL = dis.readInt(); val xpP = dis.readInt()
                val sel = dis.readInt()
                val invSize = dis.readInt()
                val inv = IntArray(invSize) { dis.readInt() }
                PlayerState(x,y,z,yaw,pitch,hp,hunger,xpL,xpP,sel,inv)
            }
        } catch (e: Exception) { null }
    }

    // ── World metadata I/O ────────────────────────────────────────────────
    fun saveMeta(ctx: Context, worldName: String, meta: WorldMeta) {
        try {
            val f = File(worldDir(ctx, worldName), "world.dat")
            DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { dos ->
                dos.writeLong(meta.seed)
                dos.writeFloat(meta.totalTime)
                dos.writeInt(meta.version)
            }
        } catch (e: Exception) { /* non-fatal */ }
    }

    fun loadMeta(ctx: Context, worldName: String): WorldMeta? {
        return try {
            val f = File(worldDir(ctx, worldName), "world.dat")
            if (!f.exists()) return null
            DataInputStream(BufferedInputStream(FileInputStream(f))).use { dis ->
                val seed = dis.readLong()
                val time = dis.readFloat()
                val ver  = dis.readInt()
                if (ver != VERSION_CODE) return null  // Version mismatch — regenerate
                WorldMeta(seed, time, ver)
            }
        } catch (e: Exception) { null }
    }

    fun worldExists(ctx: Context, worldName: String): Boolean =
        File(ctx.filesDir, "worlds/$worldName/world.dat").exists()

    fun listWorlds(ctx: Context): List<String> =
        File(ctx.filesDir, "worlds").listFiles()?.map { it.name } ?: emptyList()

    fun deleteWorld(ctx: Context, worldName: String) {
        worldDir(ctx, worldName).deleteRecursively()
    }
}
