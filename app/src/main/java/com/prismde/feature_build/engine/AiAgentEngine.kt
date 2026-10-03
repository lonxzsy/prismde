package com.prismde.feature_build.engine

import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    data class BuildProject(val status: String, val isSuccess: Boolean, val errorCount: Int = 0) : AiAgentAction()
    data class ContextCompacted(val savedTokens: Int) : AiAgentAction()
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
        ndk: NdkVersion? = null,
        buildConfig: BuildConfiguration? = null,
        onAction: (AiAgentAction) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val projectFiles = listProjectRelativeFiles(project.rootDir)

        val systemPrompt = buildSystemPrompt(project, projectFiles, isRu)
        val initialUserPrompt = buildInitialUserPrompt(userPrompt, project, attachedFiles, isRu)

        // Internal message history for the LLM
        val llmMessages = mutableListOf<CustomChatMessage>()
        llmMessages.add(CustomChatMessage(role = "system", content = systemPrompt))

        // Replay previous conversation context with auto-compaction if tokens are high
        val maxModelTokens = TokenEstimator.getModelContextLimit(config.model, config.provider)
        val historyToUse = prepareHistoryWithCompaction(conversationHistory, maxModelTokens, onAction)

        for (hist in historyToUse) {
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

                    "build_project" -> {
                        onAction(AiAgentAction.Thinking(if (isRu) "Запуск компиляции проекта через NDK..." else "Running project compilation with NDK..."))
                        if (ndk == null || !ndk.isInstalled) {
                            val errMsg = if (isRu) "Ошибка: NDK не установлен или не настроен. Установите NDK в Настройках для сборки."
                                         else "Error: Android NDK is not installed or configured. Please install NDK from Settings."
                            onAction(AiAgentAction.BuildProject(status = if (isRu) "NDK не установлен" else "NDK not installed", isSuccess = false, errorCount = 1))
                            toolResults.append("<tool_result name=\"build_project\" status=\"ERROR\">\n$errMsg\n</tool_result>\n")
                        } else {
                            val bConfig = buildConfig ?: BuildConfiguration()
                            val runner = BuildProcessRunner()
                            val logs = mutableListOf<String>()
                            val diagnostics = mutableListOf<Diagnostic>()
                            var completedEvent: BuildOutputEvent.Completed? = null

                            val collectJob = launch {
                                runner.events.collect { ev ->
                                    when (ev) {
                                        is BuildOutputEvent.LogLine -> logs.add(ev.text)
                                        is BuildOutputEvent.DiagnosticFound -> diagnostics.add(ev.diagnostic)
                                        is BuildOutputEvent.Completed -> completedEvent = ev
                                    }
                                }
                            }

                            val buildSuccess = runner.runBuild(project, ndk, bConfig)
                            collectJob.cancel()

                            val errors = diagnostics.filter { it.severity == com.prismde.core.model.DiagnosticSeverity.ERROR || it.severity == com.prismde.core.model.DiagnosticSeverity.FATAL }
                            val errorCount = if (errors.isNotEmpty()) errors.size else if (!buildSuccess) 1 else 0

                            if (buildSuccess) {
                                val artifact = completedEvent?.artifactFile?.absolutePath ?: "libs/${bConfig.selectedAbi.abiString}/lib${project.name}.so"
                                onAction(AiAgentAction.BuildProject(status = if (isRu) "Сборка успешна" else "Build Succeeded", isSuccess = true, errorCount = 0))
                                toolResults.append(
                                    "<tool_result name=\"build_project\" status=\"SUCCESS\">\n" +
                                    "Build completed successfully!\n" +
                                    "Artifact: $artifact\n" +
                                    "All native source files compiled cleanly without errors.\n" +
                                    "</tool_result>\n"
                                )
                            } else {
                                onAction(AiAgentAction.BuildProject(status = if (isRu) "Ошибка сборки ($errorCount)" else "Build Failed ($errorCount)", isSuccess = false, errorCount = errorCount))
                                val errorDetails = StringBuilder()
                                errorDetails.append("Build FAILED with $errorCount error(s):\n")
                                for (diag in errors.take(12)) {
                                    errorDetails.append("- ${diag.filePath}:${diag.line}:${diag.column}: [${diag.severity}] ${diag.rawMessage}\n")
                                }
                                val errorLogs = logs.filter { it.contains("error:", ignoreCase = true) || it.contains("failed", ignoreCase = true) }.takeLast(8)
                                if (errorLogs.isNotEmpty()) {
                                    errorDetails.append("\nCompiler error output:\n").append(errorLogs.joinToString("\n"))
                                }
                                toolResults.append(
                                    "<tool_result name=\"build_project\" status=\"FAILURE\">\n" +
                                    errorDetails.toString().trim() + "\n" +
                                    (if (isRu) "ВНИМАНИЕ: Проанализируй ошибки компилятора выше, исправь исходные файлы с помощью write_file и повтори сборку."
                                     else "ATTENTION: Analyze compiler errors above, fix source files with write_file, and re-verify.") +
                                    "\n</tool_result>\n"
                                )
                            }
                        }
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

    /**
     * Compresses older conversation history if tokens approach context threshold.
     */
    fun prepareHistoryWithCompaction(
        conversationHistory: List<AgentChatMessage>,
        maxModelTokens: Int,
        onAction: ((AiAgentAction) -> Unit)? = null
    ): List<AgentChatMessage> {
        if (conversationHistory.size <= 4) return conversationHistory

        var totalTokens = 0
        for (m in conversationHistory) {
            totalTokens += TokenEstimator.estimateTokens(m.text)
        }

        val threshold = (maxModelTokens * 0.65).toInt().coerceAtMost(30_000)
        if (totalTokens < threshold) {
            return conversationHistory
        }

        val keepLastCount = 3
        val olderMessages = conversationHistory.dropLast(keepLastCount)
        val recentMessages = conversationHistory.takeLast(keepLastCount)

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("[Context Compacted: Previous Task Summary]\n")
        for (m in olderMessages) {
            if (m.isUser) {
                summaryBuilder.append("User requested: ${m.text.take(160)}\n")
            } else if (m.text.isNotBlank()) {
                val firstLine = m.text.lines().firstOrNull { it.isNotBlank() } ?: ""
                summaryBuilder.append("Assistant response: ${firstLine.take(160)}\n")
            }
        }

        val compactedText = summaryBuilder.toString().trim()
        val savedTokens = (totalTokens - TokenEstimator.estimateTokens(compactedText)).coerceAtLeast(0)
        onAction?.invoke(AiAgentAction.ContextCompacted(savedTokens))

        val summaryMessage = AgentChatMessage(
            id = "compacted_summary_${System.currentTimeMillis()}",
            isUser = false,
            text = compactedText,
            actions = listOf(AiAgentAction.ContextCompacted(savedTokens))
        )

        return listOf(summaryMessage) + recentMessages
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
                "build_project" -> {}
            }

            if (name == "list_files" || name == "build_project" || args.isNotEmpty()) {
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

            4. Сборка и компиляция проекта (Clang/NDK/CMake):
               <tool_call name="build_project"></tool_call>

            ПРАВИЛА РАБОТЫ:
            - Если тебе нужно узнать содержимое файла, вызови <tool_call name="read_file">.
            - Если нужно создать или модифицировать код, вызови <tool_call name="write_file">. Всегда предоставляй ПОЛНОЕ валидное содержимое файла в теге <content>.
            - Если пользователь просит собрать или проверить проект, или после внесения правок в код C/C++, вызови <tool_call name="build_project"></tool_call>.
            - Если сборка вернет ошибки (FAILURE), внимательно изучи строки с ошибками компилятора Clang, открой указанные файлы, исправь ошибки и при необходимости снова вызови build_project.
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

            4. Build and compile project (Clang/NDK/CMake):
               <tool_call name="build_project"></tool_call>

            RULES:
            - When you need file contents, call <tool_call name="read_file">.
            - When writing or modifying code, call <tool_call name="write_file">. Always provide the COMPLETE, valid file content inside <content>.
            - If user asks to build or compile the project, or after making changes to native code, invoke <tool_call name="build_project"></tool_call>.
            - If the build fails with errors (FAILURE), carefully read the compiler diagnostics (file and line numbers), inspect the code, fix errors with write_file, and re-test.
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
