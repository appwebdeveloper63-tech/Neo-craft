package com.neocraft.game.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.view.*
import android.widget.*
import com.neocraft.game.audio.SoundEngine
import com.neocraft.game.world.*

/**
 * In-game Settings Panel.
 * Slides over the game; accessed from the pause menu.
 *
 * Sections:
 *  Graphics   — render distance, FOV, head bob, show FPS
 *  Controls   — look sensitivity, invert Y, joystick size
 *  Audio      — master, SFX, ambient volumes; mute all
 *  Gameplay   — difficulty, auto-jump, show coordinates
 *  Account    — display name, sign out
 */
class SettingsView(context: Context) : FrameLayout(context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("neocraft_settings", Context.MODE_PRIVATE)

    // Callbacks the game reads every frame
    var renderDistance: Int  get() = prefs.getInt("render_dist", 6);      private set(_) {}
    var fov: Float           get() = prefs.getFloat("fov", 70f);           private set(_) {}
    var sensitivity: Float   get() = prefs.getFloat("sensitivity", 0.22f); private set(_) {}
    var invertY: Boolean     get() = prefs.getBoolean("invert_y", false);  private set(_) {}
    var headBob: Boolean     get() = prefs.getBoolean("head_bob", true);   private set(_) {}
    var showFPS: Boolean     get() = prefs.getBoolean("show_fps", true);   private set(_) {}
    var showCoords: Boolean  get() = prefs.getBoolean("show_coords", true);private set(_) {}
    var autoJump: Boolean    get() = prefs.getBoolean("auto_jump", false); private set(_) {}
    var masterVol: Float     get() = prefs.getFloat("vol_master", 0.8f);   private set(_) {}
    var sfxVol: Float        get() = prefs.getFloat("vol_sfx", 0.9f);      private set(_) {}
    var ambientVol: Float    get() = prefs.getFloat("vol_ambient", 0.5f);  private set(_) {}

    var onClose:   (() -> Unit)? = null
    var onSignOut: (() -> Unit)? = null
    var soundEngine: SoundEngine? = null

    init { buildUI() }

    private fun buildUI() {
        setBackgroundColor(0xEE0a0a18.toInt())

        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 40)
        }
        scroll.addView(container)

        // Header
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val title = label("⚙  Settings", 20f, 0xFF60ff20.toInt())
        title.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        val closeBtn = smallBtn("✕") { onClose?.invoke() }
        header.addView(title); header.addView(closeBtn)
        container.addView(header)
        container.addView(divider())

        // ── Graphics ──────────────────────────────────────────────────────
        container.addView(sectionLabel("GRAPHICS"))

        container.addView(sliderRow("Render Distance",
            min=2, max=10, current=prefs.getInt("render_dist", 6)) { v ->
            prefs.edit().putInt("render_dist", v).apply()
            soundEngine?.play(SoundEngine.SoundType.CLICK)
        })

        container.addView(sliderRow("Field of View (°)",
            min=50, max=110, current=prefs.getFloat("fov", 70f).toInt()) { v ->
            prefs.edit().putFloat("fov", v.toFloat()).apply()
        })

        container.addView(toggleRow("Head Bob", prefs.getBoolean("head_bob", true)) { on ->
            prefs.edit().putBoolean("head_bob", on).apply()
        })
        container.addView(toggleRow("Show FPS Counter", prefs.getBoolean("show_fps", true)) { on ->
            prefs.edit().putBoolean("show_fps", on).apply()
        })
        container.addView(toggleRow("Show Coordinates", prefs.getBoolean("show_coords", true)) { on ->
            prefs.edit().putBoolean("show_coords", on).apply()
        })
        container.addView(divider())

        // ── Controls ──────────────────────────────────────────────────────
        container.addView(sectionLabel("CONTROLS"))
        container.addView(sliderRowF("Look Sensitivity",
            min=0.05f, max=0.6f, current=prefs.getFloat("sensitivity", 0.22f), steps=22) { v ->
            prefs.edit().putFloat("sensitivity", v).apply()
        })
        container.addView(toggleRow("Invert Y-Axis", prefs.getBoolean("invert_y", false)) { on ->
            prefs.edit().putBoolean("invert_y", on).apply()
        })
        container.addView(toggleRow("Auto-Jump", prefs.getBoolean("auto_jump", false)) { on ->
            prefs.edit().putBoolean("auto_jump", on).apply()
        })
        container.addView(divider())

        // ── Audio ─────────────────────────────────────────────────────────
        container.addView(sectionLabel("AUDIO"))
        container.addView(sliderRowF("Master Volume",
            min=0f, max=1f, current=prefs.getFloat("vol_master", 0.8f), steps=20) { v ->
            prefs.edit().putFloat("vol_master", v).apply()
            soundEngine?.masterVolume = v
        })
        container.addView(sliderRowF("SFX Volume",
            min=0f, max=1f, current=prefs.getFloat("vol_sfx", 0.9f), steps=20) { v ->
            prefs.edit().putFloat("vol_sfx", v).apply()
            soundEngine?.sfxVolume = v
        })
        container.addView(sliderRowF("Ambient Volume",
            min=0f, max=1f, current=prefs.getFloat("vol_ambient", 0.5f), steps=20) { v ->
            prefs.edit().putFloat("vol_ambient", v).apply()
            soundEngine?.ambientVolume = v
        })
        container.addView(toggleRow("Enable Sounds", prefs.getBoolean("sounds_on", true)) { on ->
            prefs.edit().putBoolean("sounds_on", on).apply()
            soundEngine?.enabled = on
        })
        container.addView(divider())

        // ── Account ───────────────────────────────────────────────────────
        container.addView(sectionLabel("ACCOUNT"))
        val signOutBtn = fullBtn("Sign Out", 0xFF550000.toInt()) {
            soundEngine?.play(SoundEngine.SoundType.CLICK)
            onSignOut?.invoke()
        }
        container.addView(signOutBtn, lp(0, 12, 0, 4))
        container.addView(divider())

        // ── About ─────────────────────────────────────────────────────────
        container.addView(sectionLabel("ABOUT"))
        container.addView(label("NeoCraft v8.0  •  AI-Powered Voxel Sandbox", 11f, 0xFF666666.toInt()))
        container.addView(label("Built with Gemini AI  •  Firebase Auth", 11f, 0xFF555555.toInt()))

        addView(scroll, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private fun label(text: String, size: Float, color: Int) = TextView(context).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = Typeface.MONOSPACE; setPadding(0, 4, 0, 4)
    }
    private fun sectionLabel(text: String) = label("── $text ──", 13f, 0xFF60ff20.toInt())
        .also { it.setPadding(0, 16, 0, 8) }
    private fun divider() = View(context).also { it.setBackgroundColor(0xFF222233.toInt()) }
        .also { it.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { setMargins(0,8,0,8) } }
    private fun smallBtn(text: String, action: () -> Unit) = Button(context).apply {
        this.text = text; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF550000.toInt()); setPadding(16,8,16,8)
        setOnClickListener { action() }
    }
    private fun fullBtn(text: String, bg: Int, action: () -> Unit) = Button(context).apply {
        this.text = text; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(bg); typeface = Typeface.MONOSPACE; setPadding(16,14,16,14)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
    private fun lp(l: Int, t: Int, r: Int, b: Int) =
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(l,t,r,b) }

    private fun toggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,4,0,4) }
        val tv  = label(label, 13f, 0xFFCCCCCC.toInt())
        tv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        val sw  = Switch(context).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, on -> onChange(on); soundEngine?.play(SoundEngine.SoundType.CLICK) }
        }
        row.addView(tv); row.addView(sw)
        return row
    }

    private fun sliderRow(label: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0,4,0,4) }
        val valueTv = label("$current", 12f, 0xFF60ff20.toInt())
        val headerRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val lv = label(label, 13f, 0xFFCCCCCC.toInt())
        lv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        headerRow.addView(lv); headerRow.addView(valueTv)
        val sb = SeekBar(context).apply {
            this.min = min; this.max = max; progress = current
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) {
                    valueTv.text = "$p"; if (f) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) { onChange(sb.progress) }
            })
        }
        row.addView(headerRow); row.addView(sb)
        return row
    }

    private fun sliderRowF(label: String, min: Float, max: Float, current: Float, steps: Int,
                            onChange: (Float) -> Unit): LinearLayout {
        val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0,4,0,4) }
        val valueTv = label(String.format("%.2f", current), 12f, 0xFF60ff20.toInt())
        val headerRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val lv = label(label, 13f, 0xFFCCCCCC.toInt())
        lv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        headerRow.addView(lv); headerRow.addView(valueTv)
        val init = ((current - min) / (max - min) * steps).toInt()
        val sb = SeekBar(context).apply {
            this.min = 0; this.max = steps; progress = init
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) {
                    val v = min + (max - min) * p / steps
                    valueTv.text = String.format("%.2f", v); if (f) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    val v = min + (max - min) * sb.progress / steps; onChange(v)
                }
            })
        }
        row.addView(headerRow); row.addView(sb)
        return row
    }

    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT  = ViewGroup.LayoutParams.WRAP_CONTENT
}
