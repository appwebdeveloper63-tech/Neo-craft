package com.neocraft.game.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.neocraft.game.world.DAY_LENGTH
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * NeoCraft v4 SkyRenderer
 * Physically-based sky dome + realistic weather system
 *
 * Weather states: CLEAR → OVERCAST → RAIN → THUNDER → clearing
 * Rain includes visual darkening, fog thickening, and wind drift.
 */
class SkyRenderer(context: Context) {

    private val shader = ShaderProgram(context, "shaders/sky.vert", "shaders/sky.frag")
    private var vbo = 0; private var ibo = 0; private var triCount = 0

    // ── Public values read by GameRenderer ───────────────────────────────
    var skyR = 0.49f; var skyG = 0.78f; var skyB = 0.89f
    var sunBrightness = 1f
    var ambientLight  = 0.35f
    var sunDirX = 0.6f; var sunDirY = 1f; var sunDirZ = 0.5f

    // ── Weather state ─────────────────────────────────────────────────────
    enum class Weather { CLEAR, OVERCAST, RAIN, THUNDER }
    var weather      = Weather.CLEAR
        private set
    var rainIntensity = 0f   // 0–1 smooth interpolation
        private set
    var thunderFlash  = 0f   // 0–1 flash brightness
        private set
    var windX = 0f; var windZ = 0f   // world-space wind direction

    private var weatherTimer  = 0f
    private var weatherTarget = Weather.CLEAR
    private var nextWeatherIn = 120f   // seconds until next weather change
    private var thunderTimer  = 0f
    private var lightningActive = false

    // Cached for draw
    private var _zenith   = floatArrayOf(0.3f, 0.58f, 1f)
    private var _horizon  = floatArrayOf(0.65f, 0.84f, 1f)
    private var _dayFactor = 1f

    // Uniform locations
    private val uMVP       by lazy { GLES20.glGetUniformLocation(shader.programId, "u_MVP") }
    private val uSunDir    by lazy { GLES20.glGetUniformLocation(shader.programId, "u_SunDir") }
    private val uZenith    by lazy { GLES20.glGetUniformLocation(shader.programId, "u_ZenithColor") }
    private val uHorizon   by lazy { GLES20.glGetUniformLocation(shader.programId, "u_HorizonColor") }
    private val uDayFactor by lazy { GLES20.glGetUniformLocation(shader.programId, "u_DayFactor") }
    private val uTime      by lazy { GLES20.glGetUniformLocation(shader.programId, "u_Time") }
    private val uRain      by lazy { GLES20.glGetUniformLocation(shader.programId, "u_Rain") }

    init { buildSphere() }

    // ── Main update ───────────────────────────────────────────────────────
    fun update(totalTime: Float, dt: Float = 0.016f) {
        updateSun(totalTime)
        updateWeather(dt)
    }

    private fun updateSun(totalTime: Float) {
        val angle = (totalTime / DAY_LENGTH) * 2f * PI.toFloat()
        // Sun arc: rises in east, sets in west with slight north/south variation
        sunDirX = sin(angle * 0.5f + 0.3f) * 0.55f
        sunDirY = cos(angle)
        sunDirZ = sin(angle * 0.25f + 0.8f) * 0.3f

        val dayFactor = clamp01((sunDirY + 0.15f) / 0.5f)
        // Rain darkens the sun significantly
        val rainDim = 1f - rainIntensity * 0.65f
        sunBrightness = (dayFactor * 0.85f + 0.15f) * rainDim

        // Night ambient is very low; overcast night slightly brighter
        val overcastBump = rainIntensity * 0.08f
        ambientLight = (0.07f + dayFactor * 0.32f + overcastBump) * rainDim + 0.04f

        // Thunder flash adds brief bright ambient
        ambientLight += thunderFlash * 0.4f

        // Sky colours: day → sunset → night, modulated by rain
        val sunset = clamp01(1f - abs(sunDirY - 0.08f) / 0.38f) * dayFactor

        fun lerp3(a: FloatArray, b: FloatArray, t: Float) =
            floatArrayOf(a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t)

        val dayZenith    = floatArrayOf(0.12f, 0.38f, 0.82f)
        val dayHorizon   = floatArrayOf(0.55f, 0.76f, 0.98f)
        val sunsetZenith = floatArrayOf(0.40f, 0.18f, 0.08f)
        val sunsetHorizon= floatArrayOf(0.98f, 0.42f, 0.08f)
        val nightZenith  = floatArrayOf(0.008f, 0.010f, 0.045f)
        val nightHorizon = floatArrayOf(0.025f, 0.028f, 0.075f)
        val overcastDay  = floatArrayOf(0.55f, 0.57f, 0.62f)
        val overcastH    = floatArrayOf(0.48f, 0.50f, 0.54f)

        var zenith  = lerp3(lerp3(nightZenith, dayZenith, dayFactor), sunsetZenith, sunset)
        var horizon = lerp3(lerp3(nightHorizon, dayHorizon, dayFactor), sunsetHorizon, sunset)

        // Rain/overcast: blend to grey
        zenith  = lerp3(zenith,  lerp3(nightZenith, overcastDay, dayFactor), rainIntensity * 0.85f)
        horizon = lerp3(horizon, lerp3(nightHorizon, overcastH,  dayFactor), rainIntensity * 0.85f)

        // Fog colour = horizon tint
        skyR = horizon[0] * 0.88f; skyG = horizon[1] * 0.88f; skyB = horizon[2] * 0.88f
        _zenith = zenith; _horizon = horizon; _dayFactor = dayFactor
    }

