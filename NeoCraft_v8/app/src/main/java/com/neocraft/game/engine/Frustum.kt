package com.neocraft.game.engine

/**
 * 6-plane view frustum culling.
 * Extracts planes from the MVP matrix and tests chunk AABBs against them.
 * Any chunk whose bounding box is fully outside ANY plane is skipped — no draw call.
 *
 * On a typical scene with render distance 6, this culls ~40-60% of chunks.
 */
class Frustum {

    // 6 planes, each stored as (a,b,c,d) where ax+by+cz+d=0
    private val planes = Array(6) { FloatArray(4) }

    /** Extract frustum planes from a combined MVP matrix (column-major). */
    fun extract(mvp: FloatArray) {
        // Row-major interpretation:
        val m = mvp   // column-major from Android Matrix
        // Columns: m[0..3]=col0, m[4..7]=col1, m[8..11]=col2, m[12..15]=col3
        // Row r, col c → m[c*4+r]

        fun row(r: Int) = floatArrayOf(m[r], m[4+r], m[8+r], m[12+r])
        val r0 = row(0); val r1 = row(1); val r2 = row(2); val r3 = row(3)

        // Left:   r3 + r0
        planes[0][0]=r3[0]+r0[0]; planes[0][1]=r3[1]+r0[1]; planes[0][2]=r3[2]+r0[2]; planes[0][3]=r3[3]+r0[3]
        // Right:  r3 - r0
        planes[1][0]=r3[0]-r0[0]; planes[1][1]=r3[1]-r0[1]; planes[1][2]=r3[2]-r0[2]; planes[1][3]=r3[3]-r0[3]
        // Bottom: r3 + r1
        planes[2][0]=r3[0]+r1[0]; planes[2][1]=r3[1]+r1[1]; planes[2][2]=r3[2]+r1[2]; planes[2][3]=r3[3]+r1[3]
        // Top:    r3 - r1
        planes[3][0]=r3[0]-r1[0]; planes[3][1]=r3[1]-r1[1]; planes[3][2]=r3[2]-r1[2]; planes[3][3]=r3[3]-r1[3]
        // Near:   r3 + r2
        planes[4][0]=r3[0]+r2[0]; planes[4][1]=r3[1]+r2[1]; planes[4][2]=r3[2]+r2[2]; planes[4][3]=r3[3]+r2[3]
        // Far:    r3 - r2
        planes[5][0]=r3[0]-r2[0]; planes[5][1]=r3[1]-r2[1]; planes[5][2]=r3[2]-r2[2]; planes[5][3]=r3[3]-r2[3]

        // Normalize
        for (p in planes) {
            val len = Math.sqrt((p[0]*p[0] + p[1]*p[1] + p[2]*p[2]).toDouble()).toFloat()
            if (len > 0f) { p[0]/=len; p[1]/=len; p[2]/=len; p[3]/=len }
        }
    }

    /**
     * Test an AABB (min/max) against all 6 planes.
     * Returns false if the box is fully outside the frustum (cull it).
     */
    fun testAABB(minX: Float, minY: Float, minZ: Float,
                 maxX: Float, maxY: Float, maxZ: Float): Boolean {
        for (p in planes) {
            // Find the positive vertex (most in the direction of the plane normal)
            val px = if (p[0] >= 0f) maxX else minX
            val py = if (p[1] >= 0f) maxY else minY
            val pz = if (p[2] >= 0f) maxZ else minZ
            if (p[0]*px + p[1]*py + p[2]*pz + p[3] < 0f) return false  // outside
        }
        return true
    }

    /** Convenience: test a chunk by its column position. */
    fun testChunk(cx: Int, cz: Int, chunkWidth: Int = 16, chunkHeight: Int = 128): Boolean {
        val minX = (cx * chunkWidth).toFloat()
        val minZ = (cz * chunkWidth).toFloat()
        return testAABB(minX, 0f, minZ, minX + chunkWidth, chunkHeight.toFloat(), minZ + chunkWidth)
    }
}
