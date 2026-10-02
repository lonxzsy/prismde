package com.prismde.feature_build.engine

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

data class AntigravityModel(
    val id: String,
    val displayName: String,
    val remainingFraction: Float? = null,
    val resetTime: String? = null
)

data class AntigravityTokenInfo(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long
)

data class AntigravityUserInfo(
    val email: String,
    val name: String?,
    val picture: String?
)

data class AntigravityQuotaInfo(
    val models: List<AntigravityModel>,
    val averageQuotaFraction: Float,
    val resetTime: String?
)

class AntigravityAuthManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private fun xorDecode(bytes: IntArray, key: Int = 0x5A): String {
            val chars = CharArray(bytes.size) { (bytes[it] xor key).toChar() }
            return String(chars)
        }

        val CLIENT_ID: String by lazy {
            xorDecode(intArrayOf(
                107, 106, 109, 107, 106, 106, 108, 106, 108, 106, 111, 99, 107, 119, 46, 55, 50, 41, 41, 51,
                52, 104, 50, 104, 107, 54, 57, 40, 63, 104, 105, 111, 44, 46, 53, 54, 53, 48, 50, 110,
                61, 110, 106, 105, 63, 42, 116, 59, 42, 42, 41, 116, 61, 53, 53, 61, 54, 63, 47, 41,
                63, 40, 57, 53, 52, 46, 63, 52, 46, 116, 57, 53, 55
            ))
        }

        val CLIENT_SECRET: String by lazy {
            xorDecode(intArrayOf(
                29, 21, 25, 9, 10, 2, 119, 17, 111, 98, 28, 13, 8, 110, 98, 108, 22, 62, 22, 16,
                107, 55, 22, 24, 98, 41, 2, 25, 110, 32, 108, 43, 30, 27, 60
            ))
        }

        const val REDIRECT_URI = "http://localhost:51121/oauth-callback"

        const val SCOPES = "https://www.googleapis.com/auth/cloud-platform " +
                "https://www.googleapis.com/auth/userinfo.email " +
                "https://www.googleapis.com/auth/userinfo.profile " +
                "https://www.googleapis.com/auth/cclog " +
                "https://www.googleapis.com/auth/experimentsandconfigs"

        val DEFAULT_ANTIGRAVITY_MODELS = listOf(
            AntigravityModel("claude-sonnet-4-6", "Claude Sonnet 4.6 (Мгновенная, Thinking)", 1.0f),
            AntigravityModel("gemini-2.5-flash", "Gemini 2.5 Flash (Мгновенная)", 1.0f),
            AntigravityModel("gemini-3.6-flash-high", "Gemini 3.6 Flash (High, глубокий анализ)", 1.0f),
            AntigravityModel("gemini-3.6-flash-medium", "Gemini 3.6 Flash (Medium)", 1.0f),
            AntigravityModel("gemini-3.6-flash-low", "Gemini 3.6 Flash (Low)", 1.0f),
            AntigravityModel("gemini-3-flash", "Gemini 3 Flash", 1.0f),
            AntigravityModel("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", 1.0f),
            AntigravityModel("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", 1.0f),
            AntigravityModel("gemini-3.1-pro-high", "Gemini 3.1 Pro (High)", 1.0f),
            AntigravityModel("gemini-3.1-pro-low", "Gemini 3.1 Pro (Low)", 1.0f),
            AntigravityModel("gemini-2.5-pro", "Gemini 2.5 Pro", 1.0f)
        )

        val DEFAULT_GEMINI_API_MODELS = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Рекомендуемая, быстрая)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro (Глубокий анализ кода)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Высокая скорость)",
            "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite (Легковесная)",
            "gemini-1.5-pro" to "Gemini 1.5 Pro (Большой контекст)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Базовая легковесная)"
        )
    }

    /**
     * Builds the Google OAuth URL to open in browser.
     */
    fun buildAuthorizationUrl(): String {
        val base = "https://accounts.google.com/o/oauth2/v2/auth"
        val params = listOf(
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPES,
            "access_type" to "offline",
            "prompt" to "consent"
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        return "$base?$params"
    }

    /**
     * Extracts authorization code from raw string or full redirect URL pasted by the user.
     */
    fun extractAuthCode(input: String): String {
        val trimmed = input.trim()
        if (trimmed.contains("code=")) {
            try {
                val parsed = Uri.parse(trimmed)
                val code = parsed.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    return code
                }
            } catch (_: Exception) {}

            val regex = Regex("""[?&]code=([^&]+)""")
            val match = regex.find(trimmed)
            if (match != null) {
                return URLDecoder.decode(match.groupValues[1], "UTF-8")
            }
        }
        return trimmed
    }

    /**
     * Exchanges OAuth authorization code for Access and Refresh tokens.
     */
    suspend fun exchangeCodeForTokens(rawCodeInput: String): Result<AntigravityTokenInfo> = withContext(Dispatchers.IO) {
        val cleanCode = extractAuthCode(rawCodeInput)
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Код авторизации пуст"))
        }

        val body = FormBody.Builder()
            .add("code", cleanCode)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("redirect_uri", REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseStr)
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                    val expiresIn = json.optLong("expires_in", 3600)
                    Result.success(AntigravityTokenInfo(accessToken, refreshToken, expiresIn))
                } else {
                    val errorMsg = try {
                        JSONObject(responseStr).optString("error_description", responseStr)
                    } catch (_: Exception) { responseStr }
                    Result.failure(Exception("Ошибка OAuth token ($errorMsg)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Сетевая ошибка при обмене кода: ${e.message}"))
        }
    }

    /**
     * Refreshes access token using refresh_token.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<String> = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Отсутствует refresh_token"))
        }

        val body = FormBody.Builder()
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseStr)
                    val newAccessToken = json.getString("access_token")
                    Result.success(newAccessToken)
                } else {
                    Result.failure(Exception("Не удалось обновить токен: $responseStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети при обновлении токена: ${e.message}"))
        }
    }

    /**
     * Fetches user email and profile info from Google OAuth API.
     */
    suspend fun fetchUserInfo(accessToken: String): Result<AntigravityUserInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v2/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseStr)
                    val email = json.getString("email")
                    val name = json.optString("name").takeIf { it.isNotBlank() }
                    val picture = json.optString("picture").takeIf { it.isNotBlank() }
                    Result.success(AntigravityUserInfo(email, name, picture))
                } else {
                    Result.failure(Exception("Не удалось получить профиль: $responseStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка сети при запросе профиля: ${e.message}"))
        }
    }

    private var cachedProjectId: String? = null

    /**
     * Resolves the Antigravity user companion project ID.
     */
    suspend fun loadCodeAssist(accessToken: String): String = withContext(Dispatchers.IO) {
        val cached = cachedProjectId
        if (!cached.isNullOrBlank()) return@withContext cached

        val urls = listOf(
            "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist",
            "https://daily-cloudcode-pa.googleapis.com/v1internal:loadCodeAssist"
        )
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Antigravity-IDE")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val found = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        json.optString("cloudaicompanionProject").takeIf { it.isNotBlank() }
                            ?: json.optString("cloudaicompanion_project").takeIf { it.isNotBlank() }
                    } else null
                }
                if (!found.isNullOrBlank()) {
                    cachedProjectId = found
                    return@withContext found
                }
            } catch (_: Exception) {}
        }
        val fallback = "aicode-consumers"
        cachedProjectId = fallback
        fallback
    }

    /**
     * Fetches models list and quota information from Antigravity/Code Assist API.
     */
    suspend fun fetchModelsAndQuota(accessToken: String): Result<AntigravityQuotaInfo> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
            "https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels"
        )
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Antigravity-IDE")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val result = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseStr = response.body?.string() ?: ""
                        val json = JSONObject(responseStr)
                        val modelsObj = json.optJSONObject("models")
                        if (modelsObj != null) {
                            val list = mutableListOf<AntigravityModel>()
                            var totalFraction = 0f
                            var countWithFraction = 0
                            var latestReset: String? = null

                            val keys = modelsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                if (key.startsWith("tab_") || key.startsWith("chat_") || key.endsWith("-tiered") || key.endsWith("-image")) {
                                    continue
                                }
                                val mObj = modelsObj.getJSONObject(key)
                                val displayName = mObj.optString("displayName", "").ifBlank { key }
                                val quotaObj = mObj.optJSONObject("quotaInfo")
                                val remaining = quotaObj?.optDouble("remainingFraction", 1.0)?.toFloat()
                                val reset = quotaObj?.optString("resetTime")

                                if (remaining != null) {
                                    totalFraction += remaining
                                    countWithFraction++
                                }
                                if (!reset.isNullOrBlank()) {
                                    latestReset = reset
                                }

                                list.add(AntigravityModel(key, displayName, remaining, reset))
                            }

                            val avg = if (countWithFraction > 0) totalFraction / countWithFraction else 1.0f
                            Result.success(AntigravityQuotaInfo(list, avg, latestReset))
                        } else null
                    } else null
                }
                if (result != null) return@withContext result
            } catch (_: Exception) {}
        }
        Result.success(AntigravityQuotaInfo(DEFAULT_ANTIGRAVITY_MODELS, 1.0f, null))
    }
}
