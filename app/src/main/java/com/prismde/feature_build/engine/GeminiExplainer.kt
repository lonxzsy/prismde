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
        config: AiConfig
    ): Result<String> {
        var token = config.antigravityAccessToken
        val selectedModel = if (config.model.isNotBlank()) config.model.trim() else "gemini-3.8-flash-high"
        var lastErrorMsg = "Неизвестная ошибка Antigravity"

        // 1. Try via Cloudcode Antigravity internal endpoint with GenerateContentRequest schema
        val cloudcodePayload = JSONObject().apply {
            put("model", selectedModel)
            put("project", "")
            put("request", JSONObject().apply {
                put("contents", jsonBody.getJSONArray("contents"))
            })
        }

        val requestCloudcode = Request.Builder()
            .url("https://cloudcode-pa.googleapis.com/v1internal:generateContent")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Antigravity-IDE")
            .header("X-Vertex-AI-LLM-Shared-Request-Type", "CODE_COMPLETION")
            .header("goog-originating-logical-product-id", "cloudcode")
            .post(cloudcodePayload.toString().toRequestBody(mediaType))
            .build()

        try {
            val res = client.newCall(requestCloudcode).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val candParent = json.optJSONObject("response") ?: json
                    val candidates = candParent.optJSONArray("candidates") ?: json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val first = candidates.getJSONObject(0)
                        val text = first.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                            ?: first.optString("text")
                        if (!text.isNullOrBlank()) {
                            return@use Result.success(text)
                        }
                    }
                    val directText = json.optString("text")
                    if (directText.isNotBlank()) {
                        return@use Result.success(directText)
                    }
                }
                lastErrorMsg = "Cloudcode (HTTP ${response.code}): $body"
                null
            }
            if (res != null) return res
        } catch (e: Exception) {
            lastErrorMsg = "Cloudcode network: ${e.message}"
        }

        // 2. Try Cloudcode models endpoint
        val requestModelCloudcode = Request.Builder()
            .url("https://cloudcode-pa.googleapis.com/v1beta/models/$selectedModel:generateContent")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Antigravity-IDE")
            .post(jsonBody.toString().toRequestBody(mediaType))
            .build()

        try {
            val res = client.newCall(requestModelCloudcode).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val candParent = json.optJSONObject("response") ?: json
                    val candidates = candParent.optJSONArray("candidates") ?: json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                            ?: candidates.getJSONObject(0).optString("text")
                        if (text.isNotBlank()) return@use Result.success(text)
                    }
                }
                lastErrorMsg = "Cloudcode models (HTTP ${response.code}): $body"
                null
            }
            if (res != null) return res
        } catch (e: Exception) {
            lastErrorMsg = "Cloudcode models network: ${e.message}"
        }

        // 3. Fallback to Google Generative Language using OAuth Bearer token
        val genericModel = selectedModel.removePrefix("gemini-").let { "gemini-$it" }
        val models = listOf(selectedModel, genericModel, "gemini-2.5-flash", "gemini-2.0-flash")

        for (m in models) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
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
                        }
                    }
                    lastErrorMsg = "OAuth Google GenAI ($m, HTTP ${response.code}): $responseStr"
                    null
                }
                if (callResult != null) return callResult
            } catch (e: Exception) {
                lastErrorMsg = "OAuth GenAI ($m) network: ${e.message}"
            }
        }

        // 4. Token refresh attempt if refresh token is present
        if (config.antigravityRefreshToken.isNotBlank()) {
            val refreshResult = antigravityAuthManager.refreshAccessToken(config.antigravityRefreshToken)
            val newToken = refreshResult.getOrNull()
            if (newToken != null && newToken != token) {
                return executeAntigravityRequest(prompt, jsonBody, mediaType, config.copy(antigravityAccessToken = newToken))
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
