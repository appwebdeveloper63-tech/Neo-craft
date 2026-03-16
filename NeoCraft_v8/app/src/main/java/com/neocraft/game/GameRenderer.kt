package com.neocraft.game

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.neocraft.game.engine.*
import com.neocraft.game.audio.SoundEngine
import com.neocraft.game.ui.SettingsView
import com.neocraft.game.entity.*
import com.neocraft.game.player.Player
import com.neocraft.game.ui.TouchController
import com.neocraft.game.world.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GameRenderer(
    private val context: Context,
    private val touch: TouchController,
    private val worldName: String = "default",
    private val seed: Long = System.currentTimeMillis()
) : GLSurfaceView.Renderer {

    private lateinit var blockShader: ShaderProgram
    private lateinit var waterShader: ShaderProgram
    lateinit var sky: SkyRenderer
    private val camera   = Camera()
    private val frustum  = Frustum()
    private val timer    = FrameTimer()

    val world     = World()
    val player    = Player(world)
    val mobs      = MobManager(world)
    val particles = ParticleSystem()
    val dynLight  = DynamicLighting(world)

    private val chunks = HashMap<Long, Chunk>(256)

    // Dedicated thread pool for chunk generation (not coroutine default pool)
    // Uses N-1 CPU cores so the GL thread always has a core free
    private val cpuCores     = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    private val meshPool     = Executors.newFixedThreadPool((cpuCores - 1).coerceAtLeast(1))
    private val meshScope    = CoroutineScope(meshPool.asCoroutineDispatcher() + SupervisorJob())

    // Tracks which chunks are currently being rebuilt (prevent double-dispatch)
    private val rebuilding   = HashSet<Long>()

    // Sorted rebuild queue — nearest chunks first
    private val rebuildQueue = ArrayDeque<Pair<Int, Int>>(256)
    private var lastPCX = Int.MIN_VALUE; private var lastPCZ = Int.MIN_VALUE

    // ── Timing & perf ─────────────────────────────────────────────────────
    private var lastNanos   = 0L
    var totalTime           = 0f
    private var saveTimer   = 0f
    private var gravTimer   = 0f
    private var torchTimer  = 0f
    private var rainTimer   = 0f
    private var frameCount  = 0L
    private var smoothDt    = 0.016f

    // ── Rendering ─────────────────────────────────────────────────────────
    var renderDistance = RENDER_DISTANCE
        set(v) { field = v.coerceIn(MIN_RENDER_DISTANCE, MAX_RENDER_DISTANCE) }
    private var currentFov = 70f
    private var wasInWater = false

    // HUD callback — includes frustum-culled chunk count for debug
    var hudUpdater: ((Int,Int,String,String,Float,Int,Int,Int,Boolean,Float,Float,String,
        String,Boolean,Boolean,IntArray,Int,Int,Long,Array<ItemStack?>,Boolean,
        List<CraftingSystem.Recipe>,Int,String,Float,Float,Int,Int,Int)->Unit)? = null

    // ── GL lifecycle ──────────────────────────────────────────────────────
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)  // slightly more lenient for z-fighting
        GLES20.glEnable(GLES20.GL_CULL_FACE); GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // Enable polygon offset to reduce z-fighting on coplanar surfaces
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(1f, 1f)

        blockShader = ShaderProgram(context, "shaders/block.vert", "shaders/block.frag")
        waterShader = ShaderProgram(context, "shaders/water.vert", "shaders/water.frag")
        sky         = SkyRenderer(context)
        TextureAtlas.init()
        particles.init()
        MobRenderer.init()
        sound.init()

        WorldGen.worldSeed = seed
        world.context = context; world.worldName = worldName

        val meta = SaveSystem.loadMeta(context, worldName)
        val ps   = SaveSystem.loadPlayer(context, worldName)
        if (meta != null) { WorldGen.worldSeed = meta.seed; totalTime = meta.totalTime }
        if (ps != null) {
            player.x=ps.x; player.y=ps.y; player.z=ps.z
            player.yaw=ps.yaw; player.pitch=ps.pitch
            player.health=ps.health; player.hunger=ps.hunger
            player.xpLevel=ps.xpLevel; player.xpPoints=ps.xpPoints
            player.inventory.selectedSlot=ps.selectedSlot
            if (ps.inventoryData.isNotEmpty()) player.inventory.deserialize(ps.inventoryData)
        } else {
            val sh = WorldGen.getHeight(8, 8)
            player.x=8.5f; player.y=(sh+4).toFloat(); player.z=8.5f
        }
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        camera.setProjection(currentFov, width.toFloat() / height.toFloat())
        touch.screenW = width.toFloat(); touch.screenH = height.toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val rawDt = ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now
        smoothDt  = timer.tick(rawDt)
        totalTime += smoothDt
        frameCount++

        // ── Dynamic render distance (based on smooth FPS) ─────────────────
        if (frameCount % 60 == 0L) {
            when {
                timer.fps < 20 && renderDistance > MIN_RENDER_DISTANCE -> renderDistance--
                timer.fps < 28 && renderDistance > MIN_RENDER_DISTANCE + 1 -> { /* hold */ }
                timer.fps > 55 && renderDistance < RENDER_DISTANCE -> renderDistance++
            }
        }

        // ── Input ─────────────────────────────────────────────────────────
        val (ldx, ldy) = touch.consumeLookDelta()
        val sensitivityMul = settings?.sensitivity?.div(0.22f) ?: 1f
        val invertMul = if (settings?.invertY == true) -1f else 1f
        player.yaw   = (player.yaw + ldx * sensitivityMul) % 360f
        player.pitch = (player.pitch - ldy * sensitivityMul * invertMul).coerceIn(-89f, 89f)
        player.sneaking = touch.sneakHeld
        if (touch.consumeSprintToggle()) player.sprinting = !player.sprinting
        if (touch.consumeDoubleTap()) player.tryToggleFly()
        if (touch.consumeInventoryToggle()) player.inventoryOpen = !player.inventoryOpen

        val didBreak = player.tickBreak(smoothDt, touch.breakHeld)
        if (touch.consumePlace()) player.placeBlock()
        player.update(smoothDt, touch.moveX, touch.moveZ, touch.jumpHeld)
        // Footstep sounds
        if (player.onGround && (touch.moveX != 0f || touch.moveZ != 0f)) {
            val underFeet = world.getBlock(player.x.toInt(), (player.y - 0.1f).toInt(), player.z.toInt())
            sound.playFootstep(underFeet, player.sprinting)
        }

        // ── Particles ─────────────────────────────────────────────────────
        if (didBreak) {
            val bt = player.breakTarget
            if (bt != null) sound.playBlockBreak(world.getBlock(bt.first, bt.second, bt.third))
            if (bt != null) {
                val blk = world.getBlock(bt.first, bt.second, bt.third)
                val (r,g,b) = blockColorRGB(blk)
                particles.emitBlockBreak(bt.first+0.5f, bt.second+0.5f, bt.third+0.5f, r, g, b)
            }
        }
        if (player.inWater && player.vy < -2f && !wasInWater)
            particles.emitWaterSplash(player.x, player.y+0.3f, player.z)
        wasInWater = player.inWater

        // Torch smoke — throttled
        torchTimer += smoothDt
        if (torchTimer >= 0.3f) {
            torchTimer = 0f
            val bx=floor(player.x).toInt(); val by=floor(player.y).toInt(); val bz=floor(player.z).toInt()
            for (dx in -4..4) for (dy in -2..4) for (dz in -4..4) {
                val blk = world.getBlock(bx+dx, by+dy, bz+dz)
                if (blk == BlockType.TORCH || blk == BlockType.LANTERN) {
                    particles.emitSmoke(bx+dx+0.5f, by+dy+1.1f, bz+dz+0.5f)
                    if (frameCount % 2 == 0L)
                        particles.emitFlame(bx+dx+0.5f, by+dy+0.9f, bz+dz+0.5f)
                }
            }
        }

        // Rain splashes — reduced particle count to keep smooth
        if (sky.rainIntensity > 0.3f) {
            rainTimer += smoothDt
            if (rainTimer >= 0.1f) {
                rainTimer = 0f
                repeat((sky.rainIntensity * 3).toInt()) {
                    val rx = player.x + (Math.random()*20 - 10).toFloat()
                    val rz = player.z + (Math.random()*20 - 10).toFloat()
                    particles.emitRainSplash(rx, WorldGen.getHeight(rx.toInt(), rz.toInt()) + 1f, rz)
                }
            }
        }

        particles.update(smoothDt)

        // ── Mobs (throttled: update every other frame when stressed) ──────
        if (!timer.isStressed || frameCount % 2 == 0L) {
            val mobEvents = mobs.update(smoothDt * if(timer.isStressed) 2f else 1f,
                player.x, player.y, player.z, _dayFactor(), sky.rainIntensity)
            for (ev in mobEvents) {
                if (ev.radius > 0f) { particles.emitExplosion(ev.x,ev.y,ev.z,ev.radius); player.takeDamage(ev.damage) }
                else if (ev.damage > 0) { player.takeDamage(ev.damage); particles.emitHit(ev.x,ev.y,ev.z) }
            }
            // Melee hit
            if (touch.breakHeld && !player.inventoryOpen) {
                val (ex,ey,ez) = player.eyePos(); val (ldx2,ldy2,ldz2) = player.lookDir()
                val dmg = mobs.hitEntity(ex,ey,ez, ldx2,ldy2,ldz2)
                if (dmg > 0) particles.emitHit(ex+ldx2*3f, ey+ldy2*3f, ez+ldz2*3f)
            }
        }

        // ── Timed ticks ───────────────────────────────────────────────────
        gravTimer += smoothDt
        if (gravTimer >= GRAVITY_INTERVAL) { gravTimer=0f; world.tickGravity() }
        saveTimer += smoothDt
        if (saveTimer >= SAVE_INTERVAL) { saveTimer=0f; save() }

        // ── Sky ───────────────────────────────────────────────────────────
        sky.update(totalTime, smoothDt)
        sound.tickAmbient(smoothDt, player.y, sky.rainIntensity, sky.thunderFlash > 0.5f)
        val tf = sky.thunderFlash
        GLES20.glClearColor(
            (sky.skyR + tf*0.5f).coerceAtMost(1f),
            (sky.skyG + tf*0.5f).coerceAtMost(1f),
            (sky.skyB + tf*0.6f).coerceAtMost(1f), 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // ── FOV smooth lerp ───────────────────────────────────────────────
        val targetFov = when { player.sprinting&&player.onGround->75f; player.flyMode->78f; else->70f }
        currentFov += (targetFov - currentFov) * (smoothDt * 7f)
        val sw = touch.screenW; val sh = touch.screenH
        if (sw>0f && sh>0f) camera.setProjection(currentFov, sw/sh)

        // ── Camera ────────────────────────────────────────────────────────
        val mvSpd = sqrt(touch.moveX.pow(2) + touch.moveZ.pow(2))
        camera.updateBob(mvSpd * if(player.sprinting) 1.2f else 1f, player.onGround, smoothDt)
        val (ex,ey,ez) = player.eyePos()
        camera.setView(ex,ey,ez, player.yaw, player.pitch)
        camera.computeMVP()

        // Extract frustum planes ONCE per frame for chunk culling
        frustum.extract(camera.mvpMatrix)

        sky.draw(camera.projMatrix, ex,ey,ez, totalTime)

        // ── Chunk management ──────────────────────────────────────────────
        val pcx = Math.floorDiv(player.x.toInt(), CHUNK_WIDTH)
        val pcz = Math.floorDiv(player.z.toInt(), CHUNK_WIDTH)
        updateChunkQueue(pcx, pcz)

        // Preload chunks in background (always, not just when queue runs)
        for (dcx in -renderDistance..renderDistance) for (dcz in -renderDistance..renderDistance) {
            if (dcx*dcx+dcz*dcz > (renderDistance+1)*(renderDistance+1)) continue
            world.ensureChunkLoaded(pcx+dcx, pcz+dcz)
        }

        // Budget-limited rebuild dispatch
        val rBudget = timer.rebuildBudget()
        var rDispatched = 0
        while (rDispatched < rBudget) {
            val p = rebuildQueue.removeFirstOrNull() ?: break
            val key = World.chunkKey(p.first, p.second)
            if (rebuilding.contains(key)) continue
            if (!chunks.containsKey(key)) chunks[key] = Chunk(p.first, p.second)
            val ch = chunks[key]!!
            if (world.isChunkReady(p.first,p.second) && (ch.needsRebuild||world.isChunkDirty(p.first,p.second))) {
                world.clearDirty(p.first,p.second); ch.needsRebuild=false
                rebuilding.add(key)
                val capturedKey = key
                meshScope.launch {
                    ch.buildMesh(world)
                    rebuilding.remove(capturedKey)
                }
                rDispatched++
            }
        }

        // Budget-limited GPU upload
        val uBudget = timer.uploadBudget()
        var uDone = 0
        for ((_,ch) in chunks) {
            if (uDone >= uBudget) break
            if (ch.pendingReady) { ch.uploadPending(); uDone++ }
        }

        // Rebuild dirty chunks (neighbour edits etc.)
        for ((_,ch) in chunks) {
            val key = World.chunkKey(ch.cx, ch.cz)
            if (rebuilding.contains(key)) continue
            if (world.isChunkDirty(ch.cx,ch.cz) && world.isChunkReady(ch.cx,ch.cz)) {
                world.clearDirty(ch.cx,ch.cz); ch.needsRebuild=false
                rebuilding.add(key)
                val capturedKey = key
                meshScope.launch { ch.buildMesh(world); rebuilding.remove(capturedKey) }
            }
        }

        // Unload distant chunks every 10 seconds
        if (frameCount % 600 == 0L) {
            world.unloadDistantChunks(pcx, pcz, renderDistance+3)
            chunks.entries.removeIf { (key,ch) ->
                val cz2=((key shr 20)and 0xFFFFF).toInt().let{if(it>0x7FFFF)it-0x100000 else it}
                val cx2=(key and 0xFFFFF).toInt().let{if(it>0x7FFFF)it-0x100000 else it}
                val far = abs(cx2-pcx)>renderDistance+4 || abs(cz2-pcz)>renderDistance+4
                if(far){ch.freeBuffers()}; far
            }
        }

        // ── Dynamic lighting ──────────────────────────────────────────────
        val lights      = dynLight.update(player.x, player.y, player.z)
        val ambBoost    = dynLight.ambientBoost(player.x, player.y, player.z)

        // ── Fog ───────────────────────────────────────────────────────────
        val rainShrink  = sky.rainIntensity * 0.55f
        val effRd       = renderDistance * (1f - rainShrink)
        val fogStart    = (effRd-1.8f)*CHUNK_WIDTH; val fogEnd=(effRd-0.3f)*CHUNK_WIDTH
        val isUW        = player.underwater
        val fSR = if(isUW) 0f else sky.skyR; val fSG=if(isUW)0.15f else sky.skyG; val fSB=if(isUW)0.55f else sky.skyB
        val fStart=if(isUW)2f else fogStart; val fEnd=if(isUW)12f else fogEnd

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureAtlas.textureId)

        fun applyUniforms(sh: ShaderProgram) {
            sh.use(); sh.setMVP(camera.mvpMatrix); sh.setTexture(0)
            sh.setSunDir(sky.sunDirX,sky.sunDirY,sky.sunDirZ)
            sh.setSkyColor(fSR,fSG,fSB)
            sh.setFogRange(fStart,fEnd)
            sh.setSunBrightness(sky.sunBrightness)
            sh.setAmbient(sky.ambientLight + ambBoost)
            sh.setRain(sky.rainIntensity); sh.setTime(totalTime)
            sh.setWindDir(sky.windX,sky.windZ)
            sh.setUnderwater(if(isUW)1f else 0f)
            sh.setNumLights(lights.size)
            for ((i,l) in lights.withIndex()) { sh.setLightPos(i,l.x,l.y,l.z); sh.setLightCol(i,l.r,l.g,l.b); sh.setLightInt(i,l.intensity) }
        }

        // ── Opaque pass with frustum culling ─────────────────────────────
        GLES20.glDisable(GLES20.GL_BLEND)
        applyUniforms(blockShader); blockShader.setAlpha(1f)
        var drawnChunks = 0; var culledChunks = 0
        for ((_,ch) in chunks) {
            if (abs(ch.cx-pcx) > renderDistance+1 || abs(ch.cz-pcz) > renderDistance+1) continue
            if (!frustum.testChunk(ch.cx, ch.cz)) { culledChunks++; continue }
            ch.drawOpaque(blockShader); drawnChunks++
        }
        MobRenderer.drawAll(mobs.entities, blockShader, camera.mvpMatrix, player.yaw)
        particles.render(blockShader, camera.mvpMatrix)
        blockShader.disableAttribs()

        // ── Water pass with frustum culling ───────────────────────────────
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        applyUniforms(waterShader); waterShader.setAlpha(1f); waterShader.setTime(totalTime)
        for ((_,ch) in chunks) {
            if (abs(ch.cx-pcx) > renderDistance+1 || abs(ch.cz-pcz) > renderDistance+1) continue
            if (!frustum.testChunk(ch.cx,ch.cz)) continue
            ch.drawWater(waterShader)
        }
        waterShader.disableAttribs()
        GLES20.glDepthMask(true)

        // ── HUD update ────────────────────────────────────────────────────
        val bx=floor(player.x.toDouble()).toInt()
        val by2=floor(player.y.toDouble()).toInt()
        val bz2=floor(player.z.toDouble()).toInt()
        val biome=WorldGen.getBiome(bx,bz2).name.lowercase().replaceFirstChar{it.uppercase()}.replace('_',' ')
        val recipes=CraftingSystem.availableRecipes(player.inventory)

        hudUpdater?.invoke(
            player.inventory.selectedSlot, timer.fps,
            "XYZ: $bx/$by2/$bz2",
            if(player.inventory.hotbarBlockId<BlockType.NAMES.size) BlockType.NAMES[player.inventory.hotbarBlockId] else "",
            player.breakProgress, player.health, player.hunger, player.air,
            player.underwater, _dayFactor(), totalTime, "Biome: $biome",
            player.gameMode.displayName, player.onFire, player.inventoryOpen,
            player.inventory.getHotbarBlocks(), player.xpLevel, player.xpPoints, player.score,
            player.inventory.slots.copyOf(), false, recipes, renderDistance,
            sky.weather.name, sky.rainIntensity, sky.thunderFlash,
            mobs.entities.size, drawnChunks, culledChunks
        )
    }

    private fun _dayFactor() = (sky.sunDirY+0.15f).coerceIn(0f,1f)/0.5f

    private fun updateChunkQueue(pcx: Int, pcz: Int) {
        if (pcx==lastPCX && pcz==lastPCZ) return
        lastPCX=pcx; lastPCZ=pcz
        rebuildQueue.clear()
        for (dcx in -renderDistance..renderDistance) for (dcz in -renderDistance..renderDistance) {
            if (dcx*dcx+dcz*dcz > renderDistance*renderDistance) continue
            rebuildQueue.add(Pair(pcx+dcx, pcz+dcz))
        }
        // Nearest-first sort
        rebuildQueue.sortBy { (cx,cz) -> (cx-pcx)*(cx-pcx)+(cz-pcz)*(cz-pcz) }
    }

    private fun blockColorRGB(blk: Int): Triple<Float,Float,Float> = when(blk) {
        BlockType.GRASS -> Triple(0.37f,0.66f,0.18f); BlockType.DIRT -> Triple(0.55f,0.37f,0.20f)
        BlockType.STONE -> Triple(0.54f,0.54f,0.54f); BlockType.SAND -> Triple(0.83f,0.73f,0.41f)
        BlockType.COAL_ORE -> Triple(0.1f,0.1f,0.1f); BlockType.IRON_ORE -> Triple(0.83f,0.69f,0.50f)
        BlockType.DIAMOND_ORE -> Triple(0.25f,0.88f,0.82f); BlockType.GOLD_ORE -> Triple(1f,0.84f,0f)
        else -> Triple(0.6f,0.6f,0.6f)
    }

    private fun save() {
        SaveSystem.saveMeta(context, worldName, SaveSystem.WorldMeta(WorldGen.worldSeed, totalTime))
        SaveSystem.savePlayer(context, worldName, SaveSystem.PlayerState(
            player.x,player.y,player.z, player.yaw,player.pitch,
            player.health,player.hunger, player.xpLevel,player.xpPoints,
            player.inventory.selectedSlot, player.inventory.serialize()
        ))
        world.saveAllChunks()
    }

    fun scrollHotbar(dir: Int) { player.inventory.scrollHotbar(dir) }
    fun setGameMode(mode: GameMode) { player.gameMode = mode }
    fun teleportPlayer(x: Float, y: Float, z: Float) { player.x=x; player.y=y; player.z=z; player.vx=0f; player.vy=0f; player.vz=0f }
    fun destroy() { save(); world.destroy(); meshScope.cancel(); meshPool.shutdown(); sound.destroy() }
}
