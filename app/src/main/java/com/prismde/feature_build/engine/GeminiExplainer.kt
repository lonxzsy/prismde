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

class GeminiExplainer(private val client: OkHttpClient) {

    // Priority models list: modern 2.x and 1.5 variants. If a model returns 404, fallback to next.
    private val candidateModels = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-flash",
        "gemini-2.5-pro",
        "gemini-1.5-pro",
        "gemini-pro"
    )

    /**
     * PROMPT 1: Human-readable diagnostic analysis and explanation.
     * Focused entirely on teaching and explaining the issue to the developer in Russian.
     */
    suspend fun explainDiagnostic(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        apiKey: String
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

        return executeGeminiPrompt(prompt, apiKey)
    }

    /**
     * PROMPT 2: Specialized machine code generator.
     * Generates ONLY the exact replacement code slice for editor automation, with zero markdown noise or commentary.
     */
    suspend fun generateCodeFix(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        apiKey: String
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

        val rawResult = executeGeminiPrompt(prompt, apiKey)
        return rawResult.map { rawText ->
            stripMarkdownCodeBlocks(rawText)
        }
    }

    private suspend fun executeGeminiPrompt(
        prompt: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Ключ Gemini API не настроен в Настройках."))
        }

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
        var lastErrorMsg = "Не удалось связаться с Gemini API."

        for (model in candidateModels) {
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

                    // Parse Google's error response body
                    val errorDetail = try {
                        val errObj = JSONObject(responseStr).optJSONObject("error")
                        errObj?.optString("message") ?: responseStr
                    } catch (_: Exception) {
                        responseStr.ifBlank { response.message }
                    }

                    lastErrorMsg = "Gemini ($model, HTTP ${response.code}): $errorDetail"

                    // If 404 (model not found / deprecated for this tier), continue to next model
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
