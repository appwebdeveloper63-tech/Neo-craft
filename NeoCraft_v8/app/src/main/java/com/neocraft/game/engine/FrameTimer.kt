package com.neocraft.game.engine

/**
 * Smooth adaptive frame-time budget manager.
 *
 * Tracks frame time with exponential moving average, detects spikes,
 * and recommends how many heavy operations (chunk rebuilds, mesh uploads)
 * are safe to perform per frame without causing jank.
 *
 * Target: 60 fps = 16.67ms per frame.
 * Budget for rebuilds: ~3ms per frame (leaving 13ms for rendering).
 */
class FrameTimer {

    private var emaMs    = 16.67f  // exponential moving average frame time
    private var spikeMs  = 0f      // current spike smoothed value
    var fps              = 60
        private set

    private var fpsCounter = 0
    private var fpsAccum   = 0f

    /** Call once per frame with the raw dt (seconds). Returns smoothed dt. */
    fun tick(rawDt: Float): Float {
        val ms = rawDt * 1000f
        emaMs  = emaMs * 0.92f + ms * 0.08f
        spikeMs = (ms - emaMs).coerceAtLeast(0f) * 0.7f + spikeMs * 0.3f

        fpsCounter++; fpsAccum += rawDt
        if (fpsAccum >= 1f) { fps = fpsCounter; fpsCounter = 0; fpsAccum = 0f }

        return (emaMs / 1000f).coerceIn(0.001f, 0.05f)
    }

    /** How many chunk mesh uploads to allow this frame. */
    fun uploadBudget(): Int = when {
        emaMs < 12f -> 6   // lots of headroom — upload fast
        emaMs < 16f -> 3   // normal
        emaMs < 22f -> 1   // getting tight — reduce uploads
        else        -> 0   // frame overrun — skip uploads entirely
    }

    /** How many chunk rebuilds to queue this frame. */
    fun rebuildBudget(): Int = when {
        emaMs < 12f -> 5
        emaMs < 16f -> 3
        emaMs < 22f -> 2
        else        -> 1
    }

    val isStressed get() = emaMs > 20f
    val isSmoothRunning get() = emaMs < 14f
}
