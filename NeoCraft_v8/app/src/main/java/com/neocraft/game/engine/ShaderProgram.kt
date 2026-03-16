package com.neocraft.game.engine

import android.content.Context
import android.opengl.GLES20
import android.util.Log

class ShaderProgram(context: Context, vertAsset: String, fragAsset: String) {

    val programId: Int

    // Uniform locations (lazy — only paid on first use)
    private val uMVP        by lazy { GLES20.glGetUniformLocation(programId, "u_MVP") }
    private val uTexture    by lazy { GLES20.glGetUniformLocation(programId, "u_Texture") }
    private val uSunDir     by lazy { GLES20.glGetUniformLocation(programId, "u_SunDir") }
    private val uSkyColor   by lazy { GLES20.glGetUniformLocation(programId, "u_SkyColor") }
    private val uFogRange   by lazy { GLES20.glGetUniformLocation(programId, "u_FogRange") }
    private val uAlpha      by lazy { GLES20.glGetUniformLocation(programId, "u_Alpha") }
    private val uTime       by lazy { GLES20.glGetUniformLocation(programId, "u_Time") }
    private val uSunBright  by lazy { GLES20.glGetUniformLocation(programId, "u_SunBrightness") }
    private val uAmbient    by lazy { GLES20.glGetUniformLocation(programId, "u_Ambient") }
    private val uUnderwater by lazy { GLES20.glGetUniformLocation(programId, "u_Underwater") }
    private val uRain       by lazy { GLES20.glGetUniformLocation(programId, "u_Rain") }
    private val uWindDir    by lazy { GLES20.glGetUniformLocation(programId, "u_WindDir") }

    // Attrib locations
    private val aPosition   by lazy { GLES20.glGetAttribLocation(programId, "a_Position") }
    private val aNormal     by lazy { GLES20.glGetAttribLocation(programId, "a_Normal") }
    private val aTexCoord   by lazy { GLES20.glGetAttribLocation(programId, "a_TexCoord") }
    private val aAO         by lazy { GLES20.glGetAttribLocation(programId, "a_AO") }

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER,   context.assets.open(vertAsset).bufferedReader().readText())
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, context.assets.open(fragAsset).bufferedReader().readText())
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vs); GLES20.glAttachShader(programId, fs)
        GLES20.glLinkProgram(programId)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        // Check link
        val status = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) Log.e("NeoCraft", "Program link error: ${GLES20.glGetProgramInfoLog(programId)}")
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val st = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
        if (st[0] == 0) Log.e("NeoCraft", "Shader error (${if(type==GLES20.GL_VERTEX_SHADER)"vert" else "frag"}): ${GLES20.glGetShaderInfoLog(s)}")
        return s
    }

    fun use() = GLES20.glUseProgram(programId)

    fun setMVP(m: FloatArray)               = GLES20.glUniformMatrix4fv(uMVP, 1, false, m, 0)
    fun setTexture(u: Int)                  = GLES20.glUniform1i(uTexture, u)
    fun setSunDir(x: Float, y: Float, z: Float) = GLES20.glUniform3f(uSunDir, x, y, z)
    fun setSkyColor(r: Float, g: Float, b: Float) = GLES20.glUniform3f(uSkyColor, r, g, b)
    fun setFogRange(s: Float, e: Float)     = GLES20.glUniform2f(uFogRange, s, e)
    fun setAlpha(a: Float)                  = GLES20.glUniform1f(uAlpha, a)
    fun setTime(t: Float)                   = GLES20.glUniform1f(uTime, t)
    fun setSunBrightness(b: Float)          = GLES20.glUniform1f(uSunBright, b)
    fun setAmbient(a: Float)                = GLES20.glUniform1f(uAmbient, a)
    fun setUnderwater(u: Float)             = GLES20.glUniform1f(uUnderwater, u)
    fun setRain(r: Float)                   = GLES20.glUniform1f(uRain, r)
    fun setWindDir(x: Float, z: Float)      = GLES20.glUniform2f(uWindDir, x, z)

    // Dynamic point lights
    fun setNumLights(n: Int) {
        val loc = GLES20.glGetUniformLocation(programId, "u_NumLights")
        if (loc >= 0) GLES20.glUniform1i(loc, n)
    }
    fun setLightPos(i: Int, x: Float, y: Float, z: Float) {
        val loc = GLES20.glGetUniformLocation(programId, "u_LightPos[$i]")
        if (loc >= 0) GLES20.glUniform3f(loc, x, y, z)
    }
    fun setLightCol(i: Int, r: Float, g: Float, b: Float) {
        val loc = GLES20.glGetUniformLocation(programId, "u_LightCol[$i]")
        if (loc >= 0) GLES20.glUniform3f(loc, r, g, b)
    }
    fun setLightInt(i: Int, intensity: Float) {
        val loc = GLES20.glGetUniformLocation(programId, "u_LightInt[$i]")
        if (loc >= 0) GLES20.glUniform1f(loc, intensity)
    }

    fun setVertexAttribs(strideBytes: Int) {
        val f = 4
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, strideBytes, 3*f)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, strideBytes, 6*f)
        GLES20.glEnableVertexAttribArray(aAO)
        GLES20.glVertexAttribPointer(aAO, 1, GLES20.GL_FLOAT, false, strideBytes, 8*f)
    }

    fun disableAttribs() {
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        GLES20.glDisableVertexAttribArray(aAO)
    }
}
