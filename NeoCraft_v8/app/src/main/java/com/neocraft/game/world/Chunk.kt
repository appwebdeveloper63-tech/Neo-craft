package com.neocraft.game.world

import android.opengl.GLES20
import com.neocraft.game.engine.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * NeoCraft v6 — Chunk mesh builder with GREEDY MESHING.
 *
 * Greedy meshing merges coplanar, same-block, same-AO adjacent faces into
 * a single large quad instead of one quad per block face. On a typical
 * chunk this reduces vertex count by 70-90%, cutting GPU draw time drastically.
 *
 * Algorithm (Mikola Lysenko):
 *  For each axis direction (+X,-X,+Y,-Y,+Z,-Z):
 *   For each layer perpendicular to that axis:
 *    Build a 2D "face mask" of (blockId, aoValue, faceType) per cell.
 *    Greedily merge rectangles of identical cells into one large quad.
 *
 * AO is still computed per-vertex via the Mikola Lysenko method;
 * a merged quad is only valid if all 4 corner AO values match.
 */
class Chunk(val cx: Int, val cz: Int) {

    private var vboOpaque = 0; private var iboOpaque = 0; var opaqueCount = 0
    private var vboWater  = 0; private var iboWater  = 0; var waterCount  = 0

    var needsRebuild = true
    var built        = false

    @Volatile var pendingOpaque:    FloatArray? = null
    @Volatile var pendingOpaqueIdx: IntArray?   = null
    @Volatile var pendingWater:     FloatArray? = null
    @Volatile var pendingWaterIdx:  IntArray?   = null
    @Volatile var pendingReady = false

