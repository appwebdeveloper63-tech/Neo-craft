package com.neocraft.game.entity

import android.opengl.GLES20
import android.opengl.Matrix
import com.neocraft.game.engine.ShaderProgram
import com.neocraft.game.engine.TextureAtlas
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Billboard-style mob renderer.
 * Each mob is drawn as a coloured quad facing the camera (billboard),
 * tinted with the mob's body colour. Simple but readable and performant.
 *
 * For a production game you'd use skeletal meshes; this gives correct
 * depth sorting, shadows, and lighting with minimal vertex count.
 */
object MobRenderer {

    private var vbo = 0
    private var ibo = 0
    private var initialized = false

    // One shared quad (unit square, centred at origin)
    private val quadVerts = floatArrayOf(
        // x,    y,    z,   nx,  ny,  nz,   u,   v,   ao
        -0.5f, 0.0f, 0.0f,  0f,  0f,  1f,  0f,  1f,  1f,
         0.5f, 0.0f, 0.0f,  0f,  0f,  1f,  1f,  1f,  1f,
         0.5f, 1.0f, 0.0f,  0f,  0f,  1f,  1f,  0f,  1f,
        -0.5f, 1.0f, 0.0f,  0f,  0f,  1f,  0f,  0f,  1f,
    )
    private val quadIdx = intArrayOf(0, 1, 2, 0, 2, 3)

    fun init() {
        if (initialized) return
        val vbuf = ByteBuffer.allocateDirect(quadVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vbuf.put(quadVerts); vbuf.position(0)
        val ibuf = ByteBuffer.allocateDirect(quadIdx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ibuf.put(quadIdx); ibuf.position(0)
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quadVerts.size * 4, vbuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIdx.size * 4, ibuf, GLES20.GL_STATIC_DRAW)
        initialized = true
    }

    /** Draw all entities in the list using billboard quads. */
    fun drawAll(
        entities: List<Entity>,
        shader: ShaderProgram,
        vpMatrix: FloatArray,   // view-projection (no model)
        cameraYaw: Float
    ) {
        if (!initialized) init()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)

        val model   = FloatArray(16)
        val mvp     = FloatArray(16)
        val rotY    = FloatArray(16)
        val trans   = FloatArray(16)
        val scale   = FloatArray(16)
        val tmp     = FloatArray(16)

        for (e in entities) {
            if (e.dead) continue
            val (w, h, col, hurtF) = mobVisuals(e)

            // Billboard: face camera by rotating around Y by cameraYaw
            Matrix.setIdentityM(model, 0)
            Matrix.setIdentityM(rotY, 0)
            Matrix.setRotateM(rotY, 0, cameraYaw, 0f, 1f, 0f)
            Matrix.setIdentityM(scale, 0)
            Matrix.scaleM(scale, 0, w, h, 1f)
            Matrix.setIdentityM(trans, 0)
            Matrix.translateM(trans, 0, e.x, e.y, e.z)

            // model = trans * rotY * scale
            Matrix.multiplyMM(tmp, 0, rotY, 0, scale, 0)
            Matrix.multiplyMM(model, 0, trans, 0, tmp, 0)
            Matrix.multiplyMM(mvp, 0, vpMatrix, 0, model, 0)

            shader.setMVP(mvp)

            // Tint via alpha; hurtFlash adds red
            val hF = hurtF.coerceIn(0f, 1f)
            shader.setAlpha(1f)
            // Encode mob colour into sky color uniform for tinting
            val r = ((col shr 16 and 0xFF) / 255f) * (1f - hF) + hF
            val g = ((col shr  8 and 0xFF) / 255f) * (1f - hF)
            val b = ((col        and 0xFF) / 255f) * (1f - hF)
            shader.setSkyColor(r, g, b)

            shader.setVertexAttribs(9 * 4)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_INT, 0)
        }
        shader.disableAttribs()
    }

    // Returns (width, height, colour, hurtFlash)
    private fun mobVisuals(e: Entity): Quad = when (e) {
        is Cow      -> Quad(1.0f, 1.4f, 0xFF5C4033.toInt(), 0f)
        is Pig      -> Quad(0.9f, 0.9f, 0xFFE88888.toInt(), 0f)
        is Sheep    -> Quad(0.9f, 1.3f, e.woolColor,        0f)
        is Chicken  -> Quad(0.5f, 0.7f, 0xFFFFFFCC.toInt(), 0f)
        is Zombie   -> Quad(0.7f, 1.8f, 0xFF559955.toInt(), e.hurtFlash)
        is Skeleton -> Quad(0.6f, 1.8f, 0xFFDDDDDD.toInt(), e.hurtFlash)
        is Creeper  -> {
            // Flash white when priming
            val priFlash = if (e.state == Creeper.State.PRIMING)
                (sin(e.fuseTimer * 20f) * 0.5f + 0.5f) else 0f
            Quad(0.6f, 1.7f, 0xFF559933.toInt(), priFlash.coerceIn(0f, 1f))
        }
        is Spider   -> Quad(1.4f, 0.9f, 0xFF333333.toInt(), e.hurtFlash)
        is Arrow    -> Quad(0.1f, 0.6f, 0xFFCC9933.toInt(), 0f)
        else        -> Quad(0.8f, 1.8f, 0xFFFF00FF.toInt(), 0f)
    }

    private data class Quad(val w: Float, val h: Float, val col: Int, val hurtFlash: Float)
}
