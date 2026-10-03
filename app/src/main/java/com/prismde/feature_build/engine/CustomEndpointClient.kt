package com.prismde.feature_build.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CustomChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class ChatCompletionResponse(
    val text: String,
    val reasoning: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

class CustomEndpointClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Resolves the models URL based on user input or by appending /models to baseUrl.
     */
    fun resolveModelsUrl(baseUrl: String, customModelsUrl: String = ""): String {
        val trimmedCustom = customModelsUrl.trim()
        if (trimmedCustom.isNotBlank()) {
            return trimmedCustom
        }
        val cleanBase = baseUrl.trim().trimEnd('/')
        return if (cleanBase.endsWith("/models", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase/models"
        }
    }

    /**
     * Resolves the chat completions URL.
     */
    fun resolveChatCompletionsUrl(baseUrl: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        return if (cleanBase.endsWith("/chat/completions", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase/chat/completions"
        }
    }

    /**
     * Builds request builder with the appropriate authentication headers.
     */
    private fun applyAuthHeaders(
        builder: Request.Builder,
        apiKey: String,
        authType: String,
        headerName: String
    ): Request.Builder {
        val cleanKey = apiKey.trim()
        if (cleanKey.isNotBlank()) {
            when (authType.lowercase()) {
                "bearer" -> builder.header("Authorization", "Bearer $cleanKey")
                "header" -> {
                    val hName = headerName.trim().ifBlank { "Authorization" }
                    builder.header(hName, cleanKey)
                }
                "none" -> { /* No auth header */ }
                else -> builder.header("Authorization", "Bearer $cleanKey")
            }
        }
        // OpenRouter / general metadata headers
        builder.header("HTTP-Referer", "https://prismde.app")
        builder.header("X-Title", "PrismDE")
        builder.header("Accept", "application/json")
        return builder
    }

    /**
     * Fetches models list from the endpoint.
     */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
        authType: String = "bearer",
        headerName: String = "Authorization",
        customModelsUrl: String = ""
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val cleanBase = baseUrl.trim()
        if (cleanBase.isBlank() && customModelsUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Укажите Base URL провайдера (например: https://api.openai.com/v1)"
                    else "Provide Base URL (e.g.: https://api.openai.com/v1)"
                )
            )
        }

        val url = resolveModelsUrl(cleanBase, customModelsUrl)
        val requestBuilder = Request.Builder().url(url).get()
        applyAuthHeaders(requestBuilder, apiKey, authType, headerName)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorDetail = extractErrorMessage(bodyStr)
                    return@use Result.failure(
                        IOException(
                            if (isRu) "Ошибка получения моделей (HTTP ${response.code}): $errorDetail"
                            else "Failed to fetch models (HTTP ${response.code}): $errorDetail"
                        )
                    )
                }

                val modelsList = parseModelsResponse(bodyStr)
                if (modelsList.isEmpty()) {
                    return@use Result.failure(
                        Exception(
                            if (isRu) "Список моделей пуст или не удалось распознать формат ответа."
                            else "Model list is empty or unrecognized response format."
                        )
                    )
                }

                Result.success(modelsList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses models response supporting OpenAI, Ollama, OpenRouter, and array formats.
     */
    fun parseModelsResponse(responseBody: String): List<String> {
        val result = mutableListOf<String>()
        val trimmed = responseBody.trim()

        try {
            if (trimmed.startsWith("[")) {
                // Plain JSON array
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.opt(i)
                    when (item) {
                        is String -> if (item.isNotBlank()) result.add(item)
                        is JSONObject -> {
                            val id = item.optString("id").ifBlank { item.optString("name") }
                            if (id.isNotBlank()) result.add(id)
                        }
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)

                // 1. OpenAI standard: { "data": [ { "id": "model-id" } ] }
                val dataArray = json.optJSONArray("data")
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.opt(i)
                        when (item) {
                            is JSONObject -> {
                                val id = item.optString("id").ifBlank { item.optString("name") }
                                if (id.isNotBlank()) result.add(id)
                            }
                            is String -> if (item.isNotBlank()) result.add(item)
                        }
                    }
                }

                // 2. Ollama / custom: { "models": [ { "name": "llama3:latest" } ] }
                if (result.isEmpty()) {
                    val modelsArray = json.optJSONArray("models")
                    if (modelsArray != null) {
                        for (i in 0 until modelsArray.length()) {
                            val item = modelsArray.opt(i)
                            when (item) {
                                is JSONObject -> {
                                    val name = item.optString("name").ifBlank { item.optString("model") }
                                    if (name.isNotBlank()) result.add(name)
                                }
                                is String -> if (item.isNotBlank()) result.add(item)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return result.distinct().sorted()
    }

    /**
     * Executes OpenAI-compatible chat completion and extracts reasoning and token usage.
     */
    suspend fun sendChatCompletionDetails(
        messages: List<CustomChatMessage>,
        config: AiConfig,
        temperature: Double = 0.2
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val baseUrl = config.customBaseUrl.trim()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Укажите Base URL для Custom Endpoint в Настройках."
                    else "Custom Endpoint Base URL is missing in Settings."
                )
            )
        }

        val targetModel = config.model.trim().ifBlank {
            config.customModel.trim().ifBlank { "gpt-4o" }
        }

        val url = resolveChatCompletionsUrl(baseUrl)

        val jsonBody = JSONObject().apply {
            put("model", targetModel)
            put("temperature", temperature)

            val messagesArray = JSONArray()
            for (msg in messages) {
                messagesArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", messagesArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))

        applyAuthHeaders(requestBuilder, config.customApiKey, config.customAuthType, config.customHeaderName)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorDetail = extractErrorMessage(bodyStr)
                    return@use Result.failure(
                        IOException("Custom Endpoint ($targetModel, HTTP ${response.code}): $errorDetail")
                    )
                }

                val responseJson = JSONObject(bodyStr)
                val usageObj = responseJson.optJSONObject("usage")
                val promptTokens = usageObj?.optInt("prompt_tokens") ?: 0
                val completionTokens = usageObj?.optInt("completion_tokens") ?: 0
                val totalTokens = usageObj?.optInt("total_tokens") ?: (promptTokens + completionTokens)

                val choices = responseJson.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.optJSONObject("message")
                    var content = messageObj?.optString("content") ?: ""
                    var reasoning = messageObj?.optString("reasoning_content")?.trim() ?: ""

                    // Check for embedded <think>...</think> tags (e.g. DeepSeek-R1 / Qwen)
                    val thinkPattern = java.util.regex.Pattern.compile("""<think>([\s\S]*?)</think>""", java.util.regex.Pattern.CASE_INSENSITIVE)
                    val thinkMatcher = thinkPattern.matcher(content)
                    if (thinkMatcher.find()) {
                        val foundThink = thinkMatcher.group(1)?.trim() ?: ""
                        if (foundThink.isNotBlank() && reasoning.isBlank()) {
                            reasoning = foundThink
                        }
                        content = content.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "").trim()
                    }

                    val toolCallsArray = messageObj?.optJSONArray("tool_calls")
                    if (toolCallsArray != null && toolCallsArray.length() > 0) {
                        val sb = StringBuilder()
                        if (content.isNotBlank()) sb.append(content).append("\n\n")
                        for (i in 0 until toolCallsArray.length()) {
                            val tc = toolCallsArray.optJSONObject(i)
                            val fn = tc?.optJSONObject("function")
                            val name = fn?.optString("name") ?: ""
                            val argsStr = fn?.optString("arguments") ?: "{}"
                            sb.append("<tool_call name=\"$name\">\n")
                            try {
                                val argsJson = JSONObject(argsStr)
                                for (key in argsJson.keys()) {
                                    val value = argsJson.opt(key)
                                    sb.append("  <$key>$value</$key>\n")
                                }
                            } catch (_: Exception) {
                                sb.append("  $argsStr\n")
                            }
                            sb.append("</tool_call>\n")
                        }
                        return@use Result.success(
                            ChatCompletionResponse(
                                text = sb.toString().trim(),
                                reasoning = reasoning.ifBlank { null },
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                totalTokens = totalTokens
                            )
                        )
                    }

                    if (content.isNotBlank()) {
                        return@use Result.success(
                            ChatCompletionResponse(
                                text = content,
                                reasoning = reasoning.ifBlank { null },
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                totalTokens = totalTokens
                            )
                        )
                    }

                    if (reasoning.isNotBlank()) {
                        return@use Result.success(
                            ChatCompletionResponse(
                                text = reasoning,
                                reasoning = reasoning,
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                totalTokens = totalTokens
                            )
                        )
                    }
                }

                Result.failure(
                    Exception(
                        if (isRu) "Пустой ответ от модели ($targetModel)."
                        else "Empty response from model ($targetModel)."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendChatCompletion(
        messages: List<CustomChatMessage>,
        config: AiConfig,
        temperature: Double = 0.2
    ): Result<String> {
        return sendChatCompletionDetails(messages, config, temperature).map { it.text }
    }

    private fun extractErrorMessage(bodyStr: String): String {
        return try {
            val json = JSONObject(bodyStr)
            val errObj = json.optJSONObject("error")
            if (errObj != null) {
                errObj.optString("message", bodyStr)
            } else {
                json.optString("message", bodyStr)
            }
        } catch (_: Exception) {
            bodyStr.take(300)
        }
    }
}