    companion object {
        // Vertex layout: x,y,z, nx,ny,nz, u,v, ao  →  9 floats
        const val STRIDE = 9

        val NORMALS = arrayOf(
            floatArrayOf( 1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf( 0f, 1f, 0f), floatArrayOf( 0f,-1f, 0f),
            floatArrayOf( 0f, 0f, 1f), floatArrayOf( 0f, 0f,-1f)
        )
        val TILE_U = 1f / ATLAS_COLS
        val TILE_V = 1f / ATLAS_ROWS

        // AO per-vertex helper (Mikola Lysenko formula)
        private fun aoVal(s1: Boolean, s2: Boolean, corner: Boolean): Int = when {
            s1 && s2             -> 0
            (if(s1)1 else 0) + (if(s2)1 else 0) + (if(corner)1 else 0) >= 2 -> 1
            s1 || s2 || corner   -> 2
            else                 -> 3
        }
        private fun aoToFloat(v: Int) = when(v) { 0->0f; 1->0.4f; 2->0.7f; else->1f }
    }

    // ── Greedy mesh build ────────────────────────────────────────────────
    fun buildMesh(world: World) {
        val opV = FloatArrayBuilder(32768)
        val opI = IntArrayBuilder(16384)
        val wtV = FloatArrayBuilder(4096)
        val wtI = IntArrayBuilder(2048)
        var opVC = 0; var wtVC = 0

        val data = world.getChunkData(cx, cz) ?: run { pendingReady = true; return }
        val origX = cx * CHUNK_WIDTH; val origZ = cz * CHUNK_WIDTH

        // We iterate over all 6 face directions.
        // For each direction we sweep through slices perpendicular to that axis.
        //
        // Axis encoding:
        //  d=0 → +X (face 0)   d=1 → -X (face 1)
        //  d=2 → +Y (face 2)   d=3 → -Y (face 3)
        //  d=4 → +Z (face 4)   d=5 → -Z (face 5)
        //
        // For each direction, u/v axes are the two remaining axes.

        // Axis [d] → (main_dim, u_dim, v_dim, normal_sign)
        val axes = arrayOf(
            intArrayOf(0,2,1, 1),  // +X: main=X u=Z v=Y
            intArrayOf(0,2,1,-1),  // -X
            intArrayOf(1,0,2, 1),  // +Y: main=Y u=X v=Z
            intArrayOf(1,0,2,-1),  // -Y
            intArrayOf(2,0,1, 1),  // +Z: main=Z u=X v=Y
            intArrayOf(2,0,1,-1),  // -Z
        )
        val faceDims = intArrayOf(CHUNK_WIDTH, CHUNK_WIDTH, CHUNK_HEIGHT,
                                  CHUNK_HEIGHT, CHUNK_WIDTH, CHUNK_WIDTH)
        val sliceDims = arrayOf(
            intArrayOf(CHUNK_HEIGHT, CHUNK_WIDTH),  // +X: v=Y, u=Z
            intArrayOf(CHUNK_HEIGHT, CHUNK_WIDTH),
            intArrayOf(CHUNK_WIDTH, CHUNK_WIDTH),   // +Y: v=Z, u=X  (note: swapped for greedy)
            intArrayOf(CHUNK_WIDTH, CHUNK_WIDTH),
            intArrayOf(CHUNK_HEIGHT, CHUNK_WIDTH),  // +Z: v=Y, u=X
            intArrayOf(CHUNK_HEIGHT, CHUNK_WIDTH),
        )

        // Face type for atlas: 0=top, 1=bottom, 2=side
        val faceTypeArr = intArrayOf(2, 2, 0, 1, 2, 2)

        for (d in 0..5) {
            val mainDim  = axes[d][0]
            val uDim     = axes[d][1]
            val vDim     = axes[d][2]
            val nSign    = axes[d][3]
            val normal   = NORMALS[d]
            val faceType = faceTypeArr[d]

            val mainSize = faceDims[d]
            val uSize: Int; val vSize: Int
            when (d) {
                0,1 -> { uSize = CHUNK_WIDTH; vSize = CHUNK_HEIGHT }
                2,3 -> { uSize = CHUNK_WIDTH; vSize = CHUNK_WIDTH  }
                else -> { uSize = CHUNK_WIDTH; vSize = CHUNK_HEIGHT }
            }

            // Greedy mask: (blockId shl 8) or (ao4bits) — 0 means "empty/hidden"
            // We use a short encoding: blockId(16) | ao0(2) | ao1(2) | ao2(2) | ao3(2) packed in a long
            val mask   = Array(uSize) { LongArray(vSize) { 0L } }
            val aoMask = Array(uSize) { Array(vSize) { IntArray(4) } }

            for (layer in 0 until mainSize) {
                // Build mask for this layer
                for (u in 0 until uSize) for (v in 0 until vSize) {
                    val pos  = intArrayOf(0, 0, 0)
                    pos[mainDim] = layer; pos[uDim] = u; pos[vDim] = v

                    val blk = getLocal(data, pos[0], pos[1], pos[2])
                    if (blk == BlockType.AIR) { mask[u][v] = 0L; continue }

                    val isWater  = BlockType.isLiquid(blk)
                    // Neighbour in +normal direction (what we're checking against)
                    val nPos = intArrayOf(pos[0] + if(mainDim==0) nSign else 0,
                                          pos[1] + if(mainDim==1) nSign else 0,
                                          pos[2] + if(mainDim==2) nSign else 0)
                    val nb = getWorld(world, nPos[0] + origX, nPos[1], nPos[2] + origZ)

                    val visible = when {
                        isWater  -> nb != blk && !BlockType.isSolid(nb)
                        else     -> !BlockType.isOpaque(nb)
                    }
                    if (!visible) { mask[u][v] = 0L; continue }

                    // Compute AO for the 4 vertices of this face
                    val ao = computeFaceAO(world, d, pos[0]+origX, pos[1], pos[2]+origZ)
                    aoMask[u][v] = ao
                    // Encode: blockId in high 32, ao in low 8 (2 bits each × 4 vertices)
                    val aoCode = (ao[0] or (ao[1] shl 2) or (ao[2] shl 4) or (ao[3] shl 6))
                    mask[u][v] = (blk.toLong() shl 8) or aoCode.toLong()
                }

                // Greedy merge
                val done = Array(uSize) { BooleanArray(vSize) }
                for (u in 0 until uSize) for (v in 0 until vSize) {
                    val code = mask[u][v]
                    if (code == 0L || done[u][v]) continue

                    // Expand width (u direction)
                    var w = 1
                    while (u+w < uSize && mask[u+w][v] == code && !done[u+w][v]) w++

                    // Expand height (v direction)
                    var h = 1
                    outer@ while (v+h < vSize) {
                        for (ku in u until u+w)
                            if (mask[ku][v+h] != code || done[ku][v+h]) break@outer
                        h++
                    }

                    // Mark done
                    for (ku in u until u+w) for (kv in v until v+h) done[ku][kv] = true

                    // Emit quad
                    val blk = (code shr 8).toInt()
                    val isWater = BlockType.isLiquid(blk)

                    val (tc, tr) = BlockType.getAtlasTile(blk, faceType)
                    val u0 = tc * TILE_U; val v0 = tr * TILE_V
                    // UV spans the full merged rect
                    val uSpan = w * TILE_U; val vSpan = h * TILE_V

                    // Build 4 world-space positions for the quad corners
                    // The quad lives at layer+offset (depending on sign) in mainDim,
                    // and spans u..(u+w) in uDim, v..(v+h) in vDim
                    val layerOffset = if (nSign > 0) layer + 1 else layer

                    fun mkPos(uu: Int, vv: Int): FloatArray {
                        val p = IntArray(3)
                        p[mainDim] = layerOffset; p[uDim] = uu; p[vDim] = vv
                        return floatArrayOf((p[0] + origX).toFloat(), p[1].toFloat(), (p[2] + origZ).toFloat())
                    }
                    val p0 = mkPos(u,   v)
                    val p1 = mkPos(u+w, v)
                    val p2 = mkPos(u+w, v+h)
                    val p3 = mkPos(u,   v+h)

                    // AO values at the 4 corners — from the top-left cell's ao for simplicity
                    // (For large merged rects this is approximate but visually fine)
                    val ao = aoMask[u][v]
                    val ao0 = aoToFloat(ao[0]); val ao1 = aoToFloat(ao[1])
                    val ao2 = aoToFloat(ao[2]); val ao3 = aoToFloat(ao[3])

                    val base = if (isWater) wtVC else opVC
                    val verts = if (isWater) wtV else opV
                    val idxs  = if (isWater) wtI else opI

                    // Quad verts: p0,p1,p2,p3 with UV corners
                    verts.add(p0[0],p0[1],p0[2], normal[0],normal[1],normal[2], u0,         v0+vSpan, ao0)
                    verts.add(p1[0],p1[1],p1[2], normal[0],normal[1],normal[2], u0+uSpan,   v0+vSpan, ao1)
                    verts.add(p2[0],p2[1],p2[2], normal[0],normal[1],normal[2], u0+uSpan,   v0,       ao2)
                    verts.add(p3[0],p3[1],p3[2], normal[0],normal[1],normal[2], u0,         v0,       ao3)

                    // Flip quad diagonal based on AO for better corner shading
                    val flip = ao0 + ao2 > ao1 + ao3
                    if (flip) {
                        idxs.add(base); idxs.add(base+1); idxs.add(base+3)
                        idxs.add(base+1); idxs.add(base+2); idxs.add(base+3)
                    } else {
                        idxs.add(base); idxs.add(base+1); idxs.add(base+2)
                        idxs.add(base); idxs.add(base+2); idxs.add(base+3)
                    }
                    if (isWater) wtVC += 4 else opVC += 4
                }
            }
        }

        pendingOpaque    = opV.toArray(); pendingOpaqueIdx = opI.toArray()
        pendingWater     = wtV.toArray(); pendingWaterIdx  = wtI.toArray()
        pendingReady     = true
    }

    // ── AO computation for a face at direction d ──────────────────────────
    private fun computeFaceAO(world: World, d: Int, wx: Int, wy: Int, wz: Int): IntArray {
        // For each face direction, compute AO for all 4 vertices
        // Using pre-baked offsets per direction (Mikola Lysenko method)
        return when (d) {
            0 -> { // +X face — normal x+, verts at (wx+1, wy+v, wz+u)
                intArrayOf(
                    aoVal(world.isOpq(wx+1,wy-1,wz), world.isOpq(wx+1,wy,wz-1), world.isOpq(wx+1,wy-1,wz-1)),
                    aoVal(world.isOpq(wx+1,wy-1,wz), world.isOpq(wx+1,wy,wz+1), world.isOpq(wx+1,wy-1,wz+1)),
                    aoVal(world.isOpq(wx+1,wy+1,wz), world.isOpq(wx+1,wy,wz+1), world.isOpq(wx+1,wy+1,wz+1)),
                    aoVal(world.isOpq(wx+1,wy+1,wz), world.isOpq(wx+1,wy,wz-1), world.isOpq(wx+1,wy+1,wz-1))
                )
            }
            1 -> { // -X
                intArrayOf(
                    aoVal(world.isOpq(wx,wy-1,wz), world.isOpq(wx,wy,wz+1), world.isOpq(wx,wy-1,wz+1)),
                    aoVal(world.isOpq(wx,wy-1,wz), world.isOpq(wx,wy,wz-1), world.isOpq(wx,wy-1,wz-1)),
                    aoVal(world.isOpq(wx,wy+1,wz), world.isOpq(wx,wy,wz-1), world.isOpq(wx,wy+1,wz-1)),
                    aoVal(world.isOpq(wx,wy+1,wz), world.isOpq(wx,wy,wz+1), world.isOpq(wx,wy+1,wz+1))
                )
            }
            2 -> { // +Y
                intArrayOf(
                    aoVal(world.isOpq(wx,wy+1,wz-1), world.isOpq(wx-1,wy+1,wz), world.isOpq(wx-1,wy+1,wz-1)),
                    aoVal(world.isOpq(wx,wy+1,wz-1), world.isOpq(wx+1,wy+1,wz), world.isOpq(wx+1,wy+1,wz-1)),
                    aoVal(world.isOpq(wx,wy+1,wz+1), world.isOpq(wx+1,wy+1,wz), world.isOpq(wx+1,wy+1,wz+1)),
                    aoVal(world.isOpq(wx,wy+1,wz+1), world.isOpq(wx-1,wy+1,wz), world.isOpq(wx-1,wy+1,wz+1))
                )
            }
            3 -> { // -Y
                intArrayOf(
                    aoVal(world.isOpq(wx-1,wy,wz), world.isOpq(wx,wy,wz-1), world.isOpq(wx-1,wy,wz-1)),
                    aoVal(world.isOpq(wx+1,wy,wz), world.isOpq(wx,wy,wz-1), world.isOpq(wx+1,wy,wz-1)),
                    aoVal(world.isOpq(wx+1,wy,wz), world.isOpq(wx,wy,wz+1), world.isOpq(wx+1,wy,wz+1)),
                    aoVal(world.isOpq(wx-1,wy,wz), world.isOpq(wx,wy,wz+1), world.isOpq(wx-1,wy,wz+1))
                )
            }
            4 -> { // +Z
                intArrayOf(
                    aoVal(world.isOpq(wx+1,wy,wz+1), world.isOpq(wx,wy-1,wz+1), world.isOpq(wx+1,wy-1,wz+1)),
                    aoVal(world.isOpq(wx-1,wy,wz+1), world.isOpq(wx,wy-1,wz+1), world.isOpq(wx-1,wy-1,wz+1)),
                    aoVal(world.isOpq(wx-1,wy,wz+1), world.isOpq(wx,wy+1,wz+1), world.isOpq(wx-1,wy+1,wz+1)),
                    aoVal(world.isOpq(wx+1,wy,wz+1), world.isOpq(wx,wy+1,wz+1), world.isOpq(wx+1,wy+1,wz+1))
                )
            }
            else -> { // -Z
                intArrayOf(
                    aoVal(world.isOpq(wx-1,wy,wz), world.isOpq(wx,wy-1,wz), world.isOpq(wx-1,wy-1,wz)),
                    aoVal(world.isOpq(wx+1,wy,wz), world.isOpq(wx,wy-1,wz), world.isOpq(wx+1,wy-1,wz)),
                    aoVal(world.isOpq(wx+1,wy,wz), world.isOpq(wx,wy+1,wz), world.isOpq(wx+1,wy+1,wz)),
                    aoVal(world.isOpq(wx-1,wy,wz), world.isOpq(wx,wy+1,wz), world.isOpq(wx-1,wy+1,wz))
                )
            }
        }
    }

    // ── Data helpers ──────────────────────────────────────────────────────
    private fun getLocal(data: ByteArray, x: Int, y: Int, z: Int): Int {
        if (x !in 0 until CHUNK_WIDTH || y !in 0 until CHUNK_HEIGHT || z !in 0 until CHUNK_WIDTH) return BlockType.AIR
        return data[WorldGen.index(x, y, z)].toInt() and 0xFF
    }

    private fun getWorld(world: World, wx: Int, wy: Int, wz: Int) = world.getBlockWorld(wx, wy, wz)

    // ── GPU upload ────────────────────────────────────────────────────────
    fun uploadPending() {
        if (!pendingReady) return
        val ov = pendingOpaque    ?: floatArrayOf()
        val oi = pendingOpaqueIdx ?: intArrayOf()
        val wv = pendingWater     ?: floatArrayOf()
        val wi = pendingWaterIdx  ?: intArrayOf()
        pendingOpaque=null; pendingOpaqueIdx=null; pendingWater=null; pendingWaterIdx=null; pendingReady=false

        freeBuffers()
        if (oi.isNotEmpty()) {
            val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); vboOpaque=ids[0]; iboOpaque=ids[1]
            uploadF(vboOpaque, ov); uploadI(iboOpaque, oi); opaqueCount = oi.size
        }
        if (wi.isNotEmpty()) {
            val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); vboWater=ids[0]; iboWater=ids[1]
            uploadF(vboWater, wv); uploadI(iboWater, wi); waterCount = wi.size
        }
        built = true
    }

    private fun uploadF(vbo: Int, d: FloatArray) {
        val buf = ByteBuffer.allocateDirect(d.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(d); buf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, d.size*4, buf, GLES20.GL_STATIC_DRAW)
    }
    private fun uploadI(ibo: Int, d: IntArray) {
        val buf = ByteBuffer.allocateDirect(d.size*4).order(ByteOrder.nativeOrder()).asIntBuffer()
        buf.put(d); buf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, d.size*4, buf, GLES20.GL_STATIC_DRAW)
    }

    fun drawOpaque(sp: ShaderProgram) { if(!built||opaqueCount==0) return; bind(sp,vboOpaque,iboOpaque,opaqueCount) }
    fun drawWater(sp: ShaderProgram)  { if(!built||waterCount==0)  return; bind(sp,vboWater, iboWater, waterCount)  }

    private fun bind(sp: ShaderProgram, vbo: Int, ibo: Int, count: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        sp.setVertexAttribs(STRIDE*4)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_INT, 0)
    }

    fun freeBuffers() {
        if(vboOpaque!=0){GLES20.glDeleteBuffers(2,intArrayOf(vboOpaque,iboOpaque),0);vboOpaque=0;iboOpaque=0;opaqueCount=0}
        if(vboWater !=0){GLES20.glDeleteBuffers(2,intArrayOf(vboWater, iboWater), 0);vboWater=0; iboWater=0; waterCount=0 }
        built=false
    }

    // ── Dynamic primitive builders (avoid boxing overhead) ────────────────
    private class FloatArrayBuilder(cap: Int) {
        private var buf = FloatArray(cap); private var size = 0
        fun add(vararg v: Float) { ensureCap(v.size); v.forEach { buf[size++] = it } }
        private fun ensureCap(n: Int) { if(size+n > buf.size) buf = buf.copyOf(buf.size*2) }
        fun toArray() = buf.copyOf(size)
    }
    private class IntArrayBuilder(cap: Int) {
        private var buf = IntArray(cap); private var size = 0
        fun add(vararg v: Int) { ensureCap(v.size); v.forEach { buf[size++] = it } }
        private fun ensureCap(n: Int) { if(size+n > buf.size) buf = buf.copyOf(buf.size*2) }
        fun toArray() = buf.copyOf(size)
    }
}

// Extension: fast opaque check used by AO
private fun World.isOpq(wx: Int, wy: Int, wz: Int) = BlockType.isOpaque(getBlockWorld(wx, wy, wz))
