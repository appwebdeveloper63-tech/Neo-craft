package com.neocraft.game.ai

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * NeoCraft AI Building Assistant
 *
 * Uses Google Gemini (primary) with OpenAI fallback.
 * Provides:
 *  - Building structure suggestions and blueprints
 *  - Block combination advice
 *  - Step-by-step build guides
 *  - Creative build ideas based on biome/context
 *  - Command execution (give blocks, teleport, set time)
 */
class AIAssistant {

    // ── Configuration (set from BuildConfig / secure storage) ─────────────
    private var geminiKey   = ""
    private var openaiKey   = ""
    private var currentModel = Model.GEMINI

    enum class Model { GEMINI, OPENAI }

    // ── Conversation history (kept in memory, max 20 turns) ───────────────
    private val history = ArrayDeque<Pair<String, String>>(20) // (role, content)

    // ── AI command result ─────────────────────────────────────────────────
    data class AIResponse(
        val message: String,
        val commands: List<AICommand> = emptyList(),
        val buildPlan: BuildPlan? = null,
        val error: String? = null
    )

    data class AICommand(
        val type: CommandType,
        val args: List<String>
    )

    enum class CommandType {
        GIVE_BLOCK,      // give player a block
        TELEPORT,        // move player to coords
        SET_TIME,        // set day/night
        SET_WEATHER,     // set weather
        CLEAR_INVENTORY, // clear inventory
        PLACE_STRUCTURE, // place a template structure
        SET_GAMEMODE     // switch game mode
    }

    data class BuildPlan(
        val title: String,
        val description: String,
        val steps: List<String>,
        val materials: Map<String, Int>, // blockName -> count
        val difficulty: String           // Easy / Medium / Hard / Expert
    )

    fun configure(geminiApiKey: String, openaiApiKey: String = "") {
        geminiKey  = geminiApiKey
        openaiKey  = openaiApiKey
        currentModel = if (geminiApiKey.isNotEmpty()) Model.GEMINI else Model.OPENAI
    }

    // ── System prompt: gives the AI its building-expert personality ────────
    private val systemPrompt = """
You are JARVIS, an expert Minecraft-style building assistant inside NeoCraft, a voxel sandbox game.

YOUR ROLE:
- Help players design and build amazing structures
- Suggest creative builds based on their environment and biome  
- Provide step-by-step construction guides
- Recommend optimal block combinations for aesthetics and function
- Answer questions about blocks, biomes, crafting, and survival

AVAILABLE BLOCKS (most important ones):
Stone, Cobblestone, Stone Bricks, Mossy Stone Bricks, Smooth Stone, Granite, Diorite, Andesite
Oak/Birch/Spruce/Jungle/Acacia/Dark Oak Planks and Logs, Bricks, Glass, Obsidian
Sand, Sandstone, Gravel, Clay, Dirt, Grass, Snow Block, Ice, Packed Ice
Coal/Iron/Gold/Diamond/Copper Ore and Blocks, Netherite Block, Ancient Debris
Deepslate, Blackstone, Basalt, Calcite, Tuff, Amethyst, Sculk
Wool (White/Red/Blue/Yellow/Green/Black), Concrete (Gray/White/Red/Blue)
Terracotta, Mud, Moss Block, Podzol, Mycelium
TNT, Crafting Table, Furnace, Chest, Torch, Lantern, Bookshelf
Glowstone, Sea Lantern, Shroomlight, Magma Block, Netherrack
Prismarine, Honey Block, Slime Block, Dripstone Block
Nether Brick, Red Nether Brick, Quartz Block, Purpur Block
Warped/Crimson Stems and Leaves, Wart Block

GAME COMMANDS YOU CAN ISSUE (include in your response as JSON after your message):
- GIVE_BLOCK: <BlockName> <amount> — give the player materials  
- TELEPORT: <x> <y> <z> — move player to location
- SET_TIME: day|night|sunrise|sunset
- SET_WEATHER: clear|rain|thunder
- SET_GAMEMODE: survival|creative|adventure|spectator
- PLACE_STRUCTURE: <structureName> — place a pre-built template

RESPONSE FORMAT:
Always respond conversationally first, then if issuing commands, add:
[COMMANDS]
{"commands":[{"type":"GIVE_BLOCK","args":["OakPlanks","64"]},{"type":"SET_GAMEMODE","args":["creative"]}]}
[/COMMANDS]

If providing a build plan, add:
[BUILDPLAN]
{"title":"Medieval Tower","difficulty":"Medium","description":"A classic stone tower with battlements","steps":["Place 10x10 stone foundation","Build walls up 20 blocks","Add windows every 5 blocks","Crown with battlements"],"materials":{"Stone Bricks":400,"Cobblestone":200,"Glass":20,"Torch":8}}
[/BUILDPLAN]

Keep responses friendly, concise, and practical. Max 3 sentences for simple questions, longer for build plans.
""".trimIndent()

    // ── Main chat function ─────────────────────────────────────────────────
    suspend fun chat(
        userMessage: String,
        playerContext: PlayerContext
    ): AIResponse = withContext(Dispatchers.IO) {
        if (geminiKey.isEmpty() && openaiKey.isEmpty()) {
            return@withContext AIResponse(
                message = "AI assistant not configured. Please add your Gemini API key in Settings.",
                error = "No API key"
            )
        }

        // Add context to the user message
        val contextualMessage = buildContextMessage(userMessage, playerContext)

        // Keep history bounded
        if (history.size >= 20) history.removeFirst()
        history.addLast(Pair("user", contextualMessage))

        return@withContext try {
            val rawResponse = when (currentModel) {
                Model.GEMINI -> callGemini(contextualMessage)
                Model.OPENAI -> callOpenAI(contextualMessage)
            }
            history.addLast(Pair("assistant", rawResponse))
            parseResponse(rawResponse)
        } catch (e: Exception) {
            // Fallback to other model
            Log.e("AIAssistant", "Primary model failed: ${e.message}")
            if (currentModel == Model.GEMINI && openaiKey.isNotEmpty()) {
                currentModel = Model.OPENAI
                try {
                    val raw = callOpenAI(contextualMessage)
                    history.addLast(Pair("assistant", raw))
                    parseResponse(raw)
                } catch (e2: Exception) {
                    AIResponse(message = "AI is unavailable right now. Try again in a moment.", error = e2.message)
                }
            } else {
                AIResponse(message = "Could not reach the AI. Check your internet connection.", error = e.message)
            }
        }
    }

