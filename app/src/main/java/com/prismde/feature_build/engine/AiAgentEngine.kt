package com.prismde.feature_build.engine

import com.prismde.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class AiAgentAction {
    data class Thinking(val message: String) : AiAgentAction()
    data class ReadFile(val relativePath: String, val lineCount: Int) : AiAgentAction()
    data class WriteFile(
        val relativePath: String,
        val linesAdded: Int,
        val linesRemoved: Int,
        val isNew: Boolean,
        val content: String
    ) : AiAgentAction()
    data class ListFiles(val fileCount: Int) : AiAgentAction()
    data class FinalAnswer(val text: String) : AiAgentAction()
    data class Error(val message: String) : AiAgentAction()
}

data class AttachedFile(
    val file: File,
    val relativePath: String,
    val sizeBytes: Long
)

data class AgentChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String = "",
    val attachedFiles: List<String> = emptyList(),
    val actions: List<AiAgentAction> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

class AiAgentEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val antigravityAuthManager: AntigravityAuthManager = AntigravityAuthManager(client),
    private val customEndpointClient: CustomEndpointClient = CustomEndpointClient(client)
) {

    /**
     * Executes an autonomous multi-step agentic task.
     * Iterates until AI provides a final answer or max iterations reached.
     */
    suspend fun executeTask(
        userPrompt: String,
        project: Project,
        attachedFiles: List<AttachedFile>,
        config: AiConfig,
        conversationHistory: List<AgentChatMessage>,
        onAction: (AiAgentAction) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val projectFiles = listProjectRelativeFiles(project.rootDir)

        val systemPrompt = buildSystemPrompt(project, projectFiles, isRu)
        val initialUserPrompt = buildInitialUserPrompt(userPrompt, project, attachedFiles, isRu)

        // Internal message history for the LLM
        val llmMessages = mutableListOf<CustomChatMessage>()
        llmMessages.add(CustomChatMessage(role = "system", content = systemPrompt))

        // Replay previous conversation context (last 4 turns)
        val recentHistory = conversationHistory.takeLast(4)
        for (hist in recentHistory) {
            if (hist.isUser) {
                llmMessages.add(CustomChatMessage(role = "user", content = hist.text))
            } else if (hist.text.isNotBlank()) {
                llmMessages.add(CustomChatMessage(role = "assistant", content = hist.text))
            }
        }

        llmMessages.add(CustomChatMessage(role = "user", content = initialUserPrompt))

        val maxIterations = 8
        var currentIteration = 0
        var finalExplanation = ""

        onAction(AiAgentAction.Thinking(if (isRu) "ИИ анализирует задачу..." else "AI is analyzing task..."))

        while (currentIteration < maxIterations) {
            currentIteration++

            val completionResult = sendChatCompletion(llmMessages, config)
            if (completionResult.isFailure) {
                val err = completionResult.exceptionOrNull()?.message ?: "Unknown error"
                onAction(AiAgentAction.Error(err))
                return@withContext Result.failure(Exception(err))
            }

            val responseText = completionResult.getOrNull() ?: ""
            llmMessages.add(CustomChatMessage(role = "assistant", content = responseText))

            // Check if AI performed any tool calls
            val toolCalls = extractToolCalls(responseText)

            if (toolCalls.isEmpty()) {
                // No more tool calls; extract cleanly formatted final text
                finalExplanation = cleanResponseText(responseText)
                onAction(AiAgentAction.FinalAnswer(finalExplanation))
                return@withContext Result.success(finalExplanation)
            }

            // Process all tool calls in order
            val toolResults = StringBuilder()

            for (call in toolCalls) {
                when (call.name) {
                    "read_file" -> {
                        val path = call.args["path"] ?: ""
                        val file = resolveSafeFile(project.rootDir, path)
                        if (file != null && file.exists() && file.isFile) {
                            val content = try { file.readText() } catch (e: Exception) { "Error reading file: ${e.message}" }
                            val lines = content.lines().size
                            onAction(AiAgentAction.ReadFile(path, lines))
                            toolResults.append("<tool_result name=\"read_file\" path=\"$path\">\n$content\n</tool_result>\n")
                        } else {
                            toolResults.append("<tool_result name=\"read_file\" path=\"$path\">Error: File not found or inaccessible.</tool_result>\n")
                        }
                    }

                    "write_file" -> {
                        val path = call.args["path"] ?: ""
                        val newContent = call.args["content"] ?: ""
                        val file = resolveSafeFile(project.rootDir, path)
                        if (file != null) {
                            file.parentFile?.mkdirs()
                            val isNew = !file.exists()
                            val oldContent = if (file.exists()) file.readText() else ""
                            file.writeText(newContent)

                            val (added, removed) = computeLineDiff(oldContent, newContent, isNew)
                            onAction(AiAgentAction.WriteFile(path, added, removed, isNew, newContent))
                            toolResults.append("<tool_result name=\"write_file\" path=\"$path\">Success: file written (${newContent.lines().size} lines).</tool_result>\n")
                        } else {
                            toolResults.append("<tool_result name=\"write_file\" path=\"$path\">Error: Invalid path outside project.</tool_result>\n")
                        }
                    }

                    "list_files" -> {
                        val files = listProjectRelativeFiles(project.rootDir)
                        onAction(AiAgentAction.ListFiles(files.size))
                        toolResults.append("<tool_result name=\"list_files\">\n${files.joinToString("\n")}\n</tool_result>\n")
                    }
                }
            }

            // Feed tool results back to LLM for next step
            llmMessages.add(
                CustomChatMessage(
                    role = "user",
                    content = toolResults.toString() + "\n" +
                            (if (isRu) "Продолжай работу над задачей или предоставь итоговый ответ, если всё завершено."
                            else "Continue working on the task or provide your final explanation if finished.")
                )
            )
        }

        if (finalExplanation.isBlank()) {
            finalExplanation = if (isRu) "Задача выполнена. Файлы проекта обновлены." else "Task completed. Project files updated."
            onAction(AiAgentAction.FinalAnswer(finalExplanation))
        }

        Result.success(finalExplanation)
    }

    private suspend fun sendChatCompletion(
        messages: List<CustomChatMessage>,
        config: AiConfig
    ): Result<String> {
        return when (config.provider) {
            "custom" -> customEndpointClient.sendChatCompletion(messages, config)
            "antigravity" -> sendAntigravityChat(messages, config)
            else -> sendGeminiApiChat(messages, config)
        }
    }

    private suspend fun sendGeminiApiChat(
        messages: List<CustomChatMessage>,
        config: AiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = config.apiKey.trim()
        val targetModel = config.model.trim().ifBlank { "gemini-2.5-flash" }
        val isRu = java.util.Locale.getDefault().language == "ru"

        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Google AI Studio API ключ не введен в Настройках."
                    else "Google AI Studio API key is missing in Settings."
                )
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$cleanKey"
        val jsonBody = JSONObject().apply {
            val contents = JSONArray()
            for (m in messages) {
                if (m.role == "system") {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", m.content) })
                        })
                    })
                } else {
                    contents.put(JSONObject().apply {
                        put("role", if (m.role == "assistant") "model" else "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", m.content) })
                        })
                    })
                }
            }
            put("contents", contents)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@use Result.failure(Exception("Gemini API (HTTP ${response.code}): $bodyStr"))
                }
                val respJson = JSONObject(bodyStr)
                val candidates = respJson.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text") ?: ""
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendAntigravityChat(
        messages: List<CustomChatMessage>,
        config: AiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        if (config.antigravityAccessToken.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Войдите в аккаунт Google Antigravity в Настройках."
                    else "Please sign in to Google Antigravity in Settings."
                )
            )
        }

        val promptBuilder = StringBuilder()
        for (m in messages) {
            promptBuilder.append("[${m.role.uppercase()}]:\n${m.content}\n\n")
        }

        val targetModel = config.model.trim().ifBlank { "gemini-3.8-flash" }
        val explainer = GeminiExplainer(client, antigravityAuthManager)
        val dummyDiag = com.prismde.core.model.Diagnostic(
            id = "chat_task",
            filePath = "",
            line = 0,
            column = 0,
            severity = com.prismde.core.model.DiagnosticSeverity.NOTE,
            rawMessage = "Chat Task",
            humanTitle = "Chat Task",
            humanExplanation = ""
        )

        // Delegate to existing Antigravity execution pipeline
        explainer.explainDiagnostic(dummyDiag, promptBuilder.toString(), config)
    }

    data class ParsedToolCall(val name: String, val args: Map<String, String>)

    fun extractToolCalls(text: String): List<ParsedToolCall> {
        val calls = mutableListOf<ParsedToolCall>()
        if (text.isBlank()) return calls

        // 1. Normalize DeepSeek DSML tokens (< | | DSML | | ... or <｜DSML｜>...) into clean XML tags
        val normalized = text
            .replace(Regex("""<[\s|｜]*DSML[\s|｜]*""", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("""</[\s|｜]*DSML[\s|｜]*""", RegexOption.IGNORE_CASE), "</")

        // 2. Matches standard XML <tool_call name="..."> and <invoke name="...">
        val invocationRegex = Pattern.compile(
            """<(?:tool_call|invoke)\s+name=["'](.*?)["']>([\s\S]*?)(?:</(?:tool_call|invoke)>|</calls>|(?=<(?:tool_call|invoke)\s+name=)|$)""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = invocationRegex.matcher(normalized)
        while (matcher.find()) {
            val name = matcher.group(1)?.trim()?.lowercase() ?: continue
            val inner = matcher.group(2)?.trim() ?: ""
            val args = mutableMapOf<String, String>()

            when (name) {
                "read_file" -> {
                    // Extract path from <path>, <parameter name="path">, <arg name="path">
                    val pathMatcher = Pattern.compile(
                        """<(?:path|(?:parameter|arg)\s+name=["']path["'])>([\s\S]*?)</(?:path|parameter|arg)>""",
                        Pattern.CASE_INSENSITIVE
                    ).matcher(inner)

                    if (pathMatcher.find()) {
                        args["path"] = pathMatcher.group(1)?.trim() ?: ""
                    } else {
                        // Check if inner contains only a plain path without tags
                        val cleanPath = inner.replace(Regex("<[^>]+>"), "").trim()
                        if (cleanPath.isNotBlank() && !cleanPath.contains("\n") && !cleanPath.contains(" ")) {
                            args["path"] = cleanPath
                        }
                    }
                }
                "write_file" -> {
                    val pathMatcher = Pattern.compile(
                        """<(?:path|(?:parameter|arg)\s+name=["']path["'])>([\s\S]*?)</(?:path|parameter|arg)>""",
                        Pattern.CASE_INSENSITIVE
                    ).matcher(inner)
                    if (pathMatcher.find()) {
                        args["path"] = pathMatcher.group(1)?.trim() ?: ""
                    }

                    val contentMatcher = Pattern.compile(
                        """<(?:content|(?:parameter|arg)\s+name=["']content["'])>([\s\S]*?)</(?:content|parameter|arg)>""",
                        Pattern.CASE_INSENSITIVE
                    ).matcher(inner)
                    if (contentMatcher.find()) {
                        args["content"] = contentMatcher.group(1) ?: ""
                    }
                }
                "list_files" -> {}
            }

            if (name == "list_files" || args.isNotEmpty()) {
                calls.add(ParsedToolCall(name, args))
            }
        }

        // Also check self-closing tags like <tool_call name="list_files"/>
        if (calls.isEmpty()) {
            val selfClosingRegex = Pattern.compile("""<(?:tool_call|invoke)\s+name=["'](.*?)["']\s*/>""", Pattern.CASE_INSENSITIVE)
            val scMatcher = selfClosingRegex.matcher(normalized)
            while (scMatcher.find()) {
                val name = scMatcher.group(1)?.trim()?.lowercase() ?: continue
                calls.add(ParsedToolCall(name, emptyMap()))
            }
        }

        return calls
    }

    fun cleanResponseText(raw: String): String {
        val normalized = raw
            .replace(Regex("""<[\s|｜]*DSML[\s|｜]*""", RegexOption.IGNORE_CASE), "<")
            .replace(Regex("""</[\s|｜]*DSML[\s|｜]*""", RegexOption.IGNORE_CASE), "</")

        return normalized
            .replace(Regex("""<(?:tool_call|invoke)\s+name=["'][^"']*["']>[\s\S]*?(?:</(?:tool_call|invoke)>|</calls>|(?=<(?:tool_call|invoke)\s+name=)|$)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<tool_call[\s\S]*?</tool_call>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<tool_result[\s\S]*?</tool_result>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?calls>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?invoke[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?parameter[^>]*>""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun computeLineDiff(oldText: String, newText: String, isNew: Boolean): Pair<Int, Int> {
        val newLines = if (newText.isEmpty()) emptyList() else newText.lines()
        if (isNew) return Pair(newLines.size, 0)

        val oldLines = if (oldText.isEmpty()) emptyList() else oldText.lines()
        val oldSet = oldLines.toSet()
        val newSet = newLines.toSet()

        val added = newLines.count { it !in oldSet }
        val removed = oldLines.count { it !in newSet }

        val diffAdded = maxOf(added, if (newLines.size > oldLines.size) newLines.size - oldLines.size else 0)
        val diffRemoved = maxOf(removed, if (oldLines.size > newLines.size) oldLines.size - newLines.size else 0)

        return Pair(diffAdded, diffRemoved)
    }

    private fun resolveSafeFile(rootDir: File, relativePath: String): File? {
        val clean = relativePath.trim().trimStart('/', '\\')
        val file = File(rootDir, clean)
        return try {
            val rootCanonical = rootDir.canonicalPath
            val fileCanonical = file.canonicalPath
            if (fileCanonical.startsWith(rootCanonical)) file else null
        } catch (_: Exception) {
            null
        }
    }

    fun listProjectRelativeFiles(rootDir: File): List<String> {
        if (!rootDir.exists()) return emptyList()
        val rootPath = rootDir.absolutePath
        return rootDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && !it.path.contains("/build/") && !it.path.contains("\\build\\") }
            .map { it.absolutePath.removePrefix(rootPath).trimStart('/', '\\') }
            .toList()
    }

    private fun buildSystemPrompt(project: Project, files: List<String>, isRu: Boolean): String {
        return if (isRu) """
            Ты автономный AI-ассистент разработчика в мобильной IDE PrismDE (C/C++, Android NDK).
            Твоя цель — помочь разработчику реализовать функциональность, исправить баги или создать новые файлы в проекте "${project.name}".

            Файлы в текущем проекте:
            ${files.joinToString("\n") { "- $it" }}

            У тебя есть доступ к инструментам проекта. Для вызова инструментов используй формат тегов:
            1. Чтение файла проекта:
               <tool_call name="read_file">
                 <path>относительный/путь/к/файлу</path>
               </tool_call>

            2. Создание или перезапись файла проекта:
               <tool_call name="write_file">
                 <path>относительный/путь/к/файлу</path>
                 <content>Полное новое содержимое файла</content>
               </tool_call>

            3. Просмотр структуры файлов проекта:
               <tool_call name="list_files"></tool_call>

            ПРАВИЛА РАБОТЫ:
            - Если тебе нужно узнать содержимое файла, вызови <tool_call name="read_file">.
            - Если нужно создать или модифицировать код, вызови <tool_call name="write_file">. Всегда предоставляй ПОЛНОЕ валидное содержимое файла в теге <content>.
            - ВНИМАНИЕ: Используй ТОЛЬКО стандартный тег <tool_call name="...">...</tool_call>. НЕ используй спецсимволы DSML (<|DSML|>, invoke, calls, parameter). Обязательно закрывай каждый вызов тегом </tool_call>.
            - После выполнения всех операций напиши краткое, профессиональное и понятное резюме на русском языке без лишней воды.
        """.trimIndent()
        else """
            You are an autonomous developer AI assistant in the mobile IDE PrismDE (C/C++, Android NDK).
            Your goal is to help the developer implement features, fix bugs, or create new files in project "${project.name}".

            Project files:
            ${files.joinToString("\n") { "- $it" }}

            You have access to tools via XML tags:
            1. Read a project file:
               <tool_call name="read_file">
                 <path>relative/path/to/file</path>
               </tool_call>

            2. Write or create a project file:
               <tool_call name="write_file">
                 <path>relative/path/to/file</path>
                 <content>Complete full file content</content>
               </tool_call>

            3. List project files:
               <tool_call name="list_files"></tool_call>

            RULES:
            - When you need file contents, call <tool_call name="read_file">.
            - When writing or modifying code, call <tool_call name="write_file">. Always provide the COMPLETE, valid file content inside <content>.
            - ATTENTION: Strictly use standard XML format: <tool_call name="...">...</tool_call>. Do NOT use DSML tokens (<|DSML|>, invoke, calls, parameter). Always close each tool call with </tool_call>.
            - When finished, provide a clear, concise summary of the changes made.
        """.trimIndent()
    }

    private fun buildInitialUserPrompt(
        userPrompt: String,
        project: Project,
        attachedFiles: List<AttachedFile>,
        isRu: Boolean
    ): String {
        val sb = StringBuilder()
        sb.append(userPrompt).append("\n\n")

        if (attachedFiles.isNotEmpty()) {
            sb.append(if (isRu) "Прикрепленные пользователем файлы:\n" else "Attached files:\n")
            for (att in attachedFiles) {
                sb.append("--- File: ${att.relativePath} ---\n")
                try {
                    sb.append(att.file.readText())
                } catch (e: Exception) {
                    sb.append("(Error reading: ${e.message})")
                }
                sb.append("\n--------------------------------\n\n")
            }
        }

        return sb.toString()
    }
}
