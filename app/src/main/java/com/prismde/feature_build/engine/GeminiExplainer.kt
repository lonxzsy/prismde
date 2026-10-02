package com.prismde.feature_build.engine

import com.prismde.core.model.Diagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiConfig(
    val provider: String = "gemini_api", // "gemini_api" or "antigravity"
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val antigravityAccessToken: String = "",
    val antigravityRefreshToken: String = ""
)

class GeminiExplainer(
    private val client: OkHttpClient,
    private val antigravityAuthManager: AntigravityAuthManager = AntigravityAuthManager(client)
) {

    // Default fallback list for Gemini API
    private val candidateModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash",
        "gemini-2.0-flash-thinking-exp",
        "gemini-1.5-flash-latest",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-pro"
    )

    /**
     * PROMPT 1: Human-readable diagnostic analysis and explanation.
     */
    suspend fun explainDiagnostic(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        config: AiConfig
    ): Result<String> {
        val prompt = """
            Ты эксперт по разработке на C/C++ и Android NDK в мобильной IDE PrismDE.
            Помоги разработчику понять и разобрать ошибку компилятора:
            
            Файл: ${diagnostic.filePath}
            Строка: ${diagnostic.line}, Колонка: ${diagnostic.column}
            Тип: ${diagnostic.severity}
            Сообщение компилятора: ${diagnostic.rawMessage}
            
            Фрагмент исходного кода:
            ```cpp
            $sourceCodeContext
            ```
            
            Ответь структурированно, профессионально и понятно на чистом русском языке:
            1. В чем точная причина ошибки и почему компилятор на нее указывает.
            2. Рекомендации и правильный подход к решению.
        """.trimIndent()

        val rawResult = executeAiPrompt(prompt, config)
        return rawResult.map { text ->
            val header = if (config.provider == "antigravity") {
                "> 🤖 **Сервис:** Google Antigravity • Модель `${config.model}`\n\n"
            } else {
                "> 🤖 **Сервис:** Google AI Studio • Модель `${config.model}`\n\n"
            }
            header + text
        }
    }

    suspend fun explainDiagnostic(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        apiKey: String
    ): Result<String> {
        return explainDiagnostic(diagnostic, sourceCodeContext, AiConfig(provider = "gemini_api", apiKey = apiKey))
    }

    /**
     * PROMPT 2: Specialized machine code generator.
     */
    suspend fun generateCodeFix(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        config: AiConfig
    ): Result<String> {
        val prompt = """
            Ты инструмент автоматического исправления кода в мобильной IDE PrismDE.
            Твоя задача — исправить ошибку компилятора в C/C++ файле.
            
            Файл: ${diagnostic.filePath}
            Строка ошибки: ${diagnostic.line}, Колонка: ${diagnostic.column}
            Сообщение компилятора: ${diagnostic.rawMessage}
            
            Контекст кода:
            ```cpp
            $sourceCodeContext
            ```
            
            СТРОГИЕ ПРАВИЛА:
            1. Верни ТОЛЬКО исправленный фрагмент кода, заменяющий ошибочную строку или проблемный блок.
            2. НЕ ПИШИ никаких объяснений, приветствий, текста до или после кода.
            3. Если код оборачивается в блок, используй только сам код. Никаких лишних комментариев.
        """.trimIndent()

        val rawResult = executeAiPrompt(prompt, config)
        return rawResult.map { rawText ->
            stripMarkdownCodeBlocks(rawText)
        }
    }

    suspend fun generateCodeFix(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        apiKey: String
    ): Result<String> {
        return generateCodeFix(diagnostic, sourceCodeContext, AiConfig(provider = "gemini_api", apiKey = apiKey))
    }

    private suspend fun executeAiPrompt(
        prompt: String,
        config: AiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)
        }

        val mediaType = "application/json".toMediaType()

        if (config.provider == "antigravity") {
            if (config.antigravityAccessToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Выбран сервис Google Antigravity, но аккаунт не подключен. Откройте Настройки -> секцию Google Antigravity и выполните вход.")
                )
            }
            return@withContext executeAntigravityRequest(prompt, jsonBody, mediaType, config)
        }

        // Standard Gemini API Key Flow
        val cleanKey = config.apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Выбран сервис Google AI Studio, но Gemini API ключ не введен. Перейдите в Настройки и укажите API ключ.")
            )
        }

        val modelsToTry = mutableListOf<String>()
        if (config.model.isNotBlank()) {
            modelsToTry.add(config.model.trim())
        }
        for (m in candidateModels) {
            if (!modelsToTry.contains(m)) {
                modelsToTry.add(m)
            }
        }

        var lastErrorMsg = "Не удалось связаться с Gemini API."

        for (model in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            try {
                val callResult = client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val responseJson = JSONObject(responseStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.getJSONObject("content")
                            val parts = content.getJSONArray("parts")
                            val text = parts.getJSONObject(0).getString("text")
                            return@use Result.success(text)
                        } else {
                            return@use Result.failure(Exception("Пустой ответ от модели ($model)."))
                        }
                    }

                    val errorDetail = try {
                        val errObj = JSONObject(responseStr).optJSONObject("error")
                        errObj?.optString("message") ?: responseStr
                    } catch (_: Exception) {
                        responseStr.ifBlank { response.message }
                    }

                    lastErrorMsg = "Gemini ($model, HTTP ${response.code}): $errorDetail"

                    if (response.code == 404) {
                        null
                    } else {
                        Result.failure(Exception(lastErrorMsg))
                    }
                }

                if (callResult != null) {
                    return@withContext callResult
                }
            } catch (e: Exception) {
                lastErrorMsg = "Ошибка сети при обращении к $model: ${e.message}"
            }
        }

        Result.failure(Exception(lastErrorMsg))
    }

    private suspend fun executeAntigravityRequest(
        prompt: String,
        jsonBody: JSONObject,
        mediaType: okhttp3.MediaType,
        config: AiConfig,
        hasRetriedToken: Boolean = false
    ): Result<String> {
        val token = config.antigravityAccessToken
        val selectedModel = if (config.model.isNotBlank()) config.model.trim() else "gemini-3.8-flash-high"
        val project = antigravityAuthManager.loadCodeAssist(token)

        val cloudcodePayload = JSONObject().apply {
            put("project", project)
            put("model", selectedModel)
            put("request", JSONObject().apply {
                put("contents", jsonBody.getJSONArray("contents"))
            })
        }

        var lastErrorMsg = "Неизвестная ошибка Antigravity"
        var needsTokenRefresh = false

        // Target native Antigravity endpoints in order of preference
        val candidateEndpoints = listOf(
            "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse" to true,
            "https://daily-cloudcode-pa.googleapis.com/v1internal:generateContent" to false,
            "https://cloudcode-pa.googleapis.com/v1internal:generateContent" to false
        )

        for ((url, isSse) in candidateEndpoints) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Antigravity-IDE")
                .header("X-Vertex-AI-LLM-Shared-Request-Type", "CODE_COMPLETION")
                .header("goog-originating-logical-product-id", "cloudcode")
                .post(cloudcodePayload.toString().toRequestBody(mediaType))
                .build()

            try {
                val callResult = client.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) {
                        needsTokenRefresh = true
                        lastErrorMsg = "Сессия истекла (HTTP ${response.code})"
                        return@use null
                    }
                    if (response.isSuccessful) {
                        val body = response.body ?: return@use null
                        if (isSse) {
                            val fullText = StringBuilder()
                            body.charStream().buffered().forEachLine { line ->
                                if (line.startsWith("data:")) {
                                    val dataChunk = line.removePrefix("data:").trim()
                                    if (dataChunk.isNotBlank() && dataChunk != "[DONE]") {
                                        try {
                                            val jsonChunk = JSONObject(dataChunk)
                                            val candParent = jsonChunk.optJSONObject("response") ?: jsonChunk
                                            val candidates = candParent.optJSONArray("candidates") ?: jsonChunk.optJSONArray("candidates")
                                            val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                                            if (!text.isNullOrBlank()) fullText.append(text)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            if (fullText.isNotBlank()) {
                                return@use Result.success(fullText.toString())
                            }
                        } else {
                            val bodyStr = body.string()
                            val json = JSONObject(bodyStr)
                            val candParent = json.optJSONObject("response") ?: json
                            val candidates = candParent.optJSONArray("candidates") ?: json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val text = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                                    ?: candidates.getJSONObject(0).optString("text")
                                if (!text.isNullOrBlank()) return@use Result.success(text)
                            }
                            val direct = json.optString("text")
                            if (direct.isNotBlank()) return@use Result.success(direct)
                        }
                    } else {
                        val err = response.body?.string() ?: ""
                        lastErrorMsg = "Antigravity (${response.code}): $err"
                    }
                    null
                }
                if (callResult != null) return callResult
            } catch (e: Exception) {
                lastErrorMsg = "Сетевая ошибка: ${e.message}"
            }
        }

        // If 401/403 or token expired and we have refresh token, refresh once
        if (needsTokenRefresh && !hasRetriedToken && config.antigravityRefreshToken.isNotBlank()) {
            val refreshResult = antigravityAuthManager.refreshAccessToken(config.antigravityRefreshToken)
            val newToken = refreshResult.getOrNull()
            if (newToken != null && newToken.isNotBlank()) {
                return executeAntigravityRequest(prompt, jsonBody, mediaType, config.copy(antigravityAccessToken = newToken), hasRetriedToken = true)
            }
        }

        return Result.failure(Exception("Не удалось получить ответ от Google Antigravity ($selectedModel). $lastErrorMsg"))
    }

    private fun stripMarkdownCodeBlocks(rawText: String): String {
        val trimmed = rawText.trim()
        val multilineMatch = Regex("""```(?:\w+)?\s*\n([\s\S]*?)\n```""").find(trimmed)
        if (multilineMatch != null) {
            return multilineMatch.groupValues[1].trimEnd()
        }
        val singleLineMatch = Regex("""```(?:\w+)?\s*([^\n]+?)\s*```""").find(trimmed)
        if (singleLineMatch != null) {
            return singleLineMatch.groupValues[1].trim()
        }
        return trimmed
    }
}
