package com.neocraft.game.engine

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

/**
 * GPU-friendly particle system.
 * Supports: block-break debris, rain splashes, dust, smoke, sparkles, blood/hit.
 *
 * Each particle is a tiny billboard quad rendered with additive or alpha blending.
 * All particles stored in a single dynamic VBO rebuilt each frame (fast for ~2000 particles).
 */
class ParticleSystem {

    // ── Particle data ─────────────────────────────────────────────────────
    private data class Particle(
        var x: Float, var y: Float, var z: Float,
        var vx: Float, var vy: Float, var vz: Float,
        var life: Float, var maxLife: Float,
        var size: Float,
        var r: Float, var g: Float, var b: Float, var a: Float,
        val additive: Boolean = false
    )

    private val particles = ArrayList<Particle>(2048)
    private var vbo = 0
    private var initialized = false

    // Stride: x y z size r g b a = 8 floats
    private val STRIDE = 8 * 4

    fun init() {
        if (initialized) return
        val ids = IntArray(1); GLES20.glGenBuffers(1, ids, 0); vbo = ids[0]
        initialized = true
    }

    // ── Emitters ──────────────────────────────────────────────────────────

    /** Explode block debris — coloured chips flying outward. */
    fun emitBlockBreak(x: Float, y: Float, z: Float, blockR: Float, blockG: Float, blockB: Float) {
        repeat(12) {
            val speed = 2f + Random.nextFloat() * 4f
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val elev  = (Random.nextFloat() - 0.3f) * PI.toFloat() * 0.5f
            spawn(x + Random.nextFloat() - 0.5f,
                  y + Random.nextFloat() * 0.8f,
                  z + Random.nextFloat() - 0.5f,
                  cos(angle) * cos(elev) * speed,
                  sin(elev) * speed + 1.5f,
                  sin(angle) * cos(elev) * speed,
                  life = 0.4f + Random.nextFloat() * 0.5f,
                  size = 0.06f + Random.nextFloat() * 0.08f,
                  r = blockR * (0.7f + Random.nextFloat() * 0.3f),
                  g = blockG * (0.7f + Random.nextFloat() * 0.3f),
                  b = blockB * (0.7f + Random.nextFloat() * 0.3f)
            )
        }
    }

