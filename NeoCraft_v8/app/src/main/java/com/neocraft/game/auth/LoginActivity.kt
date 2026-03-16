package com.neocraft.game.auth

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.neocraft.game.MainActivity
import com.neocraft.game.auth.WorldSelectActivity
import kotlinx.coroutines.*

/**
 * Login / Sign-Up screen.
 * Shown on first launch. "Play as Guest" skips auth.
 * Clean dark theme matching the game aesthetic.
 */
class LoginActivity : AppCompatActivity() {

    private val auth  = AuthManager(this@LoginActivity)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Views (created programmatically — no layout XML needed)
    private lateinit var titleLbl:    TextView
    private lateinit var emailEt:     EditText
    private lateinit var passwordEt:  EditText
    private lateinit var nameEt:      EditText
    private lateinit var signupBtn:   Button
    private lateinit var loginBtn:    Button
    private lateinit var guestBtn:    Button
    private lateinit var toggleLbl:   TextView
    private lateinit var statusLbl:   TextView
    private lateinit var loadingBar:  ProgressBar

    private var isSignUpMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure auth with your Firebase keys (set in BuildConfig)
        auth.configure(
            apiKey    = BuildConfigHelper.firebaseApiKey,
            projectId = BuildConfigHelper.firebaseProjectId
        )

        // Check if already signed in
        auth.onAuthStateChanged = { user ->
            if (user != null) launchGame()
        }

        buildUI()
    }

    private fun buildUI() {
        val ctx = this
        val root = ScrollView(ctx)
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            setBackgroundColor(0xFF0a0a14.toInt())
            setPadding(64, 120, 64, 64)
        }
        root.addView(container)

        // Title / Logo
        titleLbl = TextView(ctx).apply {
            text = "⛏ NeoCraft"
            textSize = 36f
            setTextColor(0xFF60ff20.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 8)
        }
        val subtitleLbl = TextView(ctx).apply {
            text = "Create an account to save your world"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 48)
        }

        // Display name (signup only)
        nameEt = EditText(ctx).apply {
            hint = "Display Name"; textSize = 16f; inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(24,20,24,20)
        }

        emailEt = EditText(ctx).apply {
            hint = "Email"; textSize = 16f; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(24,20,24,20)
        }

        passwordEt = EditText(ctx).apply {
            hint = "Password (min 6 chars)"; textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            setBackgroundColor(0xFF1a1a2e.toInt()); setPadding(24,20,24,20)
        }

        statusLbl = TextView(ctx).apply {
            textSize = 14f; setTextColor(0xFFff4444.toInt()); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,8,0,8)
        }

        loadingBar = ProgressBar(ctx).apply { visibility = View.GONE }

        signupBtn = makeButton("Create Account", 0xFF1a5a1a.toInt()) {
            if (isSignUpMode) doSignUp() else doSignIn()
        }

        loginBtn = makeButton("Sign In", 0xFF1a3a5a.toInt()) {
            if (!isSignUpMode) doSignIn() else doSignUp()
        }

        guestBtn = makeButton("▶  Play as Guest", 0xFF2a2a2a.toInt()) { doGuest() }

        toggleLbl = TextView(ctx).apply {
            text = "Already have an account? Sign In instead"
            textSize = 13f; setTextColor(0xFF4488ff.toInt()); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE; setPadding(0,16,0,0)
            setOnClickListener { toggleMode() }
        }

        val divider = TextView(ctx).apply {
            text = "─────  or  ─────"
            textSize = 12f; setTextColor(0xFF444444.toInt()); gravity = Gravity.CENTER; setPadding(0,24,0,24)
        }

        val margin8 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0,8,0,8) }

        container.addView(titleLbl)
        container.addView(subtitleLbl)
        container.addView(nameEt, margin8)
        container.addView(emailEt, margin8)
        container.addView(passwordEt, margin8)
        container.addView(statusLbl)
        container.addView(loadingBar)
        container.addView(signupBtn, margin8)
        container.addView(toggleLbl)
        container.addView(divider)
        container.addView(guestBtn, margin8)

        setContentView(root)
    }

    private fun makeButton(label: String, bgColor: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label; textSize = 16f; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(bgColor); typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 20, 24, 20)
            setOnClickListener { action() }
        }

    private fun toggleMode() {
        isSignUpMode = !isSignUpMode
        if (isSignUpMode) {
            nameEt.visibility    = View.VISIBLE
            signupBtn.text       = "Create Account"
            toggleLbl.text       = "Already have an account? Sign In"
        } else {
            nameEt.visibility    = View.GONE
            signupBtn.text       = "Sign In"
            toggleLbl.text       = "No account? Create one"
        }
        statusLbl.text = ""
    }

    private fun doSignUp() {
        val name  = nameEt.text.toString().trim()
        val email = emailEt.text.toString().trim()
        val pass  = passwordEt.text.toString()
        if (isSignUpMode && name.isEmpty()) { statusLbl.text = "Please enter a display name."; return }
        if (email.isEmpty()) { statusLbl.text = "Please enter your email."; return }
        if (pass.length < 6) { statusLbl.text = "Password must be at least 6 characters."; return }
        setLoading(true)
        scope.launch {
            val result = if (isSignUpMode) auth.signUp(email, pass, name) else auth.signIn(email, pass)
            setLoading(false)
            when (result) {
                is AuthManager.AuthResult.Success -> launchGame()
                is AuthManager.AuthResult.Error   -> statusLbl.text = result.message
            }
        }
    }

    private fun doSignIn() = doSignUp()

    private fun doGuest() {
        setLoading(true)
        scope.launch {
            auth.signInAnonymously()
            setLoading(false)
            launchGame()
        }
    }

    private fun setLoading(on: Boolean) {
        loadingBar.visibility = if (on) View.VISIBLE else View.GONE
        signupBtn.isEnabled   = !on
        guestBtn.isEnabled    = !on
        statusLbl.text        = if (on) "Connecting..." else ""
        statusLbl.setTextColor(if(on) 0xFF888888.toInt() else 0xFFff4444.toInt())
    }

    private fun launchGame() {
        val user = auth.user
        val intent = Intent(this, WorldSelectActivity::class.java).apply {
            putExtra("displayName", user?.displayName ?: "Explorer")
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel(); auth.destroy() }
}

/** Helper to read BuildConfig values safely. */
object BuildConfigHelper {
    val firebaseApiKey     get() = tryGet("FIREBASE_API_KEY")
    val firebaseProjectId  get() = tryGet("FIREBASE_PROJECT_ID")
    val geminiApiKey       get() = tryGet("GEMINI_API_KEY")
    val openaiApiKey       get() = tryGet("OPENAI_API_KEY")

    private fun tryGet(name: String): String = try {
        val f = Class.forName("com.neocraft.game.BuildConfig").getField(name)
        f.get(null) as? String ?: ""
    } catch (e: Exception) { "" }
}
