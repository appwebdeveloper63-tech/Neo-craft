package com.neocraft.game.ui

import android.content.Context
import android.graphics.*
import android.text.method.ScrollingMovementMethod
import android.view.*
import android.widget.*
import com.neocraft.game.ai.AIAssistant
import com.neocraft.game.ai.BuildingTemplates

/**
 * Floating AI chat panel that slides in from the right edge.
 * Shows conversation history, a text input, and quick-action buttons.
 * Also displays build plans in a structured step-by-step card.
 */
class AIChatView(context: Context) : FrameLayout(context) {

    var onSendMessage:     ((String) -> Unit)? = null
    var onClose:           (() -> Unit)? = null
    var onQuickBuild:      ((String) -> Unit)? = null  // template name
    var onGiveBlock:       ((String, Int) -> Unit)? = null

    private val chatLog  = StringBuilder()
    private lateinit var chatText:  TextView
    private lateinit var inputEt:   EditText
    private lateinit var sendBtn:   Button
    private lateinit var planCard:  LinearLayout
    private lateinit var planTitle: TextView
    private lateinit var planSteps: TextView
    private lateinit var planMats:  TextView
    private lateinit var loadingTv: TextView
    var isLoading = false
        set(v) { field = v; post { loadingTv.visibility = if(v) VISIBLE else GONE } }

    init { buildUI() }

    private fun buildUI() {
        setBackgroundColor(0xEE0a0a18.toInt())
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        // Header
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val titleTv = TextView(context).apply {
            text = "⚡ JARVIS  –  AI Building Assistant"
            textSize = 14f; setTextColor(0xFF60ff20.toInt())
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(context).apply {
            text = "✕"; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF550000.toInt()); setPadding(16,8,16,8)
            setOnClickListener { onClose?.invoke() }
        }
        header.addView(titleTv); header.addView(closeBtn)
        root.addView(header)

        // Quick-build buttons
        val quickScroll = HorizontalScrollView(context)
        val quickRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val templates = listOf("house","cabin","tower","pyramid","bridge","gazebo","lighthouse","well","arch","castle_wall")
        for (t in templates) {
            val btn = Button(context).apply {
                text = "📦 $t"; textSize = 10f; setTextColor(0xFFccffcc.toInt())
                setBackgroundColor(0xFF1a3a1a.toInt()); setPadding(12,6,12,6)
                setOnClickListener { onQuickBuild?.invoke(t) }
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(4,0,4,0) }
            }
            quickRow.addView(btn)
        }
        quickScroll.addView(quickRow)
        root.addView(quickScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0,8,0,8) })

        // Chat history
        chatText = TextView(context).apply {
            text = "JARVIS: Hello! I'm your AI building assistant. Ask me to build something, suggest materials, or place a structure!\n\nTry: \"Build me a castle tower\" or \"What blocks for a medieval house?\"\n\n"
            textSize = 12f; setTextColor(0xFFCCCCCC.toInt())
            typeface = Typeface.MONOSPACE
            movementMethod = ScrollingMovementMethod()
            setBackgroundColor(0xFF111122.toInt()); setPadding(12,12,12,12)
        }
        root.addView(chatText, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Loading indicator
        loadingTv = TextView(context).apply {
            text = "⚡ JARVIS is thinking..."; textSize = 12f
            setTextColor(0xFF60ff20.toInt()); typeface = Typeface.MONOSPACE
            visibility = GONE; gravity = Gravity.CENTER; setPadding(0,8,0,8)
        }
        root.addView(loadingTv)

        // Build plan card (hidden by default)
        planCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF112211.toInt()); setPadding(12,12,12,12)
            visibility = GONE
        }
        planTitle = TextView(context).apply { textSize=14f; setTextColor(0xFF60ff20.toInt()); typeface=Typeface.MONOSPACE }
        planSteps = TextView(context).apply { textSize=11f; setTextColor(0xFFCCCCCC.toInt()); typeface=Typeface.MONOSPACE }
        planMats  = TextView(context).apply { textSize=11f; setTextColor(0xFFffcc44.toInt()); typeface=Typeface.MONOSPACE }
        val planGetBtn = Button(context).apply {
            text = "📦 Get All Materials"; textSize=12f; setTextColor(0xFFffffff.toInt())
            setBackgroundColor(0xFF1a5a1a.toInt()); setPadding(12,8,12,8)
            setOnClickListener { giveAllMaterials() }
        }
        planCard.addView(planTitle); planCard.addView(planSteps); planCard.addView(planMats); planCard.addView(planGetBtn)
        root.addView(planCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0,8,0,8) })

        // Input row
        val inputRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        inputEt = EditText(context).apply {
            hint = "Ask JARVIS..."; textSize = 13f
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF444444.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(12,10,12,10)
            typeface = Typeface.MONOSPACE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendMessage(); true }
        }
        sendBtn = Button(context).apply {
            text = "→"; textSize=16f; setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF60ff20.toInt()); setPadding(16,8,16,8)
            setOnClickListener { sendMessage() }
        }
        inputRow.addView(inputEt, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        inputRow.addView(sendBtn)
        root.addView(inputRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0,8,0,0) })

        addView(root, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun sendMessage() {
        val msg = inputEt.text.toString().trim()
        if (msg.isEmpty() || isLoading) return
        inputEt.setText("")
        appendChat("You: $msg\n\n")
        onSendMessage?.invoke(msg)
    }

    fun appendChat(text: String) {
        post {
            chatLog.append(text)
            chatText.text = chatLog.toString()
            // Auto-scroll to bottom
            val layout = chatText.layout ?: return@post
            val scrollY = layout.getLineTop(chatText.lineCount) - chatText.height
            if (scrollY > 0) chatText.scrollTo(0, scrollY)
        }
    }

    fun showResponse(response: AIAssistant.AIResponse) {
        post {
            appendChat("JARVIS: ${response.message}\n\n")
            if (response.buildPlan != null) showBuildPlan(response.buildPlan)
            isLoading = false
        }
    }

    private var currentPlan: AIAssistant.BuildPlan? = null

    private fun showBuildPlan(plan: AIAssistant.BuildPlan) {
        currentPlan = plan
        post {
            planCard.visibility = VISIBLE
            planTitle.text = "📐 ${plan.title}  [${plan.difficulty}]"
            planSteps.text = plan.steps.mapIndexed { i, s -> "${i+1}. $s" }.joinToString("\n")
            planMats.text  = "Materials needed:\n" + plan.materials.entries.joinToString("\n") { "  • ${it.key}: ${it.value}" }
        }
    }

    private fun giveAllMaterials() {
        val plan = currentPlan ?: return
        for ((blockName, count) in plan.materials) {
            onGiveBlock?.invoke(blockName, count)
        }
        appendChat("JARVIS: ✅ Materials delivered to your inventory! Good luck building!\n\n")
    }

    fun showError(msg: String) {
        post { appendChat("JARVIS: ⚠ $msg\n\n"); isLoading = false }
    }

    private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT  = ViewGroup.LayoutParams.WRAP_CONTENT
}
