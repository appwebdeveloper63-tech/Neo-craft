package com.neocraft.game.engine

import com.neocraft.game.world.BlockType
import com.neocraft.game.world.World
import kotlin.math.*

/**
 * Dynamic block-light system.
 *
 * Scans nearby blocks for light-emitting blocks (torches, glowstone, lava, etc.)
 * and computes a per-vertex ambient boost that's added in the block shader.
 *
 * Performance: only re-scans when player moves a full block.
 * Returns a list of light sources close to the player that the shader
 * can use to tint and brighten nearby geometry.
 */
data class LightSource(
    val x: Float, val y: Float, val z: Float,
    val intensity: Float,   // 0–1
    val r: Float, val g: Float, val b: Float
)

class DynamicLighting(private val world: World) {

    private val sources   = ArrayList<LightSource>(32)
    private var lastPX    = Int.MIN_VALUE
    private var lastPY    = Int.MIN_VALUE
    private var lastPZ    = Int.MIN_VALUE

    // Returns current light sources (cached until player moves 1 block)
    fun update(px: Float, py: Float, pz: Float): List<LightSource> {
        val bx = floor(px).toInt(); val by = floor(py).toInt(); val bz = floor(pz).toInt()
        if (bx == lastPX && by == lastPY && bz == lastPZ) return sources
        lastPX = bx; lastPY = by; lastPZ = bz
        sources.clear()

        // Scan a 10-block radius around the player
        val RADIUS = 10
        for (dx in -RADIUS..RADIUS) for (dy in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            val wx = bx + dx; val wy = by + dy; val wz = bz + dz
            val blk = world.getBlock(wx, wy, wz)
            if (!BlockType.emitsLight(blk)) continue
            val dist = sqrt((dx*dx + dy*dy + dz*dz).toFloat())
            if (dist > RADIUS) continue
            val maxDist = BlockType.lightLevel(blk).toFloat()
            val intens  = (1f - dist / maxDist).coerceIn(0f, 1f)
            val (r, g, b) = lightColor(blk)
            sources.add(LightSource(wx + 0.5f, wy + 0.5f, wz + 0.5f, intens, r, g, b))
        }

        // Sort by intensity descending, keep top 8 (shader limit)
        sources.sortByDescending { it.intensity }
        while (sources.size > 8) sources.removeLast()
        return sources
    }

    // Compute the total ambient light contribution at player position
    fun ambientBoost(px: Float, py: Float, pz: Float): Float {
        var boost = 0f
        for (src in sources) {
            val d = sqrt((src.x-px).pow(2) + (src.y-py).pow(2) + (src.z-pz).pow(2))
            boost += src.intensity * (1f - d / 10f).coerceAtLeast(0f) * 0.15f
        }
        return boost.coerceAtMost(0.5f)
    }

    private fun lightColor(blk: Int): Triple<Float, Float, Float> = when(blk) {
        BlockType.TORCH         -> Triple(1.0f, 0.85f, 0.5f)
        BlockType.LANTERN       -> Triple(1.0f, 0.80f, 0.4f)
        BlockType.GLOWSTONE     -> Triple(1.0f, 0.95f, 0.7f)
        BlockType.LAVA          -> Triple(1.0f, 0.4f,  0.05f)
        BlockType.MAGMA_BLOCK   -> Triple(1.0f, 0.3f,  0.0f)
        BlockType.SEA_LANTERN   -> Triple(0.7f, 1.0f,  0.9f)
        BlockType.SHROOMLIGHT   -> Triple(1.0f, 0.7f,  0.3f)
        else                    -> Triple(1.0f, 1.0f,  0.9f)
    }
}
