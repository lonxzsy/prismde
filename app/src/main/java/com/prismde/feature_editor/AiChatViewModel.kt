package com.prismde.feature_editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.feature_build.engine.AgentChatMessage
import com.prismde.feature_build.engine.AiAgentAction
import com.prismde.feature_build.engine.AiAgentEngine
import com.prismde.feature_build.engine.AiConfig
import com.prismde.feature_build.engine.AttachedFile
import com.prismde.feature_build.engine.TokenEstimator
import com.prismde.feature_editor.model.ChatSession
import com.prismde.feature_editor.storage.AiChatStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class AiChatUiState(
    val messages: List<AgentChatMessage> = emptyList(),
    val attachedFiles: List<AttachedFile> = emptyList(),
    val isRunning: Boolean = false,
    val currentActivity: String? = null,
    val errorMessage: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String = "",
    val estimatedTokensUsed: Int = 0,
    val modelMaxTokens: Int = 128_000
)

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AiAgentEngine()
    private val storage = AiChatStorage(application)
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var currentProjectName: String = ""

    /**
     * Loads chat sessions for the active project.
     */
    fun loadSessionsForProject(project: Project?, config: AiConfig) {
        if (project == null) return
        val projName = project.name
        if (projName == currentProjectName && _uiState.value.sessions.isNotEmpty()) {
            return
        }
        currentProjectName = projName

        viewModelScope.launch {
            val sessions = storage.loadSessions(projName)
            val maxTokens = TokenEstimator.getModelContextLimit(config.model, config.provider)

            if (sessions.isEmpty()) {
                val newSession = ChatSession(
                    id = UUID.randomUUID().toString(),
                    title = "New Chat",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    messages = emptyList()
                )
                _uiState.value = _uiState.value.copy(
                    sessions = listOf(newSession),
                    currentSessionId = newSession.id,
                    messages = emptyList(),
                    modelMaxTokens = maxTokens,
                    estimatedTokensUsed = 0
                )
                storage.saveSessions(projName, listOf(newSession))
            } else {
                val active = sessions.first()
                val used = calculateTokens(active.messages, _uiState.value.attachedFiles)
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    currentSessionId = active.id,
                    messages = active.messages,
                    modelMaxTokens = maxTokens,
                    estimatedTokensUsed = used
                )
            }
        }
    }

    fun switchSession(sessionId: String, project: Project) {
        if (_uiState.value.isRunning) return
        val currentSessions = _uiState.value.sessions
        val target = currentSessions.firstOrNull { it.id == sessionId } ?: return

        // Persist current session state before switching
        persistCurrentSession(project)

        val used = calculateTokens(target.messages, _uiState.value.attachedFiles)
        _uiState.value = _uiState.value.copy(
            currentSessionId = target.id,
            messages = target.messages,
            estimatedTokensUsed = used,
            errorMessage = null,
            currentActivity = null
        )
    }

    fun createNewSession(project: Project) {
        if (_uiState.value.isRunning) return
        persistCurrentSession(project)

        val newSession = ChatSession(
            id = UUID.randomUUID().toString(),
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            messages = emptyList()
        )

        val updatedSessions = listOf(newSession) + _uiState.value.sessions
        _uiState.value = _uiState.value.copy(
            sessions = updatedSessions,
            currentSessionId = newSession.id,
            messages = emptyList(),
            estimatedTokensUsed = calculateTokens(emptyList(), _uiState.value.attachedFiles),
            errorMessage = null,
            currentActivity = null
        )

        viewModelScope.launch {
            storage.saveSessions(project.name, updatedSessions)
        }
    }

    fun deleteSession(sessionId: String, project: Project) {
        if (_uiState.value.isRunning) return
        val remaining = _uiState.value.sessions.filterNot { it.id == sessionId }

        val nextSession = if (remaining.isNotEmpty()) {
            if (_uiState.value.currentSessionId == sessionId) remaining.first()
            else remaining.firstOrNull { it.id == _uiState.value.currentSessionId } ?: remaining.first()
        } else {
            ChatSession(
                id = UUID.randomUUID().toString(),
                title = "New Chat",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = emptyList()
            )
        }

        val finalSessions = if (remaining.isEmpty()) listOf(nextSession) else remaining
        val used = calculateTokens(nextSession.messages, _uiState.value.attachedFiles)

        _uiState.value = _uiState.value.copy(
            sessions = finalSessions,
            currentSessionId = nextSession.id,
            messages = nextSession.messages,
            estimatedTokensUsed = used
        )

        viewModelScope.launch {
            storage.saveSessions(project.name, finalSessions)
        }
    }

    fun compactCurrentSession(project: Project) {
        if (_uiState.value.isRunning) return
        val messages = _uiState.value.messages
        if (messages.size <= 2) return

        val compacted = engine.prepareHistoryWithCompaction(
            conversationHistory = messages,
            maxModelTokens = 1000 // Force compaction on all but recent turns
        )

        _uiState.value = _uiState.value.copy(
            messages = compacted,
            estimatedTokensUsed = calculateTokens(compacted, _uiState.value.attachedFiles)
        )
        persistCurrentSession(project)
    }

    private fun persistCurrentSession(project: Project) {
        val currId = _uiState.value.currentSessionId
        val currMessages = _uiState.value.messages
        if (currId.isBlank()) return

        val title = generateSessionTitle(currMessages)
        val updatedSessions = _uiState.value.sessions.map { s ->
            if (s.id == currId) {
                s.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis(),
                    messages = currMessages
                )
            } else s
        }

        _uiState.value = _uiState.value.copy(sessions = updatedSessions)
        viewModelScope.launch {
            storage.saveSessions(project.name, updatedSessions)
        }
    }

    private fun generateSessionTitle(messages: List<AgentChatMessage>): String {
        val firstUserMsg = messages.firstOrNull { it.isUser }?.text?.trim()
        return if (!firstUserMsg.isNullOrBlank()) {
            val singleLine = firstUserMsg.lines().first().take(36)
            if (singleLine.length >= 36) "$singleLine..." else singleLine
        } else {
            "New Chat"
        }
    }

    private fun calculateTokens(messages: List<AgentChatMessage>, attached: List<AttachedFile>, project: Project? = null): Int {
        val lastServerMsg = messages.lastOrNull { !it.isUser && it.serverTokens > 0 }
        if (lastServerMsg != null && lastServerMsg.serverTokens > 0) {
            val subsequent = messages.takeLastWhile { it != lastServerMsg }
            var delta = 0
            for (m in subsequent) {
                delta += TokenEstimator.estimateTokens(m.text)
            }
            return lastServerMsg.serverTokens + delta
        }

        var tokens = 750 // Base system prompt
        if (project != null && project.rootDir.exists()) {
            val projFiles = engine.listProjectRelativeFiles(project.rootDir)
            for (f in projFiles) {
                tokens += (f.length / 3).coerceAtLeast(1)
            }
            val skills = com.prismde.feature_build.engine.ProjectSkillManager.loadProjectSkills(project.rootDir)
            for (s in skills) {
                tokens += TokenEstimator.estimateTokens(s.content)
            }
        }

        for (att in attached) {
            tokens += (att.sizeBytes / 3.5).toInt().coerceAtLeast(1)
        }

        for (m in messages) {
            tokens += TokenEstimator.estimateTokens(m.text)
            if (!m.thinkingText.isNullOrBlank()) {
                tokens += TokenEstimator.estimateTokens(m.thinkingText)
            }
            for (action in m.actions) {
                when (action) {
                    is AiAgentAction.ReadFile -> tokens += (action.lineCount * 6)
                    is AiAgentAction.WriteFile -> tokens += TokenEstimator.estimateTokens(action.content)
                    is AiAgentAction.BuildProject -> tokens += 150
                    is AiAgentAction.Thinking -> if (action.thoughtDetail != null) tokens += TokenEstimator.estimateTokens(action.thoughtDetail)
                    else -> {}
                }
            }
        }
        return tokens
    }

    fun attachFile(file: File, rootDir: File) {
        if (!file.exists() || !file.isFile) return
        val relPath = file.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/', '\\')
        val current = _uiState.value.attachedFiles
        if (current.none { it.relativePath == relPath }) {
            val updated = current + AttachedFile(file, relPath, file.length())
            _uiState.value = _uiState.value.copy(
                attachedFiles = updated,
                estimatedTokensUsed = calculateTokens(_uiState.value.messages, updated)
            )
        }
    }

    fun removeAttachedFile(relativePath: String) {
        val updated = _uiState.value.attachedFiles.filterNot { it.relativePath == relativePath }
        _uiState.value = _uiState.value.copy(
            attachedFiles = updated,
            estimatedTokensUsed = calculateTokens(_uiState.value.messages, updated)
        )
    }

    fun clearAttachedFiles() {
        _uiState.value = _uiState.value.copy(
            attachedFiles = emptyList(),
            estimatedTokensUsed = calculateTokens(_uiState.value.messages, emptyList())
        )
    }

    fun clearChat(project: Project? = null) {
        activeJob?.cancel()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            isRunning = false,
            currentActivity = null,
            errorMessage = null,
            estimatedTokensUsed = calculateTokens(emptyList(), _uiState.value.attachedFiles, project)
        )
        if (project != null) {
            persistCurrentSession(project)
        }
    }

    fun sendMessage(
        prompt: String,
        project: Project,
        config: AiConfig,
        ndk: NdkVersion? = null,
        buildConfig: BuildConfiguration? = null,
        onFileModified: (File, String) -> Unit = { _, _ -> }
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.isRunning) return

        val attached = _uiState.value.attachedFiles
        val userMsg = AgentChatMessage(
            isUser = true,
            text = trimmed,
            attachedFiles = attached.map { it.relativePath }
        )

        val assistantMsgId = UUID.randomUUID().toString()
        val emptyAssistantMsg = AgentChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            actions = emptyList()
        )

        val currentMessages = _uiState.value.messages + userMsg + emptyAssistantMsg
        val maxTokens = TokenEstimator.getModelContextLimit(config.model, config.provider)

        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            isRunning = true,
            currentActivity = null,
            errorMessage = null,
            modelMaxTokens = maxTokens,
            estimatedTokensUsed = calculateTokens(currentMessages, attached, project)
        )

        activeJob = viewModelScope.launch {
            val isRu = java.util.Locale.getDefault().language == "ru"
            val actionsAcc = mutableListOf<AiAgentAction>()
            var currentThinkingText: String? = null
            var lastServerTokens = 0

            val result = engine.executeTask(
                userPrompt = trimmed,
                project = project,
                attachedFiles = attached,
                config = config,
                conversationHistory = _uiState.value.messages,
                ndk = ndk,
                buildConfig = buildConfig
            ) { action ->
                actionsAcc.add(action)

                when (action) {
                    is AiAgentAction.Thinking -> {
                        if (action.thoughtDetail != null) {
                            currentThinkingText = action.thoughtDetail
                        }
                    }
                    is AiAgentAction.TokenUsageUpdated -> {
                        lastServerTokens = action.totalTokens
                    }
                    else -> {}
                }

                // Real-time status update for UI
                val statusText = when (action) {
                    is AiAgentAction.Thinking -> action.message
                    is AiAgentAction.ReadFile -> if (isRu) "Чтение файла: ${action.relativePath} (${action.lineCount} строк)..." else "Reading file: ${action.relativePath} (${action.lineCount} lines)..."
                    is AiAgentAction.WriteFile -> {
                        val diff = if (action.isNew) "+${action.linesAdded}" else "+${action.linesAdded}, -${action.linesRemoved}"
                        val targetFile = File(project.rootDir, action.relativePath)
                        onFileModified(targetFile, action.content)
                        if (isRu) "Изменен: ${action.relativePath} ($diff строк)..." else "Modified: ${action.relativePath} ($diff lines)..."
                    }
                    is AiAgentAction.ListFiles -> if (isRu) "Анализ файлов проекта (${action.fileCount} файлов)..." else "Scanning project structure (${action.fileCount} files)..."
                    is AiAgentAction.BuildProject -> if (isRu) "Сборка проекта: ${action.status}..." else "Building project: ${action.status}..."
                    is AiAgentAction.ContextCompacted -> if (isRu) "Контекст сжат (~${action.savedTokens} токенов)..." else "Context compacted (~${action.savedTokens} tokens)..."
                    is AiAgentAction.TokenUsageUpdated -> null
                    is AiAgentAction.FinalAnswer -> null
                    is AiAgentAction.Error -> action.message
                }

                // Update assistant message with live actions list, thinking and tokens
                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            actions = actionsAcc.toList(),
                            thinkingText = currentThinkingText,
                            serverTokens = lastServerTokens
                        )
                    } else msg
                }

                val currentUsed = if (lastServerTokens > 0) lastServerTokens else calculateTokens(updatedMessages, attached, project)
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    currentActivity = statusText,
                    estimatedTokensUsed = currentUsed
                )
            }

            result.onSuccess { finalExplanation ->
                val fullText = finalExplanation.trim()
                if (fullText.isNotEmpty()) {
                    // Smooth progressive typing animation (~1 - 1.4 seconds)
                    val targetDurationMs = 1200L
                    val delayStepMs = 16L
                    val totalSteps = (targetDurationMs / delayStepMs).toInt().coerceAtLeast(1)
                    val stepSize = (fullText.length / totalSteps).coerceAtLeast(2)

                    var currentLen = 0
                    while (currentLen < fullText.length) {
                        currentLen = minOf(fullText.length, currentLen + stepSize)
                        val partial = fullText.substring(0, currentLen)
                        val streamingMessages = _uiState.value.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    text = partial,
                                    actions = actionsAcc.toList(),
                                    thinkingText = currentThinkingText,
                                    serverTokens = lastServerTokens
                                )
                            } else msg
                        }
                        _uiState.value = _uiState.value.copy(
                            messages = streamingMessages,
                            currentActivity = null
                        )
                        kotlinx.coroutines.delay(delayStepMs)
                    }
                }

                val finalMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = fullText,
                            actions = actionsAcc.toList(),
                            thinkingText = currentThinkingText,
                            serverTokens = lastServerTokens
                        )
                    } else msg
                }
                val finalUsed = if (lastServerTokens > 0) lastServerTokens else calculateTokens(finalMessages, attached, project)
                _uiState.value = _uiState.value.copy(
                    messages = finalMessages,
                    isRunning = false,
                    currentActivity = null,
                    estimatedTokensUsed = finalUsed
                )

                // Save session state to disk
                persistCurrentSession(project)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    currentActivity = null,
                    errorMessage = err.message
                )
            }
        }
    }

    fun cancelTask() {
        activeJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isRunning = false,
            currentActivity = null
        )
    }
}
