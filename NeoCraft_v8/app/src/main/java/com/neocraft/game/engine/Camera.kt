package com.neocraft.game.engine

import android.opengl.Matrix
import kotlin.math.*

class Camera {
    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    val mvpMatrix  = FloatArray(16)

    // Head bob parameters
    private var bobPhase  = 0f
    private var bobAmt    = 0f   // current bob amplitude (smoothed)

    fun setProjection(fovDeg: Float, aspect: Float, near: Float = 0.05f, far: Float = 600f) {
        val f  = 1f / tan(Math.toRadians(fovDeg.toDouble() / 2)).toFloat()
        val nf = 1f / (near - far)
        projMatrix.apply {
            set(0, f/aspect); set(1, 0f); set(2, 0f); set(3, 0f)
            set(4, 0f);       set(5, f);  set(6, 0f); set(7, 0f)
            set(8, 0f);       set(9, 0f); set(10, (far+near)*nf); set(11, -1f)
            set(12, 0f);      set(13, 0f);set(14, 2f*far*near*nf); set(15, 0f)
        }
    }

    /**
     * Update head bob animation.
     * @param speed movement speed 0–1 (0=still, 1=sprinting)
     * @param onGround true when player is on ground
     * @param dt delta time in seconds
     */
    fun updateBob(speed: Float, onGround: Boolean, dt: Float) {
        val targetAmt = if (onGround && speed > 0.1f) speed else 0f
        bobAmt += (targetAmt - bobAmt) * (dt * 8f)
        bobPhase += dt * (2.8f + speed * 3.5f) // walking = 2.8 Hz, sprinting faster
    }

    fun setView(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        yawDeg: Float, pitchDeg: Float,
        applyBob: Boolean = true
    ) {
        val yr = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pr = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val dx = -sin(yr)*cos(pr); val dy = sin(pr); val dz = -cos(yr)*cos(pr)

        // Head bob: vertical + lateral sway
        val bobV = if (applyBob) sin(bobPhase * 2f) * bobAmt * 0.04f else 0f
        val bobH = if (applyBob) sin(bobPhase)      * bobAmt * 0.02f else 0f
        // Lateral is perpendicular to look direction
        val rightX = cos(yr); val rightZ = -sin(yr)

        val ex = eyeX + rightX * bobH
        val ey = eyeY + bobV
        val ez = eyeZ + rightZ * bobH

        Matrix.setLookAtM(viewMatrix, 0, ex, ey, ez, ex+dx, ey+dy, ez+dz, 0f, 1f, 0f)
    }

    fun computeMVP() = Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)
}
