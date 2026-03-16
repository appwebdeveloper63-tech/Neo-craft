package com.neocraft.game.auth

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.neocraft.game.MainActivity
import com.neocraft.game.world.SaveSystem
import java.text.SimpleDateFormat
import java.util.*

/**
 * World Selection screen — shown after login.
 * Lists saved worlds, lets user create new ones with custom seeds,
 * rename, delete, and launch.
 *
 * Each world shows: name, biome preview colour, date last played,
 * play time, and a delete button.
 */
class WorldSelectActivity : AppCompatActivity() {

    private lateinit var worldList: LinearLayout
    private lateinit var newWorldName: EditText
    private lateinit var seedField: EditText
    private lateinit var displayNameTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUI()
        refreshWorldList()
    }

    private fun buildUI() {
        val root = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0a0a14.toInt())
            setPadding(32, 60, 32, 60)
        }
        root.addView(container)

        // Header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val titleTv = tv("⛏  Select World", 22f, 0xFF60ff20.toInt())
        titleTv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        displayNameTv = tv("", 13f, 0xFF888888.toInt())
        header.addView(titleTv); header.addView(displayNameTv)
        container.addView(header)

        // Username from intent
        val uname = intent.getStringExtra("displayName") ?: "Explorer"
        displayNameTv.text = "👤 $uname"

        container.addView(div())

        // World list (populated by refreshWorldList)
        worldList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(worldList)
        container.addView(div())

        // Create new world section
        container.addView(tv("── CREATE NEW WORLD ──", 13f, 0xFF60ff20.toInt())
            .also { it.setPadding(0,16,0,8) })

        newWorldName = et("World name (e.g. MyWorld)")
        seedField    = et("World seed (optional, leave blank for random)")
        container.addView(newWorldName, lp())
        container.addView(seedField,    lp())

        val createBtn = btn("▶  Create & Play", 0xFF1a5a1a.toInt()) { createWorld() }
        container.addView(createBtn, lp())

        setContentView(root)
    }

    private fun refreshWorldList() {
        worldList.removeAllViews()
        val worlds = SaveSystem.listWorlds(this)

        if (worlds.isEmpty()) {
            worldList.addView(tv("No saved worlds yet. Create one below!", 13f, 0xFF666666.toInt())
                .also { it.setPadding(0, 16, 0, 16) })
            return
        }

        for (worldName in worlds) {
            val meta = SaveSystem.loadMeta(this, worldName)
            val card = buildWorldCard(worldName, meta)
            worldList.addView(card, lp(bot = 12))
        }
    }

    private fun buildWorldCard(
        worldName: String,
        meta: SaveSystem.WorldMeta?
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1a1a2e.toInt())
            setPadding(16, 16, 16, 16)
        }

        // Colour swatch (derived from world seed)
        val seed = meta?.seed ?: 0L
        val hue = (seed and 0xFF).toFloat() * 360f / 255f
        val swatchColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.5f, 0.6f))
        val swatch = View(this).apply {
            setBackgroundColor(swatchColor)
            layoutParams = LinearLayout.LayoutParams(8, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { setMargins(0, 0, 16, 0) }
        }
        card.addView(swatch)

        // Info column
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        info.addView(tv(worldName, 16f, 0xFFFFFFFF.toInt()))

        val date = meta?.let {
            val sdf = SimpleDateFormat("MMM d yyyy", Locale.getDefault())
            "Last played: ${sdf.format(Date())}"
        } ?: "New world"
        info.addView(tv(date, 11f, 0xFF888888.toInt()))
        info.addView(tv("Seed: ${meta?.seed ?: "random"}", 11f, 0xFF555577.toInt()))
        card.addView(info)

        // Buttons column
        val btns = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        btns.addView(smallBtn("▶  Play") { launchWorld(worldName) })
        btns.addView(smallBtn("🗑 Delete") { deleteWorld(worldName) })
        card.addView(btns)
        return card
    }

    private fun createWorld() {
        var name = newWorldName.text.toString().trim()
        if (name.isEmpty()) name = "World${System.currentTimeMillis() % 10000}"
        val seedStr = seedField.text.toString().trim()
        val seed = seedStr.toLongOrNull() ?: System.currentTimeMillis()

        // Save initial metadata
        SaveSystem.saveMeta(this, name, SaveSystem.WorldMeta(seed = seed, totalTime = 0f))

        launchWorld(name, seed)
    }

    private fun launchWorld(worldName: String, seed: Long? = null) {
        val meta = seed?.let { SaveSystem.WorldMeta(it, 0f) }
            ?: SaveSystem.loadMeta(this, worldName)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("worldName", worldName)
            putExtra("worldSeed", meta?.seed ?: System.currentTimeMillis())
        }
        startActivity(intent)
    }

    private fun deleteWorld(worldName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete \"$worldName\"?")
            .setMessage("This cannot be undone. All blocks and progress will be lost.")
            .setPositiveButton("Delete") { _, _ ->
                SaveSystem.deleteWorld(this, worldName)
                refreshWorldList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun tv(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); typeface = Typeface.MONOSPACE
    }
    private fun et(hint: String) = EditText(this).apply {
        this.hint = hint; textSize = 14f
        setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
        setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(16, 14, 16, 14)
        typeface = Typeface.MONOSPACE
    }
    private fun btn(text: String, bg: Int, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 15f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(bg); typeface = Typeface.MONOSPACE; setPadding(16, 14, 16, 14)
        setOnClickListener { action() }
    }
    private fun smallBtn(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF223344.toInt()); setPadding(8, 6, 8, 6)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(0,3,0,3) }
    }
    private fun div() = View(this).apply {
        setBackgroundColor(0xFF222233.toInt())
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { setMargins(0,12,0,12) }
    }
    private fun lp(top: Int = 4, bot: Int = 4) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0,top,0,bot) }

    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT  = ViewGroup.LayoutParams.WRAP_CONTENT
}
