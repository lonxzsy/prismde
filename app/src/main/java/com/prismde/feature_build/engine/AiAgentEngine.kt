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
    data class Thinking(val message: String, val thoughtDetail: String? = null) : AiAgentAction()
    data class ReadFile(val relativePath: String, val lineCount: Int) : AiAgentAction()
    data class SearchCode(val query: String, val matchesCount: Int) : AiAgentAction()
    data class ApplyDiff(val relativePath: String, val hunksCount: Int) : AiAgentAction()
    data class WebSearchAction(val query: String, val resultCount: Int) : AiAgentAction()
    data class WebFetchAction(val url: String, val charCount: Int) : AiAgentAction()
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
    data class TokenUsageUpdated(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AiAgentAction()
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
    val thinkingText: String? = null,
    val thinkingDurationMs: Long = 0L,
    val isThinkingActive: Boolean = false,
    val serverTokens: Int = 0,
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

        val skills = ProjectSkillManager.loadProjectSkills(project.rootDir)
        val skillsPrompt = ProjectSkillManager.formatSkillsPrompt(skills, isRu)
        val systemPrompt = buildSystemPrompt(project, projectFiles, isRu) + skillsPrompt
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

        val maxIterations = 25
        var currentIteration = 0
        var finalExplanation = ""
        var lastBuildSuccess: Boolean? = null
        var filesModifiedSinceBuild = false
        val userPromptLower = userPrompt.lowercase()
        val userRequestedBuild = userPromptLower.contains("собер") ||
                userPromptLower.contains("билд") ||
                userPromptLower.contains("build") ||
                userPromptLower.contains("compil") ||
                userPromptLower.contains("компил")

        onAction(AiAgentAction.Thinking(if (isRu) "ИИ анализирует задачу..." else "AI is analyzing task..."))

        while (currentIteration < maxIterations) {
            currentIteration++

            val completionResult = sendChatCompletion(llmMessages, config)
            if (completionResult.isFailure) {
                val err = completionResult.exceptionOrNull()?.message ?: "Unknown error"
                onAction(AiAgentAction.Error(err))
                return@withContext Result.failure(Exception(err))
            }

            val completionResponse = completionResult.getOrNull()
            val responseText = completionResponse?.text ?: ""
            val reasoning = completionResponse?.reasoning

            if (completionResponse != null && completionResponse.totalTokens > 0) {
                onAction(AiAgentAction.TokenUsageUpdated(completionResponse.promptTokens, completionResponse.completionTokens, completionResponse.totalTokens))
            }

            if (!reasoning.isNullOrBlank()) {
                onAction(AiAgentAction.Thinking(message = if (isRu) "Размышления модели" else "Model reasoning", thoughtDetail = reasoning))
            }

            llmMessages.add(CustomChatMessage(role = "assistant", content = responseText))

            // Check if AI performed any tool calls
            val toolCalls = extractToolCalls(responseText)

            if (toolCalls.isEmpty()) {
                // If code was changed or build previously failed, force re-verification before finishing
                if ((lastBuildSuccess == false && filesModifiedSinceBuild) || (userRequestedBuild && (lastBuildSuccess == null || filesModifiedSinceBuild))) {
                    if (currentIteration < maxIterations) {
                        val reminder = if (isRu) {
                            "ВНИМАНИЕ: Файлы проекта были изменены, но сборка еще не была повторно запущена для проверки (или предыдущая сборка завершилась ошибкой). Обязательно вызови <tool_call name=\"build_project\"></tool_call> прямо сейчас, чтобы проверить успешность сборки перед окончательным ответом."
                        } else {
                            "ATTENTION: Project files were modified, but build has not been re-run to verify compilation (or previous build failed). You MUST invoke <tool_call name=\"build_project\"></tool_call> right now to verify compilation before giving your final answer."
                        }
                        onAction(AiAgentAction.Thinking(if (isRu) "Проверка сборки: тестирование компиляции изменений..." else "Verifying build: testing compilation of changes..."))
                        llmMessages.add(CustomChatMessage(role = "user", content = reminder))
                        continue
                    }
                }

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
                            val allLines = try { file.readLines() } catch (e: Exception) { listOf("Error reading file: ${e.message}") }
                            val totalLines = allLines.size
                            val offset = call.args["offset"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val limit = call.args["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 350

                            // Check if this is a giant binary byte array header (e.g. font / image bytes like SuisseIntl_Regular.h)
                            val isBinaryBlobHeader = totalLines > 200 && allLines.take(50).count { it.contains("0x") && it.contains(",") } > 25

                            val contentStr = if (isBinaryBlobHeader && offset == 1 && limit == 350) {
                                val headerPart = allLines.take(15).joinToString("\n")
                                val footerPart = allLines.takeLast(5).joinToString("\n")
                                "$headerPart\n\n// [... Embedded binary font/asset array: $totalLines lines of hex byte data omitted to preserve context. Total size: ${file.length() / 1024} KB ...]\n\n$footerPart"
                            } else {
                                val startIdx = (offset - 1).coerceIn(0, totalLines)
                                val endIdx = minOf(totalLines, startIdx + limit)
                                val slice = allLines.subList(startIdx, endIdx).joinToString("\n")
                                if (endIdx < totalLines) {
                                    "$slice\n\n// [... Showing lines $offset..$endIdx of $totalLines lines (${file.length() / 1024} KB). Specify <offset>${endIdx + 1}</offset> in read_file to read more ...]"
                                } else {
                                    slice
                                }
                            }

                            onAction(AiAgentAction.ReadFile(path, totalLines))
                            toolResults.append("<tool_result name=\"read_file\" path=\"$path\" lines=\"$totalLines\">\n$contentStr\n</tool_result>\n")
                        } else {
                            toolResults.append("<tool_result name=\"read_file\" path=\"$path\">Error: File not found or inaccessible.</tool_result>\n")
                        }
                    }

                    "search_code" -> {
                        val query = call.args["query"] ?: ""
                        val matches = searchInProject(project.rootDir, query)
                        onAction(AiAgentAction.SearchCode(query, matches.size))
                        val resultStr = if (matches.isEmpty()) {
                            "No matches found for query: \"$query\""
                        } else {
                            matches.take(25).joinToString("\n") { (path, line, text) ->
                                "$path:$line: $text"
                            }
                        }
                        toolResults.append("<tool_result name=\"search_code\" query=\"$query\">\n$resultStr\n</tool_result>\n")
                    }

                    "apply_diff" -> {
                        val path = call.args["path"] ?: ""
                        val findBlock = call.args["find"] ?: ""
                        val replaceBlock = call.args["replace"] ?: ""
                        val file = resolveSafeFile(project.rootDir, path)
                        if (file != null && file.exists()) {
                            val content = file.readText()
                            if (findBlock.isNotBlank() && content.contains(findBlock)) {
                                val oldContent = content
                                val newContent = content.replaceFirst(findBlock, replaceBlock)
                                file.writeText(newContent)
                                filesModifiedSinceBuild = true
                                val (added, removed) = computeLineDiff(oldContent, newContent, false)
                                onAction(AiAgentAction.ApplyDiff(path, 1))
                                onAction(AiAgentAction.WriteFile(path, added, removed, false, newContent))
                                toolResults.append("<tool_result name=\"apply_diff\" path=\"$path\">Success: block replaced successfully.</tool_result>\n")
                            } else {
                                toolResults.append("<tool_result name=\"apply_diff\" path=\"$path\">Error: target <find> block not found in file. Use read_file to inspect exact lines or write_file.</tool_result>\n")
                            }
                        } else {
                            toolResults.append("<tool_result name=\"apply_diff\" path=\"$path\">Error: File not found.</tool_result>\n")
                        }
                    }

                    "web_search" -> {
                        val query = call.args["query"] ?: ""
                        onAction(AiAgentAction.Thinking(if (isRu) "Поиск в интернете: \"$query\"..." else "Searching web: \"$query\"..."))
                        val searchService = AiWebSearchService(client)
                        val results = searchService.searchWeb(query)
                        onAction(AiAgentAction.WebSearchAction(query, results.size))
                        val resultStr = if (results.isEmpty()) {
                            "No web search results found for: \"$query\""
                        } else {
                            results.joinToString("\n\n") { item ->
                                "Title: ${item.title}\nURL: ${item.url}\nSnippet: ${item.snippet}"
                            }
                        }
                        toolResults.append("<tool_result name=\"web_search\" query=\"$query\">\n$resultStr\n</tool_result>\n")
                    }

                    "web_fetch" -> {
                        val url = call.args["url"] ?: call.args["link"] ?: ""
                        onAction(AiAgentAction.Thinking(if (isRu) "Загрузка страницы: $url..." else "Fetching web page: $url..."))
                        val fetcher = AiWebFetchService(client)
                        val fetchResult = fetcher.fetchUrl(url)
                        fetchResult.fold(
                            onSuccess = { res ->
                                onAction(AiAgentAction.WebFetchAction(res.url, res.markdown.length))
                                val note = if (res.isTruncated) "\n(Note: Content truncated to 100KB limit)" else ""
                                toolResults.append("<tool_result name=\"web_fetch\" url=\"$url\">\nTitle: ${res.title}\n${res.markdown}$note\n</tool_result>\n")
                            },
                            onFailure = { err ->
                                toolResults.append("<tool_result name=\"web_fetch\" url=\"$url\">Error fetching page: ${err.message}</tool_result>\n")
                            }
                        )
                    }

                    "download_file" -> {
                        val url = call.args["url"] ?: call.args["link"] ?: ""
                        val path = call.args["path"] ?: ""
                        val toCHeader = call.args["to_c_header"]?.equals("true", ignoreCase = true) == true || path.endsWith(".h", ignoreCase = true)
                        val file = resolveSafeFile(project.rootDir, path)
                        if (file != null && url.isNotBlank()) {
                            onAction(AiAgentAction.Thinking(if (isRu) "Загрузка файла: $url..." else "Downloading file: $url..."))
                            val fetcher = AiWebFetchService(client)
                            val tempFile = File.createTempFile("prism_dl", ".bin")
                            val dlResult = fetcher.downloadBinaryFile(url, tempFile)
                            dlResult.fold(
                                onSuccess = { bytes ->
                                    if (toCHeader) {
                                        val arrayName = file.nameWithoutExtension.replace("[^a-zA-Z0-9_]".toRegex(), "_")
                                        val rawBytes = tempFile.readBytes()
                                        val sb = java.lang.StringBuilder()
                                        sb.append("// Auto-generated from $url by PrismDE AI Assistant\n")
                                        sb.append("#pragma once\n\n")
                                        sb.append("const unsigned int ${arrayName}_size = ${rawBytes.size};\n")
                                        sb.append("const unsigned char ${arrayName}_data[${rawBytes.size}] = {\n")
                                        for (i in rawBytes.indices) {
                                            if (i % 16 == 0) sb.append("    ")
                                            sb.append(String.format("0x%02X, ", rawBytes[i]))
                                            if (i % 16 == 15) sb.append("\n")
                                        }
                                        sb.append("\n};\n")
                                        file.parentFile?.mkdirs()
                                        val newContent = sb.toString()
                                        file.writeText(newContent)
                                        tempFile.delete()
                                        filesModifiedSinceBuild = true
                                        onAction(AiAgentAction.WriteFile(path, newContent.lines().size, 0, true, newContent))
                                        toolResults.append("<tool_result name=\"download_file\" path=\"$path\">Success: downloaded ${bytes / 1024} KB from $url and converted to C++ header with array '${arrayName}_data' (${rawBytes.size} bytes).</tool_result>\n")
                                    } else {
                                        file.parentFile?.mkdirs()
                                        tempFile.copyTo(file, overwrite = true)
                                        tempFile.delete()
                                        filesModifiedSinceBuild = true
                                        toolResults.append("<tool_result name=\"download_file\" path=\"$path\">Success: downloaded ${bytes / 1024} KB to $path.</tool_result>\n")
                                    }
                                },
                                onFailure = { err ->
                                    try { tempFile.delete() } catch (_: Throwable) {}
                                    toolResults.append("<tool_result name=\"download_file\" path=\"$path\">Error downloading: ${err.message}</tool_result>\n")
                                }
                            )
                        } else {
                            toolResults.append("<tool_result name=\"download_file\">Error: Invalid URL or path outside project.</tool_result>\n")
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
                            filesModifiedSinceBuild = true

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
                        val detectedType = if (buildConfig?.projectType == com.prismde.core.model.ProjectType.AUTO_DETECT || buildConfig == null) {
                            ProjectDetector.detect(project.rootDir)
                        } else {
                            buildConfig.projectType
                        }
                        val isMaven = detectedType == com.prismde.core.model.ProjectType.MAVEN
                        val isGradle = detectedType == com.prismde.core.model.ProjectType.GRADLE
                        val isJvmOrGradle = isMaven || isGradle

                        onAction(
                            AiAgentAction.Thinking(
                                when {
                                    isGradle -> if (isRu) "Запуск сборки проекта через Gradle..." else "Running project compilation with Gradle..."
                                    isMaven -> if (isRu) "Запуск сборки проекта через Maven..." else "Running project compilation with Maven..."
                                    else -> if (isRu) "Запуск компиляции проекта через NDK..." else "Running project compilation with NDK..."
                                }
                            )
                        )

                        if (!isJvmOrGradle && (ndk == null || !ndk.isInstalled)) {
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
                            lastBuildSuccess = buildSuccess
                            filesModifiedSinceBuild = false

                            val errors = diagnostics.filter { it.severity == com.prismde.core.model.DiagnosticSeverity.ERROR || it.severity == com.prismde.core.model.DiagnosticSeverity.FATAL }
                            val errorCount = if (errors.isNotEmpty()) errors.size else if (!buildSuccess) 1 else 0

                            if (buildSuccess) {
                                val defaultArtifact = when {
                                    isGradle -> "build/outputs/apk/debug/${project.name}.apk"
                                    isMaven -> "target/${project.name}.jar"
                                    else -> "libs/${bConfig.selectedAbi.abiString}/lib${project.name}.so"
                                }
                                val artifact = completedEvent?.artifactFile?.absolutePath ?: defaultArtifact
                                onAction(AiAgentAction.BuildProject(status = if (isRu) "Сборка успешна" else "Build Succeeded", isSuccess = true, errorCount = 0))
                                toolResults.append(
                                    "<tool_result name=\"build_project\" status=\"SUCCESS\">\n" +
                                    "Build completed successfully!\n" +
                                    "Artifact: $artifact\n" +
                                    (if (isGradle) "Gradle Android/JVM project built cleanly without errors.\n"
                                     else if (isMaven) "Maven project compiled cleanly without errors.\n"
                                     else "All native source files compiled cleanly without errors.\n") +
                                    "</tool_result>\n"
                                )
                            } else {
                                onAction(AiAgentAction.BuildProject(status = if (isRu) "Ошибка сборки ($errorCount)" else "Build Failed ($errorCount)", isSuccess = false, errorCount = errorCount))
                                val errorDetails = StringBuilder()
                                errorDetails.append("Build FAILED with $errorCount error(s):\n")
                                for (diag in errors.take(12)) {
                                    errorDetails.append("- ${diag.filePath}:${diag.line}:${diag.column}: [${diag.severity}] ${diag.rawMessage}\n")
                                }
                                val errorLogs = logs.filter { it.contains("error:", ignoreCase = true) || it.contains("failed", ignoreCase = true) || it.contains("[ERROR]", ignoreCase = true) }.takeLast(8)
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
            val nextInstruction = if (isRu) {
                if (filesModifiedSinceBuild) {
                    "Продолжай работу над задачей или предоставь итоговый отчет, если все изменения полностью завершены."
                } else {
                    "Продолжай работу над задачей: внеси необходимые изменения в файлы проекта (write_file / apply_diff / download_file). Задача еще НЕ выполнена, так как файлы проекта еще не обновлены!"
                }
            } else {
                if (filesModifiedSinceBuild) {
                    "Continue working on the task or provide your final explanation if finished."
                } else {
                    "Continue working on the task: apply the required changes to project files (write_file / apply_diff / download_file). The task is NOT complete until code is updated!"
                }
            }

            llmMessages.add(
                CustomChatMessage(
                    role = "user",
                    content = toolResults.toString() + "\n" + nextInstruction
                )
            )
        }

        if (finalExplanation.isBlank()) {
            finalExplanation = if (filesModifiedSinceBuild) {
                if (isRu) "Задача выполнена. Файлы проекта обновлены." else "Task completed. Project files updated."
            } else {
                if (isRu) "Анализ завершен. Для внесения изменений в файлы проекта уточните параметры."
                else "Analysis completed. Please specify changes to apply to project files."
            }
            onAction(AiAgentAction.FinalAnswer(finalExplanation))
        }

        Result.success(finalExplanation)
    }

    private fun searchInProject(rootDir: File, query: String): List<Triple<String, Int, String>> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<Triple<String, Int, String>>()
        val lowerQuery = query.lowercase()
        val skipDirs = setOf(".git", ".gradle", "build", "obj", ".cxx", ".idea", "node_modules", "bin")

        rootDir.walkTopDown()
            .onEnter { dir -> !skipDirs.contains(dir.name) }
            .maxDepth(8)
            .forEach { file ->
                if (file.isFile && file.length() in 1L..1_000_000L && results.size < 30) {
                    try {
                        val relPath = file.relativeTo(rootDir).path.replace('\\', '/')
                        var lineNum = 1
                        file.forEachLine { line ->
                            if (results.size < 30 && line.lowercase().contains(lowerQuery)) {
                                results.add(Triple(relPath, lineNum, line.trim()))
                            }
                            lineNum++
                        }
                    } catch (_: Throwable) {}
                }
            }
        return results
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
    ): Result<ChatCompletionResponse> {
        return when (config.provider) {
            "custom" -> customEndpointClient.sendChatCompletionDetails(messages, config)
            "antigravity" -> sendAntigravityChat(messages, config)
            else -> sendGeminiApiChat(messages, config)
        }
    }

    private suspend fun sendGeminiApiChat(
        messages: List<CustomChatMessage>,
        config: AiConfig
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
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
        val systemContent = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }.trim()
        val nonSystemMessages = messages.filter { it.role != "system" }

        val contents = JSONArray()
        var lastRole: String? = null
        var currentText = StringBuilder()

        for (m in nonSystemMessages) {
            val geminiRole = if (m.role == "assistant") "model" else "user"
            if (geminiRole == lastRole) {
                currentText.append("\n\n").append(m.content)
            } else {
                if (lastRole != null && currentText.isNotEmpty()) {
                    contents.put(JSONObject().apply {
                        put("role", lastRole)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", currentText.toString()) })
                        })
                    })
                }
                lastRole = geminiRole
                currentText = StringBuilder(m.content)
            }
        }
        if (lastRole != null && currentText.isNotEmpty()) {
            contents.put(JSONObject().apply {
                put("role", lastRole)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", currentText.toString()) })
                })
            })
        }

        val jsonBody = JSONObject().apply {
            if (systemContent.isNotBlank()) {
                val sysObj = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemContent) })
                    })
                }
                put("system_instruction", sysObj)
                put("systemInstruction", sysObj)
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
                val usageMeta = respJson.optJSONObject("usageMetadata")
                val promptTokens = usageMeta?.optInt("promptTokenCount") ?: 0
                val completionTokens = usageMeta?.optInt("candidatesTokenCount") ?: 0
                val totalTokens = usageMeta?.optInt("totalTokenCount") ?: (promptTokens + completionTokens)

                val candidates = respJson.optJSONArray("candidates")
                val candidateObj = candidates?.optJSONObject(0)
                val parts = candidateObj?.optJSONObject("content")?.optJSONArray("parts")

                var mainText = ""
                var reasoningText: String? = null

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i)
                        val text = part?.optString("text") ?: ""
                        if (part?.optBoolean("thought") == true) {
                            reasoningText = (reasoningText ?: "") + text.trim()
                        } else {
                            mainText += text
                        }
                    }
                }

                // Check for embedded <think>...</think> tags if not already separated
                val thinkPattern = java.util.regex.Pattern.compile("""<think>([\s\S]*?)</think>""", java.util.regex.Pattern.CASE_INSENSITIVE)
                val thinkMatcher = thinkPattern.matcher(mainText)
                if (thinkMatcher.find()) {
                    val foundThink = thinkMatcher.group(1)?.trim() ?: ""
                    if (foundThink.isNotBlank() && reasoningText == null) {
                        reasoningText = foundThink
                    }
                    mainText = mainText.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "").trim()
                }

                Result.success(
                    ChatCompletionResponse(
                        text = mainText,
                        reasoning = reasoningText,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun sendAntigravityChat(
        messages: List<CustomChatMessage>,
        config: AiConfig
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        val isRu = java.util.Locale.getDefault().language == "ru"
        val token = config.antigravityAccessToken.trim()
        if (token.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException(
                    if (isRu) "Войдите в аккаунт Google Antigravity в Настройках."
                    else "Please sign in to Google Antigravity in Settings."
                )
            )
        }

        val rawModel = config.model.trim().ifBlank { "gemini-3.8-flash" }
        val selectedModel = GeminiExplainer.mapAntigravityRuntimeModel(rawModel)
        val project = antigravityAuthManager.loadCodeAssist(token)

        val systemContent = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }.trim()
        val nonSystemMessages = messages.filter { it.role != "system" }

        val contents = JSONArray()
        var lastRole: String? = null
        var currentText = StringBuilder()

        for (m in nonSystemMessages) {
            val geminiRole = if (m.role == "assistant") "model" else "user"
            if (geminiRole == lastRole) {
                currentText.append("\n\n").append(m.content)
            } else {
                if (lastRole != null && currentText.isNotEmpty()) {
                    contents.put(JSONObject().apply {
                        put("role", lastRole)
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", currentText.toString()) })
                        })
                    })
                }
                lastRole = geminiRole
                currentText = StringBuilder(m.content)
            }
        }
        if (lastRole != null && currentText.isNotEmpty()) {
            contents.put(JSONObject().apply {
                put("role", lastRole)
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", currentText.toString()) })
                })
            })
        }

        val requestObj = JSONObject().apply {
            if (systemContent.isNotBlank()) {
                val sysObj = JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemContent) })
                    })
                }
                put("system_instruction", sysObj)
                put("systemInstruction", sysObj)
            }
            put("contents", contents)
        }

        val cloudcodePayload = JSONObject().apply {
            put("project", project)
            put("model", selectedModel)
            put("request", requestObj)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val url = "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Antigravity-IDE")
            .header("X-Vertex-AI-LLM-Shared-Request-Type", "CHAT")
            .header("goog-originating-logical-product-id", "cloudcode")
            .post(cloudcodePayload.toString().toRequestBody(mediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@use Result.failure(Exception(if (isRu) "Сессия Google Antigravity истекла. Перезайдите в Настройках." else "Antigravity session expired. Please sign in again."))
                }
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    return@use Result.failure(Exception("Antigravity (HTTP ${response.code}): $errBody"))
                }

                val body = response.body ?: return@use Result.failure(Exception("Empty Antigravity response body"))
                val fullText = StringBuilder()
                var reasoningText: String? = null

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
                                                if (p.optBoolean("thought") == true) {
                                                    reasoningText = (reasoningText ?: "") + t
                                                } else if (t.isNotBlank()) {
                                                    fullText.append(t)
                                                }
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

                var mainText = fullText.toString()
                val thinkPattern = java.util.regex.Pattern.compile("""<think>([\s\S]*?)</think>""", java.util.regex.Pattern.CASE_INSENSITIVE)
                val thinkMatcher = thinkPattern.matcher(mainText)
                if (thinkMatcher.find()) {
                    val foundThink = thinkMatcher.group(1)?.trim() ?: ""
                    if (foundThink.isNotBlank() && reasoningText == null) {
                        reasoningText = foundThink
                    }
                    mainText = mainText.replace(Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE), "").trim()
                }

                val promptTokens = TokenEstimator.estimateTokens(messages.joinToString(" ") { it.content })
                val complTokens = TokenEstimator.estimateTokens(mainText)

                Result.success(
                    ChatCompletionResponse(
                        text = mainText,
                        reasoning = reasoningText,
                        promptTokens = promptTokens,
                        completionTokens = complTokens,
                        totalTokens = promptTokens + complTokens
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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

            // 1. Universal parameter extraction from XML tags: <tag>value</tag>
            val tagPattern = Pattern.compile("""<([a-zA-Z0-9_]+)>([\s\S]*?)</\1>""", Pattern.CASE_INSENSITIVE)
            val tagMatcher = tagPattern.matcher(inner)
            while (tagMatcher.find()) {
                val tagKey = tagMatcher.group(1)?.lowercase() ?: continue
                val tagVal = tagMatcher.group(2) ?: ""
                args[tagKey] = tagVal
            }

            // 2. Universal parameter extraction from attributes: <parameter name="key">value</parameter>
            val paramPattern = Pattern.compile("""<(?:parameter|arg)\s+name=["'](.*?)["']>([\s\S]*?)</(?:parameter|arg)>""", Pattern.CASE_INSENSITIVE)
            val paramMatcher = paramPattern.matcher(inner)
            while (paramMatcher.find()) {
                val paramKey = paramMatcher.group(1)?.lowercase() ?: continue
                val paramVal = paramMatcher.group(2) ?: ""
                args[paramKey] = paramVal
            }

            // 3. Fallbacks if model outputs plain text without sub-tags:
            when (name) {
                "web_search", "search_code" -> {
                    if (!args.containsKey("query")) {
                        val clean = inner.replace(Regex("<[^>]+>"), "").trim()
                        if (clean.isNotBlank()) args["query"] = clean
                    }
                }
                "web_fetch" -> {
                    if (!args.containsKey("url")) {
                        val clean = inner.replace(Regex("<[^>]+>"), "").trim()
                        if (clean.startsWith("http://") || clean.startsWith("https://")) args["url"] = clean
                    }
                }
                "read_file" -> {
                    if (!args.containsKey("path")) {
                        val clean = inner.replace(Regex("<[^>]+>"), "").trim()
                        if (clean.isNotBlank() && !clean.contains("\n") && !clean.contains(" ")) args["path"] = clean
                    }
                }
            }

            if (name in setOf("list_files", "build_project") || args.isNotEmpty()) {
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
            .replace(Regex("""</?tool_call[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?tool_result[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?calls>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?invoke[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?parameter[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</?arg[^>]*>""", RegexOption.IGNORE_CASE), "")
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
            Ты автономный AI-ассистент разработчика в мобильной IDE PrismDE (C/C++, Android NDK, CMake, Gradle Android/Java, Maven).
            Твоя цель — помочь разработчику реализовать функциональность, исправить баги или создать новые файлы в проекте "${project.name}".

            Файлы в текущем проекте:
            ${files.joinToString("\n") { "- $it" }}

            У тебя есть доступ к инструментам проекта. Для вызова инструментов используй формат тегов:
            1. Чтение файла проекта:
               <tool_call name="read_file">
                 <path>относительный/путь/к/файлу</path>
               </tool_call>

            2. Поиск фрагмента кода или символа по всем файлам проекта (grep):
               <tool_call name="search_code">
                 <query>символ_или_текст_для_поиска</query>
               </tool_call>

            3. Точечное изменение файла через замену блока (diff / patch):
               <tool_call name="apply_diff">
                 <path>относительный/путь/к/файлу</path>
                 <find>старый точный фрагмент кода</find>
                 <replace>новый фрагмент кода</replace>
               </tool_call>

            4. Создание или полная перезапись файла проекта:
               <tool_call name="write_file">
                 <path>относительный/путь/к/файлу</path>
                 <content>Полное новое содержимое файла</content>
               </tool_call>

            5. Просмотр структуры файлов проекта:
               <tool_call name="list_files"></tool_call>

            6. Поиск актуальной информации, документации и решений в интернете:
               <tool_call name="web_search">
                 <query>поисковый запрос на английском или русском</query>
               </tool_call>

            7. Загрузка и чтение полной веб-страницы, статьи или документации по URL (web_fetch):
               <tool_call name="web_fetch">
                 <url>https://example.com/docs/api</url>
               </tool_call>

            8. Прямое скачивание файлов/шрифтов/библиотек из интернета в проект (download_file):
               <tool_call name="download_file">
                 <url>https://raw.githubusercontent.com/.../Inter.ttf</url>
                 <path>jni/ui/fonts/Inter_Regular.h</path> <!-- если расширение .h, автоматически конвертирует TTF в готовый C++ байт-массив! -->
               </tool_call>

            9. Сборка и компиляция проекта (Gradle/NDK/CMake или Maven):
               <tool_call name="build_project"></tool_call>

            ПРАВИЛА РАБОТЫ:
            - Если нужно найти где объявлен класс, метод или функция, используй <tool_call name="search_code">.
            - Если нужно прочитать файл, используй <tool_call name="read_file">.
            - Для небольших правок используй <tool_call name="apply_diff">. Для создания новых файлов используй <tool_call name="write_file">.
            - Если не уверен в синтаксисе сторонней библиотеки, NDK API или Gradle плагина, вызови <tool_call name="web_search">.
            - Чтобы прочитать подробную документацию или страницу по ссылке из поиска, вызови <tool_call name="web_fetch">.
            - Если нужно скачать шрифт (TTF/OTF) или файл в проект, используй <tool_call name="download_file">.
            - ВАЖНО: Если пользователь просит добавить фичу, шрифт или изменить логику, твоя задача НЕ ЗАВЕРШЕНА, пока ты РЕАЛЬНО не внесешь изменения в код (write_file / apply_diff / download_file). Никогда не останавливайся после одного чтения файлов или поиска без реального изменения файлов проекта!
            - Если пользователь просит собрать или проверить проект, или после внесения правок в код C/C++/Java/Kotlin, вызови <tool_call name="build_project"></tool_call>.
            - Если сборка вернет ошибки (FAILURE), внимательно изучи строки с ошибками компилятора Clang/Javac/Kotlinc, открой указанные файлы, исправь ошибки через apply_diff или write_file и ОБЯЗАТЕЛЬНО снова вызови build_project для подтверждения успешного исправления.
            - ВНИМАНИЕ: Используй ТОЛЬКО стандартный тег <tool_call name="...">...</tool_call>. НЕ используй спецсимволы DSML (<|DSML|>, invoke, calls, parameter). Обязательно закрывай каждый вызов тегом </tool_call>.
            - После выполнения всех операций напиши краткое, профессиональное и понятное резюме на русском языке без лишней воды.
        """.trimIndent()
        else """
            You are an autonomous developer AI assistant in the mobile IDE PrismDE (C/C++, Android NDK, CMake, Gradle Android/Java, Maven).
            Your goal is to help the developer implement features, fix bugs, or create new files in project "${project.name}".

            Project files:
            ${files.joinToString("\n") { "- $it" }}

            You have access to tools via XML tags:
            1. Read a project file:
               <tool_call name="read_file">
                 <path>relative/path/to/file</path>
               </tool_call>

            2. Search code in project (grep):
               <tool_call name="search_code">
                 <query>symbol_or_pattern_to_search</query>
               </tool_call>

            3. Apply targeted diff / block replacement:
               <tool_call name="apply_diff">
                 <path>relative/path/to/file</path>
                 <find>exact old code block</find>
                 <replace>new code block</replace>
               </tool_call>

            4. Write or create a project file:
               <tool_call name="write_file">
                 <path>relative/path/to/file</path>
                 <content>Complete full file content</content>
               </tool_call>

            5. List project files:
               <tool_call name="list_files"></tool_call>

            6. Search web for online documentation and libraries:
               <tool_call name="web_search">
                 <query>search query</query>
               </tool_call>

            7. Fetch web page or documentation content by URL:
               <tool_call name="web_fetch">
                 <url>https://example.com/docs/api</url>
               </tool_call>

            8. Download binary asset/font directly into project:
               <tool_call name="download_file">
                 <url>https://raw.githubusercontent.com/.../Inter.ttf</url>
                 <path>jni/ui/fonts/Inter_Regular.h</path> <!-- if .h extension, automatically converts TTF to C++ byte array! -->
               </tool_call>

            9. Build and compile project (Clang/NDK/CMake or Maven pom.xml):
               <tool_call name="build_project"></tool_call>

            RULES:
            - When looking for where symbols or methods are declared, use <tool_call name="search_code">.
            - When you need file contents, call <tool_call name="read_file">.
            - For small targeted edits, call <tool_call name="apply_diff">. For new files or major rewrites, call <tool_call name="write_file">.
            - If you need external library documentation, invoke <tool_call name="web_search">.
            - To download fonts or assets into project, invoke <tool_call name="download_file">.
            - CRITICAL: Do NOT finish or give up after only searching or reading files. You MUST actually modify the project files (write_file / apply_diff / download_file) to complete the user's request!
            - If user asks to build or compile the project, or after making changes to native or Java code, invoke <tool_call name="build_project"></tool_call>.
            - If the build fails with errors (FAILURE), carefully read the compiler diagnostics (file and line numbers), inspect the code, fix errors with apply_diff or write_file, and RE-TEST with build_project to verify the fix.
            - ATTENTION: Strictly use standard XML format: <tool_call name="...">...</tool_call>. Do NOT use DSML tokens (<|DSML|>, invoke, calls, parameter). Always close each tool call with </tool_call>.
            - When finished, provide a clear, concise summary of the changes made.

            RULES:
            - When looking for where symbols or methods are declared, use <tool_call name="search_code">.
            - When you need file contents, call <tool_call name="read_file">.
            - For small targeted edits, call <tool_call name="apply_diff">. For new files or major rewrites, call <tool_call name="write_file">.
            - If you need external library documentation, invoke <tool_call name="web_search">.
            - If user asks to build or compile the project, or after making changes to native or Java code, invoke <tool_call name="build_project"></tool_call>.
            - If the build fails with errors (FAILURE), carefully read the compiler diagnostics (file and line numbers), inspect the code, fix errors with apply_diff or write_file, and RE-TEST with build_project to verify the fix.
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
