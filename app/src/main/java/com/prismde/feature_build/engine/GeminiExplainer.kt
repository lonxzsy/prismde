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
    val provider: String = "gemini_api", // "gemini_api", "antigravity", or "custom"
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val antigravityAccessToken: String = "",
    val antigravityRefreshToken: String = "",
    val customBaseUrl: String = "",
    val customApiKey: String = "",
    val customModel: String = "",
    val customModelsUrl: String = "",
    val customAuthType: String = "bearer", // "bearer", "header", "none"
    val customHeaderName: String = "Authorization"
)

class GeminiExplainer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
    private val antigravityAuthManager: AntigravityAuthManager = AntigravityAuthManager(client)
) {

    /**
     * PROMPT 1: Human-readable diagnostic analysis and explanation.
     */
    suspend fun explainDiagnostic(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        config: AiConfig
    ): Result<String> {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val prompt = if (isRu) """
            Ты эксперт по C/C++ и Android NDK. Разбери ошибку компилятора:

            Файл: ${diagnostic.filePath} (строка ${diagnostic.line}, колонка ${diagnostic.column})
            Ошибка: ${diagnostic.rawMessage}

            Исходный код:
            ```cpp
            $sourceCodeContext
            ```

            ТРЕБОВАНИЯ К ОТВЕТУ (БЕЗ ВОДЫ):
            - Никаких приветствий, вводных вежливых фраз и общих рассуждений. Сразу к делу.
            - НЕ используй никакие эмодзи в тексте ответа.
            - Ответ должен состоять ТОЛЬКО из двух кратких и конкретных пунктов:
            
            1. **Причина:** (1-2 ёмких предложения, что конкретно не так на строке ${diagnostic.line}).
            2. **Как исправить:** (конкретное указание и краткий пример исправленного кода).
        """.trimIndent() else """
            You are a C/C++ and Android NDK expert. Analyze this compiler error:

            File: ${diagnostic.filePath} (line ${diagnostic.line}, column ${diagnostic.column})
            Error: ${diagnostic.rawMessage}

            Source code:
            ```cpp
            $sourceCodeContext
            ```

            REQUIREMENTS FOR RESPONSE (NO FLUFF):
            - No greetings, polite intros, or preamble. Get straight to the point.
            - Do NOT use emojis.
            - Provide ONLY two brief and specific sections:
            
            1. **Cause:** (1-2 concise sentences explaining what is wrong on line ${diagnostic.line}).
            2. **How to fix:** (concrete action and short corrected code snippet).
        """.trimIndent()

        val rawResult = executeAiPrompt(prompt, config)
        return rawResult.map { text ->
            val modelName = config.model.trim().ifBlank {
                when (config.provider) {
                    "antigravity" -> "gemini-3.8-flash"
                    "custom" -> "Custom Model"
                    else -> "gemini-2.5-flash"
                }
            }
            val header = when (config.provider) {
                "antigravity" -> if (isRu) "> **Сервис:** Google Antigravity • **Модель:** `$modelName`\n\n" else "> **Service:** Google Antigravity • **Model:** `$modelName`\n\n"
                "custom" -> if (isRu) "> **Сервис:** Custom Endpoint • **Модель:** `$modelName`\n\n" else "> **Service:** Custom Endpoint • **Model:** `$modelName`\n\n"
                else -> if (isRu) "> **Сервис:** Google AI Studio • **Модель:** `$modelName`\n\n" else "> **Service:** Google AI Studio • **Model:** `$modelName`\n\n"
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
        val isRu = java.util.Locale.getDefault().language == "ru"
        val prompt = if (isRu) """
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
        """.trimIndent() else """
            You are an automated code fixing tool in the mobile IDE PrismDE.
            Your task is to fix a compiler error in this C/C++ file.
            
            File: ${diagnostic.filePath}
            Error line: ${diagnostic.line}, Column: ${diagnostic.column}
            Compiler message: ${diagnostic.rawMessage}
            
            Code context:
            ```cpp
            $sourceCodeContext
            ```
            
            STRICT RULES:
            1. Return ONLY the replacement code snippet fixing the error line or block.
            2. DO NOT include explanations, greetings, or text before/after the code.
            3. No unnecessary comments. Return raw code only.
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
        val isRu = java.util.Locale.getDefault().language == "ru"

        if (config.provider == "custom") {
            val customClient = CustomEndpointClient(client)
            val messages = listOf(
                CustomChatMessage(
                    role = "system",
                    content = if (isRu) "Ты профессиональный C/C++ и Android NDK ассистент."
                    else "You are a professional C/C++ and Android NDK assistant."
                ),
                CustomChatMessage(role = "user", content = prompt)
            )
            return@withContext customClient.sendChatCompletion(messages, config)
        }

        if (config.provider == "antigravity") {
            if (config.antigravityAccessToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        if (isRu) "Выбран сервис Google Antigravity, но аккаунт не подключен. Откройте Настройки -> секцию Google Antigravity и выполните вход."
                        else "Google Antigravity is selected, but account is not connected. Open Settings -> Google Antigravity and sign in."
                    )
                )
            }
            return@withContext executeAntigravityRequest(prompt, jsonBody, mediaType, config)
        }

        // Standard Gemini API Key Flow - strictly queries user-selected model
        val cleanKey = config.apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Выбран сервис Google AI Studio, но Gemini API ключ не введен. Перейдите в Настройки и укажите API ключ."
                    else "Google AI Studio is selected, but Gemini API key is missing. Go to Settings and provide an API key."
                )
            )
        }

        val targetModel = config.model.trim().ifBlank { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$cleanKey"
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
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val fullText = StringBuilder()
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val text = part.optString("text")
                                if (text.isNotBlank()) {
                                    fullText.append(text)
                                }
                            }
                            if (fullText.isNotBlank()) {
                                return@use Result.success(fullText.toString())
                            }
                        }
                        return@use Result.failure(Exception(if (isRu) "Пустой ответ от модели ($targetModel)." else "Empty response from model ($targetModel)."))
                    } else {
                        return@use Result.failure(Exception(if (isRu) "Пустой ответ от модели ($targetModel)." else "Empty response from model ($targetModel)."))
                    }
                }

                val errorDetail = try {
                    val errObj = JSONObject(responseStr).optJSONObject("error")
                    errObj?.optString("message") ?: responseStr
                } catch (_: Exception) {
                    responseStr.ifBlank { response.message }
                }

                Result.failure(Exception("Gemini API ($targetModel, HTTP ${response.code}): $errorDetail"))
            }

            return@withContext callResult
        } catch (e: Exception) {
            return@withContext Result.failure(Exception(if (isRu) "Ошибка сети при обращении к $targetModel: ${e.message}" else "Network error reaching $targetModel: ${e.message}"))
        }
    }

    private suspend fun executeAntigravityRequest(
        prompt: String,
        jsonBody: JSONObject,
        mediaType: okhttp3.MediaType,
        config: AiConfig,
        hasRetriedToken: Boolean = false
    ): Result<String> {
        val token = config.antigravityAccessToken
        val rawModel = config.model.trim().ifBlank { "gemini-3.8-flash" }
        val selectedModel = mapAntigravityRuntimeModel(rawModel)
        val isRu = java.util.Locale.getDefault().language == "ru"

        val project = antigravityAuthManager.loadCodeAssist(token)

        val cloudcodePayload = JSONObject().apply {
            put("project", project)
            put("model", selectedModel)
            put("request", JSONObject().apply {
                put("contents", jsonBody.getJSONArray("contents"))
            })
        }

        var lastErrorMsg = if (isRu) "Неизвестная ошибка Antigravity" else "Unknown Antigravity error"
        var needsTokenRefresh = false

        // Target native Antigravity SSE consumer endpoint
        val url = "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse"

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
                    lastErrorMsg = if (isRu) "Сессия истекла (HTTP ${response.code})" else "Session expired (HTTP ${response.code})"
                    return@use null
                }
                if (response.isSuccessful) {
                    val body = response.body ?: return@use null
                    val fullText = StringBuilder()
                    body.charStream().buffered().forEachLine { line ->
                        if (line.startsWith("data:")) {
                            val dataChunk = line.removePrefix("data:").trim()
                            if (dataChunk.isNotBlank() && dataChunk != "[DONE]") {
                                try {
                                    val jsonChunk = JSONObject(dataChunk)
                                    val candParent = jsonChunk.optJSONObject("response") ?: jsonChunk
                                    val candidates = candParent.optJSONArray("candidates") ?: jsonChunk.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        for (cIdx in 0 until candidates.length()) {
                                            val cand = candidates.getJSONObject(cIdx)
                                            val content = cand.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null) {
                                                for (pIdx in 0 until parts.length()) {
                                                    val p = parts.getJSONObject(pIdx)
                                                    val t = p.optString("text")
                                                    if (t.isNotBlank()) fullText.append(t)
                                                }
                                            } else {
                                                val t = cand.optString("text")
                                                if (t.isNotBlank()) fullText.append(t)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    if (fullText.isNotBlank()) {
                        return@use Result.success(fullText.toString())
                    } else {
                        lastErrorMsg = if (isRu) "Пустой ответ от Antigravity ($selectedModel)" else "Empty response from Antigravity ($selectedModel)"
                    }
                } else {
                    val err = response.body?.string() ?: ""
                    lastErrorMsg = if (response.code == 429) {
                        if (isRu) "Сервер Google временно перегружен запросами (HTTP 429). Подождите 10-15 секунд или выберите более быструю модель (например Claude Sonnet 4.6 или Gemini 2.5 Flash)."
                        else "Google servers are temporarily rate-limited (HTTP 429). Wait 10-15 seconds or switch to a faster model (e.g. Claude Sonnet 4.6 or Gemini 2.5 Flash)."
                    } else {
                        "Antigravity ($selectedModel, HTTP ${response.code}): $err"
                    }
                }
                null
            }
            if (callResult != null) return callResult
        } catch (e: Exception) {
            lastErrorMsg = if (e is java.net.SocketTimeoutException) {
                if (isRu) "Время ожидания ответа от модели $selectedModel истекло (таймаут). Попробуйте более легкую или быструю модель (например Claude Sonnet 4.6 или Gemini 2.5 Flash)."
                else "Request timed out waiting for $selectedModel. Try a faster model (e.g. Claude Sonnet 4.6 or Gemini 2.5 Flash)."
            } else {
                if (isRu) "Сетевая ошибка ($selectedModel): ${e.message}" else "Network error ($selectedModel): ${e.message}"
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

        return Result.failure(Exception(if (isRu) "Не удалось получить ответ от Google Antigravity ($selectedModel). $lastErrorMsg" else "Failed to get response from Google Antigravity ($selectedModel). $lastErrorMsg"))
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

    companion object {
        fun mapAntigravityRuntimeModel(configModel: String): String {
            val m = configModel.trim().lowercase()
            return when (m) {
                "gemini-3.8-flash" -> "gemini-3.8-flash-tiered"
                "gemini-3.7-flash" -> "gemini-3.7-flash-tiered"
                "gemini-3.6-flash" -> "gemini-3.6-flash-high"
                "gemini-3.1-pro", "gemini-3-pro" -> "gemini-pro-agent"
                "claude-sonnet-4.6", "claude-sonnet-4-20250514", "claude-sonnet-4-6" -> "claude-sonnet-4-6"
                "claude-opus-4.6", "claude-opus-4.5", "claude-opus-4-6-thinking" -> "claude-opus-4-6-thinking"
                "gpt-oss-120b", "gpt-oss-120b-medium" -> "gpt-oss-120b-medium"
                else -> when {
                    m.startsWith("claude-sonnet") -> "claude-sonnet-4-6"
                    m.startsWith("claude-opus") -> "claude-opus-4-6-thinking"
                    m.startsWith("gpt-oss") -> "gpt-oss-120b-medium"
                    m.startsWith("gemini-3.8") -> "gemini-3.8-flash-tiered"
                    m.startsWith("gemini-3.7") -> "gemini-3.7-flash-tiered"
                    m.startsWith("gemini-3.6") -> "gemini-3.6-flash-high"
                    m.startsWith("gemini-3.1") || m.startsWith("gemini-3-pro") -> "gemini-pro-agent"
                    else -> "gemini-3.8-flash-tiered"
                }
            }
        }
    }
}