    private fun updateWeather(dt: Float) {
        weatherTimer += dt
        nextWeatherIn -= dt

        // Advance rain intensity toward target
        val targetRain = when (weather) {
            Weather.CLEAR    -> 0f
            Weather.OVERCAST -> 0.35f
            Weather.RAIN     -> 0.85f
            Weather.THUNDER  -> 1.0f
        }
        rainIntensity += (targetRain - rainIntensity) * (dt * 0.3f)

        // Wind picks up with rain
        val targetWind = rainIntensity * 0.6f
        windX += (targetWind - windX) * dt * 0.2f
        windZ += (targetWind * 0.5f - windZ) * dt * 0.2f

        // Thunder logic
        if (weather == Weather.THUNDER) {
            thunderTimer -= dt
            if (thunderTimer <= 0f) {
                thunderTimer = 8f + (sin(weatherTimer * 37.3f) * 0.5f + 0.5f) * 20f
                lightningActive = true
            }
            if (lightningActive) {
                thunderFlash = (thunderFlash + dt * 12f).coerceAtMost(1f)
                if (thunderFlash >= 1f) { thunderFlash = 0f; lightningActive = false }
            }
        } else {
            thunderFlash = (thunderFlash - dt * 4f).coerceAtLeast(0f)
        }

        // Weather transitions
        if (nextWeatherIn <= 0f) {
            val rand = sin(weatherTimer * 13.7f) * 0.5f + 0.5f   // deterministic pseudo-random
            weather = when (weather) {
                Weather.CLEAR    -> if (rand > 0.6f) Weather.OVERCAST else Weather.CLEAR
                Weather.OVERCAST -> if (rand > 0.5f) Weather.RAIN else if (rand > 0.15f) Weather.CLEAR else Weather.OVERCAST
                Weather.RAIN     -> if (rand > 0.7f) Weather.THUNDER else if (rand > 0.3f) Weather.OVERCAST else Weather.RAIN
                Weather.THUNDER  -> if (rand > 0.4f) Weather.RAIN else Weather.THUNDER
            }
            nextWeatherIn = 60f + rand * 180f   // 1–4 minutes per weather state
        }
    }

    // ── GL Draw ───────────────────────────────────────────────────────────
    fun draw(projMat: FloatArray, eyeX: Float, eyeY: Float, eyeZ: Float, totalTime: Float) {
        val view = FloatArray(16); Matrix.setIdentityM(view, 0)
        val mvp  = FloatArray(16); Matrix.multiplyMM(mvp, 0, projMat, 0, view, 0)

        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        shader.use()
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform3f(uSunDir, sunDirX, sunDirY, sunDirZ)
        GLES20.glUniform3f(uZenith, _zenith[0], _zenith[1], _zenith[2])
        GLES20.glUniform3f(uHorizon, _horizon[0], _horizon[1], _horizon[2])
        GLES20.glUniform1f(uDayFactor, _dayFactor)
        GLES20.glUniform1f(uTime, totalTime)
        GLES20.glUniform1f(uRain, rainIntensity)

        val aPos = GLES20.glGetAttribLocation(shader.programId, "a_Position")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, triCount, GLES20.GL_UNSIGNED_INT, 0)
        GLES20.glDisableVertexAttribArray(aPos)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }

    private fun buildSphere() {
        val stacks = 24; val slices = 32
        val verts = ArrayList<Float>(); val idxs = ArrayList<Int>()
        for (i in 0..stacks) {
            val phi = PI * i / stacks - PI / 2
            for (j in 0..slices) {
                val theta = 2 * PI * j / slices
                verts.add((cos(phi) * cos(theta)).toFloat())
                verts.add(sin(phi).toFloat())
                verts.add((cos(phi) * sin(theta)).toFloat())
            }
        }
        for (i in 0 until stacks) for (j in 0 until slices) {
            val a = i*(slices+1)+j; val b = a+slices+1
            idxs.add(a); idxs.add(b); idxs.add(a+1)
            idxs.add(b); idxs.add(b+1); idxs.add(a+1)
        }
        triCount = idxs.size
        val vf = verts.toFloatArray(); val ii = idxs.toIntArray()
        val vbuf = ByteBuffer.allocateDirect(vf.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().also{it.put(vf);it.position(0)}
        val ibuf = ByteBuffer.allocateDirect(ii.size*4).order(ByteOrder.nativeOrder()).asIntBuffer().also{it.put(ii);it.position(0)}
        val ids = IntArray(2); GLES20.glGenBuffers(2, ids, 0); vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo); GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vf.size*4, vbuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo); GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ii.size*4, ibuf, GLES20.GL_STATIC_DRAW)
    }

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
}
