package com.neocraft.game

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.neocraft.game.ai.*
import com.neocraft.game.audio.SoundEngine
import com.neocraft.game.ui.SettingsView
import android.content.Intent
import com.neocraft.game.auth.*
import com.neocraft.game.ui.*
import com.neocraft.game.world.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var glView:  GameSurfaceView
    private lateinit var hudView: HudView
    private lateinit var aiChat:  AIChatView

    private lateinit var settingsView: SettingsView
    private val auth      = AuthManager(this@MainActivity)
    private val assistant = AIAssistant()
    private val scope     = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var aiPanelVisible = false
    private var playTimeStart  = 0L
    private var blocksPlaced   = 0
    private var blocksDestroyed = 0

    companion object { const val WORLD_NAME = "neocraft_world" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // Configure Auth
        auth.configure(
            apiKey    = BuildConfigHelper.firebaseApiKey,
            projectId = BuildConfigHelper.firebaseProjectId
        )

        // Configure AI
        assistant.configure(
            geminiApiKey = BuildConfigHelper.geminiApiKey,
            openaiApiKey = BuildConfigHelper.openaiApiKey
        )

        // World seed
        val meta = SaveSystem.loadMeta(this, WORLD_NAME)
        val seed = meta?.seed ?: System.currentTimeMillis()

        val worldName = intent.getStringExtra("worldName") ?: WORLD_NAME
        val worldSeed = intent.getLongExtra("worldSeed", seed)
        glView  = GameSurfaceView(this, worldName, worldSeed)
        hudView = HudView(this, glView.touch)
        aiChat  = AIChatView(this)

        settingsView = SettingsView(this).apply {
            soundEngine = glView.renderer.sound
            onClose = { toggleSettings() }
            onSignOut = { auth.signOut(); finish() }
        }
        glView.renderer.settings = settingsView
        setupAIChat()
        setupHUD()
        setupAuth()

        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(hudView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // AI chat: right-side panel (40% of screen width)
        val aiParams = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.40f).toInt(),
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = android.view.Gravity.END }
        aiChat.visibility = View.GONE
        root.addView(aiChat, aiParams)

        val settingsParams = FrameLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.45f).toInt(),
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = android.view.Gravity.END }
        settingsView.visibility = View.GONE
        root.addView(settingsView, settingsParams)
        setContentView(root)
        playTimeStart = System.currentTimeMillis()
    }

    private fun setupHUD() {
        // GL → HUD bridge
        glView.renderer.hudUpdater = {
            sel, fps, coords, blk, bp, hp, hg, air, uw, df, tt, biome,
            mode, fire, inv, hotbar, xpLv, xpPts, sc, invSlots, crafting,
            recipes, rd, weather, rain, thunder, mobCount, drawn, culled ->
            runOnUiThread {
                hudView.setPlayerSelectedSlot(sel)
                hudView.update(sel, fps, coords, blk, bp, hp, hg, air, uw, df, tt, biome,
                    mode, fire, inv, hotbar, xpLv, xpPts, sc, invSlots, crafting,
                    recipes, rd, weather, rain, thunder, mobCount, drawn, culled)
            }
        }

        // Pause menu
        hudView.pauseCallback = { runOnUiThread { hudView.paused = false; hudView.postInvalidate() } }
        hudView.settingsCallback = { runOnUiThread { toggleSettings() } }
        hudView.quitCallback  = {
            saveUserStats()
            finish()
        }
        hudView.gameModeCallback = {
            val modes = GameMode.values()
            val next = modes[(glView.renderer.player.gameMode.ordinal + 1) % modes.size]
            glView.renderer.setGameMode(next)
        }
        hudView.craftCallback = { recipe ->
            if (CraftingSystem.craft(glView.renderer.player.inventory, recipe))
                glView.renderer.sound.play(SoundEngine.SoundType.CRAFT_SUCCESS)
        }

        // AI toggle button — small floating button top-right
        val aiBtnParams = FrameLayout.LayoutParams(120, 80).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            setMargins(0, 80, 8, 0)
        }
        val aiToggleBtn = Button(this).apply {
            text = "⚡ AI"; textSize = 12f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF60ff20.toInt())
            setOnClickListener { toggleAIPanel() }
        }
        // We'll inject this into the HUD post-layout
        hudView.post {
            (hudView.parent as? FrameLayout)?.addView(aiToggleBtn, aiBtnParams)
        }
    }

    private fun setupAIChat() {
        aiChat.onClose = { toggleAIPanel() }

        // User sends a message → call AI
        aiChat.onSendMessage = { message ->
            aiChat.isLoading = true
            val renderer = glView.renderer
            val player   = renderer.player
            val (ex,ey,ez) = player.eyePos()
            val ray = renderer.world.raycast(ex,ey,ez,
                player.lookDir().first, player.lookDir().second, player.lookDir().third)
            val lookingAt = if(ray.hit) BlockType.NAMES.getOrNull(
                renderer.world.getBlock(ray.bx,ray.by,ray.bz)) else null
            val invItems = player.inventory.slots
                .filterNotNull().filter{!it.isEmpty}
                .map { BlockType.NAMES.getOrNull(it.blockId) ?: "Unknown" }

            val ctx = AIAssistant.PlayerContext(
                x             = player.x, y = player.y, z = player.z,
                biome         = WorldGen.getBiome(player.x.toInt(), player.z.toInt()).name,
                lookingAtBlock = lookingAt,
                inventoryItems = invItems,
                health        = player.health, hunger = player.hunger,
                gameMode      = player.gameMode.displayName,
                isDay         = try { renderer.sky.sunDirY > 0f } catch(e: Exception) { true },
                weather       = try { renderer.sky.weather.name } catch(e: Exception) { "CLEAR" }
            )

            scope.launch {
                val response = withContext(Dispatchers.IO) {
                    assistant.chat(message, ctx)
                }
                // Execute any AI commands
                executeAICommands(response)
                aiChat.showResponse(response)
            }
        }

        // Quick-build template buttons
        aiChat.onQuickBuild = { templateName ->
            val renderer = glView.renderer
            val player   = renderer.player
            val px = player.x.toInt(); val py = player.y.toInt(); val pz = player.z.toInt()
            // Find ground at player position + 3 blocks forward
            val fwd = player.lookDir()
            val bx = (px + fwd.first * 5).toInt()
            val bz = (pz + fwd.third * 5).toInt()
            val surfY = WorldGen.getHeight(bx, bz)
            val placed = BuildingTemplates.place(renderer.world, templateName, bx, surfY, bz)
            if (placed) {
                aiChat.appendChat("JARVIS: ✅ Placed ${templateName.replace('_',' ')} at $bx,$surfY,$bz!\n\n")
            } else {
                aiChat.appendChat("JARVIS: ⚠ Unknown template '$templateName'. Try: ${BuildingTemplates.getNames().take(5).joinToString(", ")}\n\n")
            }
        }

        // Give materials from build plan
        aiChat.onGiveBlock = { blockName, count ->
            val blockId = BlockType.NAMES.indexOfFirst {
                it.equals(blockName, ignoreCase = true)
            }.takeIf { it >= 0 }
            if (blockId != null) {
                glView.renderer.player.inventory.addItem(blockId, count)
            }
        }
    }

    private fun executeAICommands(response: AIAssistant.AIResponse) {
        for (cmd in response.commands) {
            when (cmd.type) {
                AIAssistant.CommandType.GIVE_BLOCK -> {
                    val blockName = cmd.args.getOrNull(0) ?: continue
                    val count = cmd.args.getOrNull(1)?.toIntOrNull() ?: 32
                    val blockId = BlockType.NAMES.indexOfFirst { it.equals(blockName, ignoreCase = true) }
                    if (blockId >= 0) glView.renderer.player.inventory.addItem(blockId, count)
                }
                AIAssistant.CommandType.TELEPORT -> {
                    val x = cmd.args.getOrNull(0)?.toFloatOrNull() ?: continue
                    val y = cmd.args.getOrNull(1)?.toFloatOrNull() ?: continue
                    val z = cmd.args.getOrNull(2)?.toFloatOrNull() ?: continue
                    glView.renderer.teleportPlayer(x, y, z)
                }
                AIAssistant.CommandType.SET_GAMEMODE -> {
                    val mode = when(cmd.args.getOrNull(0)?.lowercase()) {
                        "creative"  -> GameMode.CREATIVE
                        "adventure" -> GameMode.ADVENTURE
                        "spectator" -> GameMode.SPECTATOR
                        else        -> GameMode.SURVIVAL
                    }
                    glView.renderer.setGameMode(mode)
                }
                AIAssistant.CommandType.PLACE_STRUCTURE -> {
                    val name = cmd.args.getOrNull(0) ?: continue
                    val p = glView.renderer.player
                    val bx = p.x.toInt(); val bz = p.z.toInt()
                    BuildingTemplates.place(glView.renderer.world, name, bx, WorldGen.getHeight(bx,bz), bz)
                }
                AIAssistant.CommandType.CLEAR_INVENTORY -> {
                    glView.renderer.player.inventory.clear()
                }
                else -> {} // SET_TIME, SET_WEATHER handled by sky renderer
            }
        }
    }

    private fun setupAuth() {
        // Show logged-in user name on HUD (we'll pass it via updateDisplayName)
        auth.onAuthStateChanged = { user ->
            runOnUiThread {
                if (user != null) {
                    // Show welcome toast
                    val greeting = if (user.isAnonymous) "Playing as Guest" else "Welcome, ${user.displayName}!"
                    Toast.makeText(this, greeting, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleAIPanel() {
        aiPanelVisible = !aiPanelVisible
        aiChat.visibility = if (aiPanelVisible) View.VISIBLE else View.GONE
        // Pause movement when AI is open so player doesn't walk off a cliff
        glView.touch.moveX = 0f; glView.touch.moveZ = 0f
    }

    private var settingsVisible = false
    private fun toggleSettings() {
        settingsVisible = !settingsVisible
        settingsView.visibility = if (settingsVisible) View.VISIBLE else View.GONE
        glView.touch.moveX = 0f; glView.touch.moveZ = 0f
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> { glView.renderer.scrollHotbar(-1); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { glView.renderer.scrollHotbar(1);  return true }
            KeyEvent.KEYCODE_BACK -> {
                if (settingsVisible) { toggleSettings(); return true }
                if (aiPanelVisible) { toggleAIPanel(); return true }
                runOnUiThread { hudView.paused = !hudView.paused; hudView.postInvalidate() }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun saveUserStats() {
        val playSeconds = ((System.currentTimeMillis() - playTimeStart) / 1000).toInt()
        val stats = AuthManager.UserStats(
            totalPlayTimeSeconds = playSeconds,
            score                = glView.renderer.player.score.toInt(),
            blocksPlaced         = blocksPlaced,
            blocksDestroyed      = blocksDestroyed,
            mobsKilled           = 0
        )
        scope.launch { auth.saveUserStats(stats) }
    }

    override fun onResume()  { super.onResume();  glView.onResume() }
    override fun onPause()   { super.onPause();   glView.onPause()  }
    override fun onDestroy() {
        super.onDestroy()
        saveUserStats()
        scope.cancel()
        auth.destroy()
        glView.destroy()
    }
}
