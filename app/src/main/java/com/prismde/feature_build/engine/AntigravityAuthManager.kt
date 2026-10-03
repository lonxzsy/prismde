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
    val resetTime: String? = null,
    val description: String? = null
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

data class QuotaBucket(
    val bucketId: String,
    val displayName: String,
    val window: String,
    val resetTime: String?,
    val description: String?,
    val remainingFraction: Float
) {
    val remainingPercent: Int
        get() = (remainingFraction * 100).toInt().coerceIn(0, 100)
}

data class QuotaGroup(
    val groupName: String,
    val description: String?,
    val buckets: List<QuotaBucket>
)

data class AntigravityQuotaInfo(
    val models: List<AntigravityModel>,
    val averageQuotaFraction: Float,
    val resetTime: String?,
    val groups: List<QuotaGroup> = emptyList(),
    val rawSummaryJson: String? = null
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

        val DEFAULT_ANTIGRAVITY_MODELS: List<AntigravityModel>
            get() {
                val isRu = java.util.Locale.getDefault().language == "ru"
                return listOf(
                    // Screenshot 1: Gemini 3 series
                    AntigravityModel(
                        id = "gemini-3.8-flash",
                        displayName = "Gemini 3.8 Flash",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Базовая быстрая модель по умолчанию; обслуживает фоновые субагенты." else "Default fast model; powers background subagents."
                    ),
                    AntigravityModel(
                        id = "gemini-3.7-flash",
                        displayName = "Gemini 3.7 Flash",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Сбалансированный агент для быстрых правок и рефакторинга." else "Balanced agent for quick edits and refactoring."
                    ),
                    AntigravityModel(
                        id = "gemini-3.6-flash",
                        displayName = "Gemini 3.6 Flash",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Предыдущая ревизия линейки Flash." else "Previous revision of the Flash series."
                    ),
                    AntigravityModel(
                        id = "gemini-3.1-pro",
                        displayName = "Gemini 3.1 Pro",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Флагманский агент для глубокого планирования архитектуры ( /plan )." else "Flagship agent for deep architecture planning ( /plan )."
                    ),

                    // Screenshot 2: Claude & GPT
                    AntigravityModel(
                        id = "claude-sonnet-4.6",
                        displayName = "Claude Sonnet 4.6 (Thinking)",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Написание и пошаговая реализация кода." else "Code generation and step-by-step implementation."
                    ),
                    AntigravityModel(
                        id = "claude-opus-4.6",
                        displayName = "Claude Opus 4.6 (Thinking)",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Архитектурный аудит и валидация логики сложных систем." else "Architectural audit and logic validation of complex systems."
                    ),
                    AntigravityModel(
                        id = "gpt-oss-120b",
                        displayName = "GPT-OSS 120B",
                        remainingFraction = 1.0f,
                        description = if (isRu) "Локально-ориентированная открытая модель рассуждений." else "Locally-oriented open reasoning model."
                    )
                )
            }

        val DEFAULT_GEMINI_API_MODELS: List<Pair<String, String>>
            get() {
                val isRu = java.util.Locale.getDefault().language == "ru"
                return listOf(
                    "gemini-2.5-flash" to if (isRu) "Gemini 2.5 Flash (Рекомендуемая, быстрая)" else "Gemini 2.5 Flash (Recommended, fast)",
                    "gemini-2.5-pro" to if (isRu) "Gemini 2.5 Pro (Глубокий анализ кода)" else "Gemini 2.5 Pro (Deep reasoning)",
                    "gemini-2.0-flash" to if (isRu) "Gemini 2.0 Flash (Высокая скорость)" else "Gemini 2.0 Flash (High speed)",
                    "gemini-2.0-flash-lite" to if (isRu) "Gemini 2.0 Flash Lite (Легковесная)" else "Gemini 2.0 Flash Lite (Lightweight)",
                    "gemini-1.5-pro" to if (isRu) "Gemini 1.5 Pro (Большой контекст)" else "Gemini 1.5 Pro (Large context)",
                    "gemini-1.5-flash" to if (isRu) "Gemini 1.5 Flash (Базовая легковесная)" else "Gemini 1.5 Flash (Basic lightweight)"
                )
            }

        fun isAllowedAntigravityModel(key: String, displayName: String): Boolean {
            val k = key.lowercase()
            val d = displayName.lowercase()
            if (k.startsWith("tab_") || k.startsWith("chat_") || k.endsWith("-tiered") || k.contains("-image") || k.contains("preview-") || k.contains("trawler")) {
                return false
            }
            // Explicitly exclude older Gemini 2.x and 1.x models
            if (k.startsWith("gemini-2") || k.startsWith("gemini-1") || d.contains("gemini 2") || d.contains("gemini 1")) {
                return false
            }
            // Claude & GPT
            if (k.startsWith("claude-") || k.startsWith("gpt-") || d.contains("claude") || d.contains("gpt")) {
                return true
            }
            // Gemini 3 series with all reflections
            if (k.startsWith("gemini-3") || k == "gemini-pro-agent" || d.contains("gemini 3")) {
                return true
            }
            return false
        }

        fun formatResetTime(resetTimeIso: String?): String {
            if (resetTimeIso.isNullOrBlank()) return ""
            return try {
                val instant = java.time.Instant.parse(resetTimeIso)
                val now = java.time.Instant.now()
                val duration = java.time.Duration.between(now, instant)
                val totalSeconds = duration.seconds

                val zone = java.time.ZoneId.systemDefault()
                val zonedDateTime = instant.atZone(zone)
                val isRu = java.util.Locale.getDefault().language == "ru"
                val locale = if (isRu) java.util.Locale("ru") else java.util.Locale.US
                val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", locale)

                val timeStr = zonedDateTime.format(timeFormatter)
                val dateTimeStr = zonedDateTime.format(dateFormatter)

                if (totalSeconds <= 0) {
                    if (isRu) "сейчас (в $timeStr)" else "now (at $timeStr)"
                } else {
                    val days = duration.toDays()
                    val hours = (duration.toHours() % 24)
                    val mins = (duration.toMinutes() % 60).coerceAtLeast(1)

                    val relative = when {
                        days > 0 -> if (isRu) "через $days дн $hours ч" else "in ${days}d ${hours}h"
                        hours > 0 -> if (isRu) "через $hours ч $mins мин" else "in ${hours}h ${mins}m"
                        else -> if (isRu) "через $mins мин" else "in ${mins}m"
                    }
                    if (days > 0) {
                        "$dateTimeStr ($relative)"
                    } else {
                        if (isRu) "в $timeStr ($relative)" else "at $timeStr ($relative)"
                    }
                }
            } catch (_: Exception) {
                resetTimeIso
            }
        }

        fun parseQuotaSummaryJson(jsonStr: String?): List<QuotaGroup> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val json = JSONObject(jsonStr)
                val groupsArray = json.optJSONArray("groups") ?: return emptyList()
                val list = mutableListOf<QuotaGroup>()
                for (i in 0 until groupsArray.length()) {
                    val gObj = groupsArray.getJSONObject(i)
                    val gName = gObj.optString("displayName")
                    val gDesc = gObj.optString("description")
                    val bucketsArray = gObj.optJSONArray("buckets")
                    val buckets = mutableListOf<QuotaBucket>()
                    if (bucketsArray != null) {
                        for (j in 0 until bucketsArray.length()) {
                            val bObj = bucketsArray.getJSONObject(j)
                            buckets.add(
                                QuotaBucket(
                                    bucketId = bObj.optString("bucketId"),
                                    displayName = bObj.optString("displayName"),
                                    window = bObj.optString("window"),
                                    resetTime = bObj.optString("resetTime").takeIf { it.isNotBlank() },
                                    description = bObj.optString("description"),
                                    remainingFraction = bObj.optDouble("remainingFraction", 1.0).toFloat()
                                )
                            )
                        }
                    }
                    list.add(QuotaGroup(gName, gDesc, buckets))
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }
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
        val isRu = java.util.Locale.getDefault().language == "ru"
        if (cleanCode.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException(if (isRu) "Код авторизации пуст" else "Authorization code is empty"))
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
                    Result.failure(Exception(if (isRu) "Ошибка OAuth token ($errorMsg)" else "OAuth token error ($errorMsg)"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(if (isRu) "Сетевая ошибка при обмене кода: ${e.message}" else "Network error during code exchange: ${e.message}"))
        }
    }

    /**
     * Refreshes access token using refresh_token.
     */
    suspend fun refreshAccessToken(refreshToken: String): Result<String> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        if (refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException(if (isRu) "Отсутствует refresh_token" else "Missing refresh_token"))
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
                    Result.failure(Exception(if (isRu) "Не удалось обновить токен: $responseStr" else "Failed to refresh token: $responseStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(if (isRu) "Ошибка сети при обновлении токена: ${e.message}" else "Network error while refreshing token: ${e.message}"))
        }
    }

    /**
     * Fetches user email and profile info from Google OAuth API.
     */
    suspend fun fetchUserInfo(accessToken: String): Result<AntigravityUserInfo> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
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
                    Result.failure(Exception(if (isRu) "Не удалось получить профиль: $responseStr" else "Failed to fetch user profile: $responseStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception(if (isRu) "Ошибка сети при запросе профиля: ${e.message}" else "Network error while fetching profile: ${e.message}"))
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
        val modelsList = mutableListOf<AntigravityModel>()
        var summaryJsonString: String? = null
        val quotaGroups = mutableListOf<QuotaGroup>()
        var primaryResetTime: String? = null
        var primaryQuotaFraction = 1.0f

        // 1. Fetch available models from daily-cloudcode-pa
        try {
            val modelsReq = Request.Builder()
                .url("https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Antigravity-IDE")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(modelsReq).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    val modelsObj = json.optJSONObject("models")
                    if (modelsObj != null) {
                        val keys = modelsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val mObj = modelsObj.getJSONObject(key)
                            val displayName = mObj.optString("displayName", "").ifBlank { key }

                            if (!isAllowedAntigravityModel(key, displayName)) {
                                continue
                            }

                            val quotaObj = mObj.optJSONObject("quotaInfo")
                            val remaining = quotaObj?.optDouble("remainingFraction", 1.0)?.toFloat()
                            val reset = quotaObj?.optString("resetTime")

                            modelsList.add(AntigravityModel(key, displayName, remaining, reset))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fetch User Quota Summary (groups: Gemini 5h & weekly, Claude/GPT 5h & weekly)
        try {
            val quotaReq = Request.Builder()
                .url("https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Antigravity-IDE")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(quotaReq).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    if (bodyStr.isNotBlank()) {
                        summaryJsonString = bodyStr
                        quotaGroups.addAll(parseQuotaSummaryJson(bodyStr))
                    }
                }
            }
        } catch (_: Exception) {}

        // Determine primary quota fraction and reset time from Gemini 5h limit or first bucket
        val geminiGroup = quotaGroups.firstOrNull { it.groupName.contains("Gemini", ignoreCase = true) }
        val gemini5h = geminiGroup?.buckets?.firstOrNull { it.window == "5h" } ?: geminiGroup?.buckets?.firstOrNull()

        val claudeGroup = quotaGroups.firstOrNull { it.groupName.contains("Claude", ignoreCase = true) || it.groupName.contains("GPT", ignoreCase = true) }
        val claude5h = claudeGroup?.buckets?.firstOrNull { it.window == "5h" } ?: claudeGroup?.buckets?.firstOrNull()

        if (gemini5h != null) {
            primaryQuotaFraction = gemini5h.remainingFraction
            primaryResetTime = gemini5h.resetTime
        } else if (quotaGroups.isNotEmpty()) {
            val firstBucket = quotaGroups.first().buckets.firstOrNull()
            if (firstBucket != null) {
                primaryQuotaFraction = firstBucket.remainingFraction
                primaryResetTime = firstBucket.resetTime
            }
        }

        // Return the exact 7 Antigravity models from screenshots with assigned quota & reset times
        val finalModels = DEFAULT_ANTIGRAVITY_MODELS.map { model ->
            val isClaudeOrGpt = model.id.startsWith("claude") || model.id.startsWith("gpt")
            val targetBucket = if (isClaudeOrGpt) (claude5h ?: gemini5h) else gemini5h
            if (targetBucket != null) {
                model.copy(
                    remainingFraction = targetBucket.remainingFraction,
                    resetTime = targetBucket.resetTime
                )
            } else {
                model
            }
        }

        Result.success(
            AntigravityQuotaInfo(
                models = finalModels,
                averageQuotaFraction = primaryQuotaFraction,
                resetTime = primaryResetTime,
                groups = quotaGroups,
                rawSummaryJson = summaryJsonString
            )
        )
    }
}
