package com.neocraft.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.neocraft.game.world.BlockType
import kotlin.math.*
import kotlin.random.Random

/**
 * NeoCraft Sound Engine v8
 *
 * Generates ALL sounds procedurally using Android's AudioTrack + SoundPool.
 * No external audio files needed — every sound is synthesised from scratch:
 *
 *  • Block break/place sounds (pitch varies by hardness & material)
 *  • Footstep sounds (different texture per block type)
 *  • Ambient cave sounds (wind, drips, echoes)
 *  • Weather sounds (rain, thunder)
 *  • Mob sounds (zombie groan, skeleton rattle, creeper hiss)
 *  • UI sounds (menu click, inventory, craft success)
 *  • Water / lava ambience
 *  • Day/night transition chime
 *
 * Uses SoundPool for short one-shot sounds and a background thread
 * for looping ambient audio via AudioTrack synthesis.
 */
class SoundEngine(private val context: Context) {

    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
    }

    private val sampleRate = 22050
    private val handler     = Handler(Looper.getMainLooper())

    // Sound IDs loaded into SoundPool
    private val sounds = HashMap<SoundType, Int>()

    // Volume settings
    var masterVolume  = 0.8f
    var sfxVolume     = 0.9f
    var ambientVolume = 0.5f
    var enabled       = true

    // Ambient state
    private var lastFootstepTime = 0L
    private var lastAmbientTime  = 0L
    private var isUnderwater     = false
    private var isInCave         = false
    private var rainIntensity    = 0f

    enum class SoundType {
        // Block
        BREAK_STONE, BREAK_DIRT, BREAK_WOOD, BREAK_GLASS, BREAK_SAND,
        BREAK_METAL, BREAK_GRAVEL, BREAK_ICE, BREAK_GENERIC,
        PLACE_STONE, PLACE_DIRT, PLACE_WOOD, PLACE_GENERIC,
        // Footstep
        STEP_GRASS, STEP_STONE, STEP_WOOD, STEP_SAND, STEP_GRAVEL, STEP_SNOW,
        // Ambient
        WATER_DRIP, CAVE_WIND, CAVE_ECHO, LAVA_BUBBLE,
        // Weather
        RAIN_HIT, THUNDER,
        // Mobs
        ZOMBIE_GROAN, ZOMBIE_HURT, SKELETON_RATTLE, CREEPER_HISS, CREEPER_EXPLODE,
        SPIDER_SKITTER,
        // Player
        PLAYER_HURT, PLAYER_SPLASH, PLAYER_EAT,
        // UI
        CLICK, CRAFT_SUCCESS, LEVEL_UP, OPEN_CHEST,
        // Transitions
        SUNRISE_CHIME, NIGHT_CHIME
    }

    // ── Initialise all sounds ──────────────────────────────────────────────
    fun init() {
        Thread {
            try {
                // Generate all sounds and load into pool
                loadSound(SoundType.BREAK_STONE,    synthesiseBreakStone())
                loadSound(SoundType.BREAK_DIRT,     synthesiseBreakDirt())
                loadSound(SoundType.BREAK_WOOD,     synthesiseBreakWood())
                loadSound(SoundType.BREAK_GLASS,    synthesiseBreakGlass())
                loadSound(SoundType.BREAK_SAND,     synthesiseBreakSand())
                loadSound(SoundType.BREAK_METAL,    synthesiseBreakMetal())
                loadSound(SoundType.BREAK_GRAVEL,   synthesiseBreakGravel())
                loadSound(SoundType.BREAK_ICE,      synthesiseBreakIce())
                loadSound(SoundType.BREAK_GENERIC,  synthesiseBreakGeneric())

                loadSound(SoundType.PLACE_STONE,    synthesisePlaceStone())
                loadSound(SoundType.PLACE_DIRT,     synthesisePlaceDirt())
                loadSound(SoundType.PLACE_WOOD,     synthesisePlaceWood())
                loadSound(SoundType.PLACE_GENERIC,  synthesisePlaceGeneric())

                loadSound(SoundType.STEP_GRASS,     synthesiseStepGrass())
                loadSound(SoundType.STEP_STONE,     synthesiseStepStone())
                loadSound(SoundType.STEP_WOOD,      synthesiseStepWood())
                loadSound(SoundType.STEP_SAND,      synthesiseStepSand())
                loadSound(SoundType.STEP_GRAVEL,    synthesiseStepGravel())
                loadSound(SoundType.STEP_SNOW,      synthesiseStepSnow())

                loadSound(SoundType.WATER_DRIP,     synthesiseWaterDrip())
                loadSound(SoundType.CAVE_WIND,      synthesiseCaveWind())
                loadSound(SoundType.LAVA_BUBBLE,    synthesiseLavaBubble())

                loadSound(SoundType.RAIN_HIT,       synthesiseRainHit())
                loadSound(SoundType.THUNDER,        synthesiseThunder())

                loadSound(SoundType.ZOMBIE_GROAN,   synthesiseZombieGroan())
                loadSound(SoundType.ZOMBIE_HURT,    synthesiseZombieHurt())
                loadSound(SoundType.SKELETON_RATTLE,synthesiseSkeletonRattle())
                loadSound(SoundType.CREEPER_HISS,   synthesiseCreeperHiss())
                loadSound(SoundType.CREEPER_EXPLODE,synthesiseCreeperExplode())
                loadSound(SoundType.SPIDER_SKITTER, synthesiseSpiderSkitter())

                loadSound(SoundType.PLAYER_HURT,    synthesisePlayerHurt())
                loadSound(SoundType.PLAYER_SPLASH,  synthesisePlayerSplash())
                loadSound(SoundType.PLAYER_EAT,     synthesisePlayerEat())

                loadSound(SoundType.CLICK,          synthesiseClick())
                loadSound(SoundType.CRAFT_SUCCESS,  synthesiseCraftSuccess())
                loadSound(SoundType.LEVEL_UP,       synthesiseLevelUp())
                loadSound(SoundType.OPEN_CHEST,     synthesiseOpenChest())

                loadSound(SoundType.SUNRISE_CHIME,  synthesiseSunriseChime())
                loadSound(SoundType.NIGHT_CHIME,    synthesiseNightChime())

                Log.d("SoundEngine", "All sounds loaded (${sounds.size})")
            } catch (e: Exception) {
                Log.e("SoundEngine", "Sound init error: ${e.message}")
            }
        }.start()
    }

    // ── Playback API ──────────────────────────────────────────────────────
    fun play(type: SoundType, volume: Float = 1f, pitch: Float = 1f) {
        if (!enabled) return
        val id = sounds[type] ?: return
        val v = (volume * masterVolume * sfxVolume).coerceIn(0f, 1f)
        val p = pitch * (0.9f + Random.nextFloat() * 0.2f)  // slight random pitch variation
        pool.play(id, v, v, 1, 0, p)
    }

    fun playBlockBreak(blockId: Int) {
        val t = when (blockId) {
            BlockType.STONE, BlockType.COBBLESTONE, BlockType.STONE_BRICKS,
            BlockType.GRANITE, BlockType.DIORITE, BlockType.ANDESITE,
            BlockType.DEEPSLATE, BlockType.BLACKSTONE, BlockType.OBSIDIAN -> SoundType.BREAK_STONE
            BlockType.DIRT, BlockType.GRASS, BlockType.GRAVEL,
            BlockType.CLAY, BlockType.PODZOL, BlockType.COARSE_DIRT       -> SoundType.BREAK_DIRT
            BlockType.LOG_OAK, BlockType.LOG_BIRCH, BlockType.LOG_SPRUCE,
            BlockType.LOG_JUNGLE, BlockType.LOG_ACACIA, BlockType.LOG_DARK_OAK,
            BlockType.PLANKS_OAK, BlockType.PLANKS_JUNGLE                 -> SoundType.BREAK_WOOD
            BlockType.GLASS                                                -> SoundType.BREAK_GLASS
            BlockType.SAND, BlockType.RED_SAND                            -> SoundType.BREAK_SAND
            BlockType.IRON_BLOCK, BlockType.GOLD_BLOCK,
            BlockType.DIAMOND_BLOCK, BlockType.NETHERITE_BLOCK            -> SoundType.BREAK_METAL
            BlockType.GRAVEL                                               -> SoundType.BREAK_GRAVEL
            BlockType.ICE, BlockType.PACKED_ICE                           -> SoundType.BREAK_ICE
            else                                                           -> SoundType.BREAK_GENERIC
        }
        val pitchMod = when {
            BlockType.hardness(blockId) > 10f -> 0.7f
            BlockType.hardness(blockId) < 0.5f -> 1.3f
            else -> 1.0f
        }
        play(t, pitch = pitchMod)
    }

    fun playBlockPlace(blockId: Int) {
        val t = when (blockId) {
            BlockType.STONE, BlockType.COBBLESTONE, BlockType.STONE_BRICKS,
            BlockType.GRANITE, BlockType.DIORITE, BlockType.ANDESITE      -> SoundType.PLACE_STONE
            BlockType.DIRT, BlockType.GRASS, BlockType.GRAVEL,
            BlockType.CLAY                                                 -> SoundType.PLACE_DIRT
            BlockType.LOG_OAK, BlockType.LOG_BIRCH, BlockType.LOG_SPRUCE,
            BlockType.PLANKS_OAK                                           -> SoundType.PLACE_WOOD
            else                                                           -> SoundType.PLACE_GENERIC
        }
        play(t, volume = 0.7f)
    }

    fun playFootstep(blockId: Int, sprinting: Boolean) {
        val now = System.currentTimeMillis()
        val interval = if (sprinting) 350L else 480L
        if (now - lastFootstepTime < interval) return
        lastFootstepTime = now

        val t = when (blockId) {
            BlockType.GRASS, BlockType.PODZOL, BlockType.MYCELIUM,
            BlockType.MOSS_BLOCK                                           -> SoundType.STEP_GRASS
            BlockType.STONE, BlockType.COBBLESTONE, BlockType.STONE_BRICKS,
            BlockType.GRANITE, BlockType.ANDESITE, BlockType.DEEPSLATE    -> SoundType.STEP_STONE
            BlockType.PLANKS_OAK, BlockType.PLANKS_JUNGLE,
            BlockType.LOG_OAK                                              -> SoundType.STEP_WOOD
            BlockType.SAND, BlockType.RED_SAND, BlockType.SANDSTONE        -> SoundType.STEP_SAND
            BlockType.GRAVEL                                               -> SoundType.STEP_GRAVEL
            BlockType.SNOW_BLOCK, BlockType.SNOW_GRASS                    -> SoundType.STEP_SNOW
            else                                                           -> SoundType.STEP_STONE
        }
        play(t, volume = 0.45f, pitch = if(sprinting) 1.1f else 1.0f)
    }

    fun tickAmbient(dt: Float, playerY: Float, rain: Float, thunder: Boolean) {
        rainIntensity = rain
        isInCave = playerY < 50f

        val now = System.currentTimeMillis()
        if (now - lastAmbientTime > 8000L) {
            lastAmbientTime = now
            when {
                isInCave && Random.nextFloat() > 0.4f -> {
                    val t = if(Random.nextBoolean()) SoundType.WATER_DRIP else SoundType.CAVE_WIND
                    play(t, ambientVolume * 0.6f)
                }
                rain > 0.3f -> play(SoundType.RAIN_HIT, rain * ambientVolume * 0.4f)
                playerY < 30f && Random.nextFloat() > 0.6f ->
                    play(SoundType.LAVA_BUBBLE, ambientVolume * 0.5f)
            }
        }
        if (thunder) play(SoundType.THUNDER, ambientVolume)
    }

    // ── Synthesis functions ───────────────────────────────────────────────
    // All synthesise* functions return a ShortArray (PCM mono 22050 Hz)

    private fun sine(freq: Double, dur: Double, amp: Double = 0.8) =
        ShortArray((sampleRate * dur).toInt()) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * amp * Short.MAX_VALUE).toInt().toShort()
        }

    private fun noise(dur: Double, amp: Double = 0.5) =
        ShortArray((sampleRate * dur).toInt()) { (Random.nextFloat() * 2 - 1).let { (it * amp * Short.MAX_VALUE).toInt().toShort() } }

    private fun envelope(data: ShortArray, attackMs: Int = 10, releaseMs: Int = 80): ShortArray {
        val att = (sampleRate * attackMs / 1000).coerceAtMost(data.size)
        val rel = (sampleRate * releaseMs / 1000).coerceAtMost(data.size)
        return ShortArray(data.size) { i ->
            val env = when {
                i < att  -> i.toDouble() / att
                i > data.size - rel -> (data.size - i).toDouble() / rel
                else -> 1.0
            }
            (data[i] * env).toInt().toShort()
        }
    }

    private fun mix(a: ShortArray, b: ShortArray): ShortArray {
        val len = maxOf(a.size, b.size)
        return ShortArray(len) { i ->
            val av = if(i < a.size) a[i].toInt() else 0
            val bv = if(i < b.size) b[i].toInt() else 0
            ((av + bv) / 2).toShort()
        }
    }

    private fun pitchShift(data: ShortArray, factor: Double): ShortArray {
        val newSize = (data.size / factor).toInt()
        return ShortArray(newSize) { i ->
            val src = (i * factor).toInt().coerceIn(0, data.size - 1)
            data[src]
        }
    }

    // ── Block sounds ─────────────────────────────────────────────────────
    private fun synthesiseBreakStone(): ShortArray {
        val crack = noise(0.12, 0.6)
        val thud  = sine(80.0, 0.08, 0.4)
        return envelope(mix(crack, thud), 5, 120)
    }
    private fun synthesiseBreakDirt(): ShortArray = envelope(noise(0.15, 0.5), 5, 100)
    private fun synthesiseBreakWood(): ShortArray {
        val crack = noise(0.08, 0.4)
        val snap  = sine(200.0, 0.05, 0.3)
        return envelope(mix(crack, snap), 3, 80)
    }
    private fun synthesiseBreakGlass(): ShortArray {
        val shatter = ShortArray((sampleRate * 0.3).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val freq = 2000.0 + Random.nextDouble() * 3000.0
            (sin(2.0 * PI * freq * t) * exp(-t * 15) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        return mix(shatter, noise(0.1, 0.2))
    }
    private fun synthesiseBreakSand(): ShortArray = envelope(noise(0.18, 0.35), 5, 150)
    private fun synthesiseBreakMetal(): ShortArray {
        val clang = sine(440.0, 0.2, 0.6)
        return envelope(mix(clang, noise(0.05, 0.2)), 2, 180)
    }
    private fun synthesiseBreakGravel(): ShortArray = envelope(noise(0.20, 0.45), 3, 130)
    private fun synthesiseBreakIce(): ShortArray {
        val crack = noise(0.06, 0.5)
        val high  = sine(800.0, 0.04, 0.3)
        return envelope(mix(crack, high), 2, 60)
    }
    private fun synthesiseBreakGeneric(): ShortArray = envelope(noise(0.12, 0.45), 5, 100)

    private fun synthesisePlaceStone() = envelope(ShortArray((sampleRate * 0.08).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * 120.0 * t) * exp(-t * 30) * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }, 2, 50)
    private fun synthesisePlaceDirt()    = envelope(noise(0.08, 0.35), 2, 60)
    private fun synthesisePlaceWood()    = envelope(mix(noise(0.06, 0.25), sine(180.0, 0.06, 0.2)), 2, 70)
    private fun synthesisePlaceGeneric() = envelope(noise(0.07, 0.3), 2, 55)

    // ── Footsteps ─────────────────────────────────────────────────────────
    private fun synthesiseStepGrass()   = envelope(noise(0.07, 0.25), 3, 70)
    private fun synthesiseStepStone()   = envelope(mix(noise(0.05, 0.3), sine(90.0, 0.05, 0.2)), 2, 50)
    private fun synthesiseStepWood()    = envelope(mix(noise(0.04, 0.2), sine(160.0, 0.04, 0.15)), 2, 60)
    private fun synthesiseStepSand()    = envelope(noise(0.09, 0.22), 4, 80)
    private fun synthesiseStepGravel()  = envelope(noise(0.08, 0.28), 3, 70)
    private fun synthesiseStepSnow()    = envelope(noise(0.10, 0.20), 5, 90)

    // ── Ambient ───────────────────────────────────────────────────────────
    private fun synthesiseWaterDrip() = ShortArray((sampleRate * 0.4).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val drop = sin(2 * PI * 900.0 * t) * exp(-t * 12) * 0.4
        val ripple = sin(2 * PI * 600.0 * t) * exp(-t * 20) * 0.2
        ((drop + ripple) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseCaveWind() = envelope(ShortArray((sampleRate * 1.5).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val lfo = sin(2 * PI * 0.3 * t) * 0.5 + 0.5
        (Random.nextFloat() * 2 - 1).let { (it * 0.3 * lfo * Short.MAX_VALUE).toInt().toShort() }
    }, 500, 500)
    private fun synthesiseLavaBubble() = ShortArray((sampleRate * 0.5).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * 60.0 * t) * exp(-t * 6) * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }

    // ── Weather ───────────────────────────────────────────────────────────
    private fun synthesiseRainHit() = envelope(noise(0.06, 0.2), 1, 50)
    private fun synthesiseThunder() = ShortArray((sampleRate * 1.8).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val body = sin(2 * PI * 40.0 * t + sin(2 * PI * 1.0 * t) * 5) * exp(-t * 1.5) * 0.8
        val noise = (Random.nextFloat() * 2 - 1) * 0.3 * exp(-t * 2)
        ((body + noise) * Short.MAX_VALUE).toInt().toShort()
    }

    // ── Mobs ──────────────────────────────────────────────────────────────
    private fun synthesiseZombieGroan() = ShortArray((sampleRate * 0.8).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val base = sin(2 * PI * 90.0 * t + sin(2 * PI * 2.0 * t) * 4)
        val noise = (Random.nextFloat() * 2 - 1) * 0.2
        ((base * 0.5 + noise) * exp(-t * 0.5) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseZombieHurt() = ShortArray((sampleRate * 0.3).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((sin(2 * PI * 150.0 * t) * 0.6 + (Random.nextFloat()*2-1) * 0.3) * exp(-t*5) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseSkeletonRattle() = ShortArray((sampleRate * 0.4).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((Random.nextFloat() * 2 - 1) * 0.4 * (sin(2 * PI * 30.0 * t) * 0.5 + 0.5) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseCreeperHiss() = ShortArray((sampleRate * 1.2).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((Random.nextFloat() * 2 - 1) * 0.5 * (1 - exp(-t * 2)) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseCreeperExplode() = ShortArray((sampleRate * 0.8).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val boom = sin(2 * PI * 50.0 * t) * 0.7 * exp(-t * 8)
        val noise = (Random.nextFloat() * 2 - 1) * 0.5 * exp(-t * 4)
        ((boom + noise) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseSpiderSkitter() = ShortArray((sampleRate * 0.3).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((sin(2 * PI * 800.0 * t) * 0.2 + (Random.nextFloat()*2-1) * 0.3) * exp(-t * 10) * Short.MAX_VALUE).toInt().toShort()
    }

    // ── Player ────────────────────────────────────────────────────────────
    private fun synthesisePlayerHurt() = ShortArray((sampleRate * 0.3).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((sin(2 * PI * 300.0 * t) * 0.5 + (Random.nextFloat()*2-1)*0.3) * exp(-t*8) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesisePlayerSplash() = ShortArray((sampleRate * 0.5).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((Random.nextFloat()*2-1) * 0.5 * exp(-t * 5) * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesisePlayerEat() = ShortArray((sampleRate * 0.25).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        ((Random.nextFloat()*2-1) * 0.3 * sin(PI * i / (sampleRate * 0.25)) * Short.MAX_VALUE).toInt().toShort()
    }

    // ── UI ────────────────────────────────────────────────────────────────
    private fun synthesiseClick() = ShortArray((sampleRate * 0.05).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * 800.0 * t) * exp(-t * 40) * 0.4 * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseCraftSuccess() = ShortArray((sampleRate * 0.4).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val note = when {
            t < 0.1  -> 523.25  // C5
            t < 0.2  -> 659.25  // E5
            else     -> 783.99  // G5
        }
        (sin(2 * PI * note * t) * exp(-t * 3) * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseLevelUp() = ShortArray((sampleRate * 0.8).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val freq = 261.63 * 2.0.pow(t * 2)   // Rising arpeggio
        (sin(2 * PI * freq * t) * exp(-t * 1) * 0.5 * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseOpenChest() = ShortArray((sampleRate * 0.3).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val creak = sin(2 * PI * (200.0 + t * 400) * t)
        (creak * exp(-t * 4) * 0.3 * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseSunriseChime() = ShortArray((sampleRate * 1.5).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        val notes = doubleArrayOf(261.63, 329.63, 392.0, 523.25)
        val note = notes[(t * 2.5).toInt().coerceAtMost(3)]
        (sin(2 * PI * note * t) * exp(-t * 1.2) * 0.4 * Short.MAX_VALUE).toInt().toShort()
    }
    private fun synthesiseNightChime() = ShortArray((sampleRate * 1.5).toInt()) { i ->
        val t = i.toDouble() / sampleRate
        (sin(2 * PI * 220.0 * t) * exp(-t * 0.8) * 0.3 * Short.MAX_VALUE).toInt().toShort()
    }

    // ── SoundPool loader ──────────────────────────────────────────────────
    private fun loadSound(type: SoundType, pcm: ShortArray) {
        try {
            // Write PCM as WAV into a temp file and load into SoundPool
            val file = java.io.File(context.cacheDir, "snd_${type.name}.wav")
            writeWav(file, pcm)
            val id = pool.load(file.absolutePath, 1)
            if (id > 0) sounds[type] = id
        } catch (e: Exception) {
            Log.w("SoundEngine", "Failed to load $type: ${e.message}")
        }
    }

    private fun writeWav(file: java.io.File, pcm: ShortArray) {
        val dataSize = pcm.size * 2
        java.io.FileOutputStream(file).use { fos ->
            val header = ByteArray(44)
            fun putInt(pos: Int, v: Int) { header[pos]=(v and 0xFF).toByte(); header[pos+1]=(v shr 8 and 0xFF).toByte(); header[pos+2]=(v shr 16 and 0xFF).toByte(); header[pos+3]=(v shr 24 and 0xFF).toByte() }
            fun putShort(pos: Int, v: Int) { header[pos]=(v and 0xFF).toByte(); header[pos+1]=(v shr 8 and 0xFF).toByte() }
            header[0]='R'.code.toByte(); header[1]='I'.code.toByte(); header[2]='F'.code.toByte(); header[3]='F'.code.toByte()
            putInt(4, 36 + dataSize)
            header[8]='W'.code.toByte(); header[9]='A'.code.toByte(); header[10]='V'.code.toByte(); header[11]='E'.code.toByte()
            header[12]='f'.code.toByte(); header[13]='m'.code.toByte(); header[14]='t'.code.toByte(); header[15]=' '.code.toByte()
            putInt(16, 16); putShort(20, 1); putShort(22, 1)
            putInt(24, sampleRate); putInt(28, sampleRate * 2); putShort(32, 2); putShort(34, 16)
            header[36]='d'.code.toByte(); header[37]='a'.code.toByte(); header[38]='t'.code.toByte(); header[39]='a'.code.toByte()
            putInt(40, dataSize)
            fos.write(header)
            val buf = ByteArray(dataSize)
            for (i in pcm.indices) { buf[i*2]=(pcm[i].toInt() and 0xFF).toByte(); buf[i*2+1]=(pcm[i].toInt() shr 8 and 0xFF).toByte() }
            fos.write(buf)
        }
    }

    fun destroy() {
        pool.release()
        sounds.clear()
        // Clean up temp WAV files
        context.cacheDir.listFiles { f -> f.name.startsWith("snd_") && f.name.endsWith(".wav") }
            ?.forEach { it.delete() }
    }
}
