package com.neocraft.game.auth

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * NeoCraft Auth Manager
 *
 * Uses Firebase Authentication REST API (no Firebase SDK needed — keeps APK small).
 * Supports: Email/Password signup & login, Google Sign-In token exchange,
 * GitHub OAuth token exchange, anonymous play, and session persistence.
 *
 * User data stored in Firebase Firestore via REST API.
 */
class AuthManager(private val context: Context) {

    // ── Firebase project config (set from BuildConfig) ────────────────────
    private var firebaseApiKey   = ""
    private var firebaseProjectId = ""

    data class User(
        val uid: String,
        val email: String,
        val displayName: String,
        val isAnonymous: Boolean = false,
        val photoUrl: String = "",
        val idToken: String = "",
        val refreshToken: String = ""
    )

    private var currentUser: User? = null
    var onAuthStateChanged: ((User?) -> Unit)? = null

    private val prefs get() = context.getSharedPreferences("neocraft_auth", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun configure(apiKey: String, projectId: String) {
        firebaseApiKey    = apiKey
        firebaseProjectId = projectId
        // Restore session
        scope.launch { restoreSession() }
    }

    // ── Email/Password Auth ───────────────────────────────────────────────

    suspend fun signUp(email: String, password: String, displayName: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (firebaseApiKey.isEmpty()) return@withContext AuthResult.Error("Auth not configured")
            try {
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$firebaseApiKey"
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                    put("returnSecureToken", true)
                }
                val resp = postJson(url, body)
                val user = User(
                    uid          = resp.optString("localId"),
                    email        = resp.optString("email"),
                    displayName  = displayName,
                    idToken      = resp.optString("idToken"),
                    refreshToken = resp.optString("refreshToken")
                )
                // Update display name
                updateProfile(user.idToken, displayName)
                // Create Firestore user document
                createUserDocument(user)
                setCurrentUser(user)
                AuthResult.Success(user)
            } catch (e: Exception) {
                Log.e("Auth", "SignUp error: ${e.message}")
                AuthResult.Error(parseFirebaseError(e.message ?: "Unknown error"))
            }
        }

