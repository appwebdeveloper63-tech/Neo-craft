package com.neocraft.game

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.neocraft.game.ui.TouchController

class GameSurfaceView(
    context: Context,
    worldName: String = "default",
    seed: Long = System.currentTimeMillis()
) : GLSurfaceView(context) {

    val touch    = TouchController()
    val renderer: GameRenderer

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        renderer = GameRenderer(context, touch, worldName, seed)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouchEvent(event)

    fun destroy() { renderer.destroy() }
}