    // ── Gemini API call ───────────────────────────────────────────────────
    private fun callGemini(userMsg: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$geminiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        // Build contents array: system + history + current
        val contents = JSONArray()

        // System instruction as first user turn (Gemini flash supports it)
        val sysContent = JSONObject()
        sysContent.put("role", "user")
        sysContent.put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        contents.put(sysContent)
        val sysReply = JSONObject()
        sysReply.put("role", "model")
        sysReply.put("parts", JSONArray().put(JSONObject().put("text", "Understood! I'm JARVIS, your NeoCraft building assistant. How can I help you build something amazing?")))
        contents.put(sysReply)

        // Conversation history
        for ((role, content) in history.dropLast(1)) {
            val turn = JSONObject()
            turn.put("role", if (role == "assistant") "model" else "user")
            turn.put("parts", JSONArray().put(JSONObject().put("text", content)))
            contents.put(turn)
        }

        // Current message
        val curr = JSONObject()
        curr.put("role", "user")
        curr.put("parts", JSONArray().put(JSONObject().put("text", userMsg)))
        contents.put(curr)

        val body = JSONObject()
        body.put("contents", contents)
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.8)
        genConfig.put("maxOutputTokens", 1024)
        body.put("generationConfig", genConfig)

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    // ── OpenAI API call ───────────────────────────────────────────────────
    private fun callOpenAI(userMsg: String): String {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $openaiKey")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 30000

        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        for ((role, content) in history) {
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        val body = JSONObject()
        body.put("model", "gpt-4o-mini")
        body.put("messages", messages)
        body.put("max_tokens", 1024)
        body.put("temperature", 0.8)

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    // ── Parse AI response for commands and build plans ────────────────────
    private fun parseResponse(raw: String): AIResponse {
        var message = raw
        val commands = mutableListOf<AICommand>()
        var buildPlan: BuildPlan? = null

        // Extract [COMMANDS] block
        val cmdRegex = Regex("""\[COMMANDS\](.*?)\[/COMMANDS\]""", RegexOption.DOT_MATCHES_ALL)
        cmdRegex.find(raw)?.let { match ->
            message = message.replace(match.value, "").trim()
            try {
                val json = JSONObject(match.groupValues[1].trim())
                val cmdsArr = json.getJSONArray("commands")
                for (i in 0 until cmdsArr.length()) {
                    val cmd = cmdsArr.getJSONObject(i)
                    val type = CommandType.valueOf(cmd.getString("type"))
                    val args = mutableListOf<String>()
                    val argsArr = cmd.getJSONArray("args")
                    for (j in 0 until argsArr.length()) args.add(argsArr.getString(j))
                    commands.add(AICommand(type, args))
                }
            } catch (e: Exception) { Log.w("AIAssistant", "Command parse error: ${e.message}") }
        }

        // Extract [BUILDPLAN] block
        val planRegex = Regex("""\[BUILDPLAN\](.*?)\[/BUILDPLAN\]""", RegexOption.DOT_MATCHES_ALL)
        planRegex.find(raw)?.let { match ->
            message = message.replace(match.value, "").trim()
            try {
                val json = JSONObject(match.groupValues[1].trim())
                val stepsArr = json.getJSONArray("steps")
                val steps = (0 until stepsArr.length()).map { stepsArr.getString(it) }
                val mats = mutableMapOf<String, Int>()
                val matsObj = json.optJSONObject("materials")
                matsObj?.keys()?.forEach { k -> mats[k] = matsObj.getInt(k) }
                buildPlan = BuildPlan(
                    title       = json.optString("title", "Build Plan"),
                    description = json.optString("description", ""),
                    steps       = steps,
                    materials   = mats,
                    difficulty  = json.optString("difficulty", "Medium")
                )
            } catch (e: Exception) { Log.w("AIAssistant", "BuildPlan parse error: ${e.message}") }
        }

        return AIResponse(message.trim(), commands, buildPlan)
    }

    // ── Context injection ─────────────────────────────────────────────────
    private fun buildContextMessage(userMsg: String, ctx: PlayerContext): String {
        return """
[Player Context]
Position: ${ctx.x.toInt()}, ${ctx.y.toInt()}, ${ctx.z.toInt()}
Biome: ${ctx.biome}
Looking at: ${ctx.lookingAtBlock ?: "air"}
Inventory: ${ctx.inventoryItems.take(9).joinToString(", ")}
Health: ${ctx.health}/20  Hunger: ${ctx.hunger}/20
GameMode: ${ctx.gameMode}
Time: ${if (ctx.isDay) "Day" else "Night"}
Weather: ${ctx.weather}

[Player says]: $userMsg
""".trimIndent()
    }

    data class PlayerContext(
        val x: Float, val y: Float, val z: Float,
        val biome: String,
        val lookingAtBlock: String?,
        val inventoryItems: List<String>,
        val health: Int, val hunger: Int,
        val gameMode: String,
        val isDay: Boolean,
        val weather: String
    )

    fun clearHistory() { history.clear() }
}