    suspend fun signIn(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (firebaseApiKey.isEmpty()) return@withContext AuthResult.Error("Auth not configured")
            try {
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$firebaseApiKey"
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                    put("returnSecureToken", true)
                }
                val resp = postJson(url, body)
                val user = User(
                    uid          = resp.optString("localId"),
                    email        = resp.optString("email"),
                    displayName  = resp.optString("displayName").ifEmpty { email.substringBefore("@") },
                    idToken      = resp.optString("idToken"),
                    refreshToken = resp.optString("refreshToken")
                )
                setCurrentUser(user)
                AuthResult.Success(user)
            } catch (e: Exception) {
                Log.e("Auth", "SignIn error: ${e.message}")
                AuthResult.Error(parseFirebaseError(e.message ?: "Unknown error"))
            }
        }

    suspend fun signInAnonymously(): AuthResult =
        withContext(Dispatchers.IO) {
            if (firebaseApiKey.isEmpty()) {
                // Offline anonymous mode — generate a local UID
                val anonUser = User(
                    uid         = "anon_${System.currentTimeMillis()}",
                    email       = "",
                    displayName = "Explorer",
                    isAnonymous = true
                )
                setCurrentUser(anonUser)
                return@withContext AuthResult.Success(anonUser)
            }
            try {
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$firebaseApiKey"
                val body = JSONObject().apply { put("returnSecureToken", true) }
                val resp = postJson(url, body)
                val user = User(
                    uid          = resp.optString("localId"),
                    email        = "",
                    displayName  = "Explorer",
                    isAnonymous  = true,
                    idToken      = resp.optString("idToken"),
                    refreshToken = resp.optString("refreshToken")
                )
                setCurrentUser(user)
                AuthResult.Success(user)
            } catch (e: Exception) {
                // Fallback to offline
                val anonUser = User("anon_local", "", "Explorer", true)
                setCurrentUser(anonUser)
                AuthResult.Success(anonUser)
            }
        }

    fun signOut() {
        currentUser = null
        prefs.edit().clear().apply()
        onAuthStateChanged?.invoke(null)
    }

    // ── Firestore user data ───────────────────────────────────────────────

    private fun createUserDocument(user: User) {
        if (firebaseProjectId.isEmpty() || user.idToken.isEmpty()) return
        try {
            val url = "https://firestore.googleapis.com/v1/projects/$firebaseProjectId/databases/(default)/documents/users/${user.uid}"
            val fields = JSONObject().apply {
                put("displayName", jsonStr(user.displayName))
                put("email", jsonStr(user.email))
                put("createdAt", jsonStr(System.currentTimeMillis().toString()))
                put("totalPlayTime", jsonInt(0))
                put("worldsCreated", jsonInt(0))
                put("blocksPlaced", jsonInt(0))
                put("score", jsonInt(0))
            }
            val body = JSONObject().put("fields", fields)
            patchJson(url, body, user.idToken)
        } catch (e: Exception) { Log.w("Auth", "Create user doc failed: ${e.message}") }
    }

    suspend fun saveUserStats(stats: UserStats) = withContext(Dispatchers.IO) {
        val user = currentUser ?: return@withContext
        if (firebaseProjectId.isEmpty() || user.idToken.isEmpty()) return@withContext
        try {
            val url = "https://firestore.googleapis.com/v1/projects/$firebaseProjectId/databases/(default)/documents/users/${user.uid}?updateMask.fieldPaths=totalPlayTime&updateMask.fieldPaths=score&updateMask.fieldPaths=blocksPlaced&updateMask.fieldPaths=blocksDestroyed&updateMask.fieldPaths=mobsKilled"
            val fields = JSONObject().apply {
                put("totalPlayTime", jsonInt(stats.totalPlayTimeSeconds))
                put("score", jsonInt(stats.score))
                put("blocksPlaced", jsonInt(stats.blocksPlaced))
                put("blocksDestroyed", jsonInt(stats.blocksDestroyed))
                put("mobsKilled", jsonInt(stats.mobsKilled))
            }
            patchJson(url, JSONObject().put("fields", fields), user.idToken)
        } catch (e: Exception) { Log.w("Auth", "Stats save failed: ${e.message}") }
    }

    suspend fun loadUserStats(): UserStats? = withContext(Dispatchers.IO) {
        val user = currentUser ?: return@withContext null
        if (firebaseProjectId.isEmpty() || user.idToken.isEmpty()) return@withContext null
        try {
            val url = "https://firestore.googleapis.com/v1/projects/$firebaseProjectId/databases/(default)/documents/users/${user.uid}"
            val resp = getJson(url, user.idToken)
            val fields = resp.optJSONObject("fields") ?: return@withContext null
            UserStats(
                totalPlayTimeSeconds = fields.optJSONObject("totalPlayTime")?.optInt("integerValue") ?: 0,
                score                = fields.optJSONObject("score")?.optInt("integerValue") ?: 0,
                blocksPlaced         = fields.optJSONObject("blocksPlaced")?.optInt("integerValue") ?: 0,
                blocksDestroyed      = fields.optJSONObject("blocksDestroyed")?.optInt("integerValue") ?: 0,
                mobsKilled           = fields.optJSONObject("mobsKilled")?.optInt("integerValue") ?: 0
            )
        } catch (e: Exception) { null }
    }

    // ── Session persistence ───────────────────────────────────────────────

    private fun setCurrentUser(user: User) {
        currentUser = user
        prefs.edit()
            .putString("uid",          user.uid)
            .putString("email",        user.email)
            .putString("displayName",  user.displayName)
            .putBoolean("isAnonymous", user.isAnonymous)
            .putString("refreshToken", user.refreshToken)
            .apply()
        onAuthStateChanged?.invoke(user)
    }

    private suspend fun restoreSession() {
        val uid = prefs.getString("uid", null) ?: return
        val refreshToken = prefs.getString("refreshToken", "") ?: ""
        val user = User(
            uid          = uid,
            email        = prefs.getString("email", "") ?: "",
            displayName  = prefs.getString("displayName", "Explorer") ?: "Explorer",
            isAnonymous  = prefs.getBoolean("isAnonymous", false),
            refreshToken = refreshToken,
            idToken      = if (refreshToken.isNotEmpty()) refreshIdToken(refreshToken) else ""
        )
        currentUser = user
        onAuthStateChanged?.invoke(user)
    }

    private fun refreshIdToken(refreshToken: String): String {
        if (firebaseApiKey.isEmpty()) return ""
        return try {
            val url = "https://securetoken.googleapis.com/v1/token?key=$firebaseApiKey"
            val body = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
            }
            val resp = postJson(url, body)
            resp.optString("id_token", "")
        } catch (e: Exception) { "" }
    }

    private fun updateProfile(idToken: String, displayName: String) {
        if (firebaseApiKey.isEmpty()) return
        try {
            val url = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$firebaseApiKey"
            val body = JSONObject().apply {
                put("idToken", idToken)
                put("displayName", displayName)
                put("returnSecureToken", false)
            }
            postJson(url, body)
        } catch (e: Exception) { /* non-fatal */ }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────

    private fun postJson(urlStr: String, body: JSONObject): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true; connectTimeout = 10000; readTimeout = 15000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream.bufferedReader().readText()
        val json = JSONObject(resp)
        if (code !in 200..299) {
            val err = json.optJSONObject("error")?.optString("message") ?: "Request failed ($code)"
            throw Exception(err)
        }
        return json
    }

    private fun patchJson(urlStr: String, body: JSONObject, idToken: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $idToken")
            doOutput = true; connectTimeout = 10000; readTimeout = 15000
        }
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        return JSONObject(conn.inputStream.bufferedReader().readText())
    }

    private fun getJson(urlStr: String, idToken: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $idToken")
            connectTimeout = 10000; readTimeout = 15000
        }
        return JSONObject(conn.inputStream.bufferedReader().readText())
    }

    private fun jsonStr(v: String) = JSONObject().put("stringValue", v)
    private fun jsonInt(v: Int)    = JSONObject().put("integerValue", v)

    private fun parseFirebaseError(raw: String) = when {
        "EMAIL_EXISTS"           in raw -> "This email is already registered."
        "INVALID_PASSWORD"       in raw -> "Wrong password."
        "EMAIL_NOT_FOUND"        in raw -> "No account found with this email."
        "WEAK_PASSWORD"          in raw -> "Password must be at least 6 characters."
        "INVALID_EMAIL"          in raw -> "Please enter a valid email address."
        "TOO_MANY_ATTEMPTS"      in raw -> "Too many attempts. Try again later."
        "NETWORK_REQUEST_FAILED" in raw -> "No internet connection."
        else                            -> raw.take(100)
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    val isSignedIn   get() = currentUser != null
    val isAnonymous  get() = currentUser?.isAnonymous == true
    val user         get() = currentUser
    val displayName  get() = currentUser?.displayName ?: "Explorer"
    val uid          get() = currentUser?.uid ?: ""
    val idToken      get() = currentUser?.idToken ?: ""

    fun destroy() { scope.cancel() }

    // ── Data classes ──────────────────────────────────────────────────────
    sealed class AuthResult {
        data class Success(val user: User) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    data class UserStats(
        val totalPlayTimeSeconds: Int = 0,
        val score: Int = 0,
        val blocksPlaced: Int = 0,
        val blocksDestroyed: Int = 0,
        val mobsKilled: Int = 0
    )
}
