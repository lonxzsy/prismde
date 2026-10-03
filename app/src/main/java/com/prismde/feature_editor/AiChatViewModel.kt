package com.prismde.feature_editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prismde.core.model.Project
import com.prismde.feature_build.engine.AgentChatMessage
import com.prismde.feature_build.engine.AiAgentAction
import com.prismde.feature_build.engine.AiAgentEngine
import com.prismde.feature_build.engine.AiConfig
import com.prismde.feature_build.engine.AttachedFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class AiChatUiState(
    val messages: List<AgentChatMessage> = emptyList(),
    val attachedFiles: List<AttachedFile> = emptyList(),
    val isRunning: Boolean = false,
    val currentActivity: String? = null,
    val errorMessage: String? = null
)

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AiAgentEngine()
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    fun attachFile(file: File, rootDir: File) {
        if (!file.exists() || !file.isFile) return
        val relPath = file.absolutePath.removePrefix(rootDir.absolutePath).trimStart('/', '\\')
        val current = _uiState.value.attachedFiles
        if (current.none { it.relativePath == relPath }) {
            _uiState.value = _uiState.value.copy(
                attachedFiles = current + AttachedFile(file, relPath, file.length())
            )
        }
    }

    fun removeAttachedFile(relativePath: String) {
        _uiState.value = _uiState.value.copy(
            attachedFiles = _uiState.value.attachedFiles.filterNot { it.relativePath == relativePath }
        )
    }

    fun clearAttachedFiles() {
        _uiState.value = _uiState.value.copy(attachedFiles = emptyList())
    }

    fun clearChat() {
        activeJob?.cancel()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            isRunning = false,
            currentActivity = null,
            errorMessage = null
        )
    }

    fun sendMessage(
        prompt: String,
        project: Project,
        config: AiConfig,
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

        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val emptyAssistantMsg = AgentChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "",
            actions = emptyList()
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg + emptyAssistantMsg,
            isRunning = true,
            currentActivity = null,
            errorMessage = null
        )

        activeJob = viewModelScope.launch {
            val isRu = java.util.Locale.getDefault().language == "ru"
            val actionsAcc = mutableListOf<AiAgentAction>()

            val result = engine.executeTask(
                userPrompt = trimmed,
                project = project,
                attachedFiles = attached,
                config = config,
                conversationHistory = _uiState.value.messages
            ) { action ->
                actionsAcc.add(action)

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
                    is AiAgentAction.FinalAnswer -> null
                    is AiAgentAction.Error -> action.message
                }

                // Update assistant message with live actions list
                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(actions = actionsAcc.toList())
                    } else msg
                }

                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    currentActivity = statusText
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
                                    actions = actionsAcc.toList()
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
                            actions = actionsAcc.toList()
                        )
                    } else msg
                }
                _uiState.value = _uiState.value.copy(
                    messages = finalMessages,
                    isRunning = false,
                    currentActivity = null
                )
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
