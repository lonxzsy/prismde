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

    suspend fun explainDiagnostic(
        diagnostic: Diagnostic,
        sourceCodeContext: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is not configured in Settings."))
        }

        val prompt = """
            Ты эксперт по разработке на C/C++ и Android NDK.
            Помоги разработчику исправить ошибку компилятора:
            
            Файл: ${diagnostic.filePath}
            Строка: ${diagnostic.line}, Колонка: ${diagnostic.column}
            Тип: ${diagnostic.severity}
            Сообщение компилятора: ${diagnostic.rawMessage}
            
            Фрагмент исходного кода:
            ```cpp
            $sourceCodeContext
            ```
            
            Ответь кратко, на чистом русском языке:
            1. В чем точная причина ошибки.
            2. Конкретный код исправления (без лишней воды).
        """.trimIndent()

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

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("Gemini API returned code ${response.code}: ${response.message}")
                    )
                }

                val responseStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")
                    Result.success(text)
                } else {
                    Result.failure(Exception("Пустой ответ от модели."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