    /** Rain splash on a surface. */
    fun emitRainSplash(x: Float, y: Float, z: Float) {
        repeat(3) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 0.8f + Random.nextFloat() * 1.2f
            spawn(x, y + 0.02f, z,
                  cos(angle) * speed, 0.5f + Random.nextFloat(),
                  sin(angle) * speed,
                  life = 0.3f, size = 0.04f,
                  r = 0.7f, g = 0.82f, b = 0.95f, a = 0.7f
            )
        }
    }

    /** Torch / fire smoke upward. */
    fun emitSmoke(x: Float, y: Float, z: Float) {
        spawn(x + (Random.nextFloat() - 0.5f) * 0.2f,
              y,
              z + (Random.nextFloat() - 0.5f) * 0.2f,
              (Random.nextFloat() - 0.5f) * 0.2f, 0.6f + Random.nextFloat() * 0.4f,
              (Random.nextFloat() - 0.5f) * 0.2f,
              life = 0.8f + Random.nextFloat(),
              size = 0.08f + Random.nextFloat() * 0.06f,
              r = 0.3f, g = 0.3f, b = 0.3f, a = 0.5f
        )
    }

    /** Torch flame sparkle. */
    fun emitFlame(x: Float, y: Float, z: Float) {
        spawn(x + (Random.nextFloat() - 0.5f) * 0.15f,
              y,
              z + (Random.nextFloat() - 0.5f) * 0.15f,
              (Random.nextFloat() - 0.5f) * 0.3f, 0.8f + Random.nextFloat() * 0.5f, 0f,
              life = 0.3f,
              size = 0.05f + Random.nextFloat() * 0.04f,
              r = 1.0f, g = 0.5f + Random.nextFloat() * 0.3f, b = 0.05f, a = 0.9f,
              additive = true
        )
    }

    /** Hit / damage effect on mob. */
    fun emitHit(x: Float, y: Float, z: Float) {
        repeat(5) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            spawn(x, y + 1f, z,
                  cos(angle) * 3f, 2f + Random.nextFloat() * 2f, sin(angle) * 3f,
                  life = 0.4f, size = 0.06f,
                  r = 0.9f, g = 0.1f, b = 0.1f
            )
        }
    }

    /** Water splash when jumping into water. */
    fun emitWaterSplash(x: Float, y: Float, z: Float) {
        repeat(8) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 2f + Random.nextFloat() * 3f
            spawn(x, y, z,
                  cos(angle) * speed, 2f + Random.nextFloat() * 3f, sin(angle) * speed,
                  life = 0.5f + Random.nextFloat() * 0.4f,
                  size = 0.07f + Random.nextFloat() * 0.06f,
                  r = 0.5f, g = 0.7f, b = 1.0f, a = 0.85f
            )
        }
    }

    /** Creeper explosion flash. */
    fun emitExplosion(x: Float, y: Float, z: Float, radius: Float) {
        repeat(40) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val elev  = (Random.nextFloat() - 0.5f) * PI.toFloat()
            val speed = radius * (0.5f + Random.nextFloat())
            spawn(x, y + radius * 0.5f, z,
                  cos(angle) * cos(elev) * speed,
                  sin(elev) * speed + 1f,
                  sin(angle) * cos(elev) * speed,
                  life = 0.6f + Random.nextFloat() * 0.8f,
                  size = 0.12f + Random.nextFloat() * 0.18f,
                  r = 1.0f, g = 0.4f + Random.nextFloat() * 0.3f, b = 0.05f, a = 0.9f,
                  additive = true
            )
        }
        // Smoke ring
        repeat(20) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val r2 = radius * 0.7f
            spawn(x + cos(angle) * r2, y + 0.5f, z + sin(angle) * r2,
                  0f, 1.5f + Random.nextFloat(), 0f,
                  life = 1.2f + Random.nextFloat(),
                  size = 0.14f, r = 0.25f, g = 0.25f, b = 0.25f, a = 0.6f
            )
        }
    }

    private fun spawn(x: Float, y: Float, z: Float,
                      vx: Float, vy: Float, vz: Float,
                      life: Float, size: Float,
                      r: Float, g: Float, b: Float, a: Float = 1f,
                      additive: Boolean = false) {
        if (particles.size >= 2000) return
        particles.add(Particle(x, y, z, vx, vy, vz, life, life, size, r, g, b, a, additive))
    }

    // ── Update ────────────────────────────────────────────────────────────
    fun update(dt: Float) {
        val gravity = 9.8f
        particles.removeAll { p ->
            p.life -= dt
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            p.vy -= gravity * dt
            p.vx *= 0.97f; p.vz *= 0.97f
            // Fade out
            p.a = (p.life / p.maxLife).coerceIn(0f, 1f) * 0.95f
            p.life <= 0f
        }
    }

    // ── Render ────────────────────────────────────────────────────────────
    /**
     * Render all particles as camera-facing point sprites.
     * Uses the block shader with additive blending for fire/explosion.
     * Normal particles use standard alpha blending.
     */
    fun render(shader: ShaderProgram, vpMatrix: FloatArray) {
        if (!initialized || particles.isEmpty()) return

        val mvp    = FloatArray(16)
        val model  = FloatArray(16)
        val tmp    = FloatArray(16)
        val scaleM = FloatArray(16)
        val transM = FloatArray(16)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)

        for (p in particles) {
            Matrix.setIdentityM(transM, 0)
            Matrix.translateM(transM, 0, p.x, p.y, p.z)
            Matrix.setIdentityM(scaleM, 0)
            Matrix.scaleM(scaleM, 0, p.size, p.size, p.size)
            Matrix.multiplyMM(model, 0, transM, 0, scaleM, 0)
            Matrix.multiplyMM(mvp, 0, vpMatrix, 0, model, 0)
            shader.setMVP(mvp)
            shader.setAlpha(p.a)
            shader.setSkyColor(p.r, p.g, p.b)

            if (p.additive) GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            else             GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            // Draw as a GL_POINTS sprite (single point, size via shader)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        }

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(true)
    }

    fun count() = particles.size
}
