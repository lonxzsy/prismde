package com.prismde.feature_editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class EditorUiState(
    val currentProject: Project? = null,
    val openFiles: List<File> = emptyList(),
    val activeFile: File? = null,
    val activeContent: String = "",
    val isModified: Boolean = false,
    val targetJumpDiagnostic: Diagnostic? = null
)

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val fileContentCache = mutableMapOf<File, String>()

    fun setProject(project: Project) {
        _uiState.value = _uiState.value.copy(currentProject = project)
    }

    fun openFile(file: File) {
        val currentOpen = _uiState.value.openFiles.toMutableList()
        if (file !in currentOpen) {
            currentOpen.add(file)
        }

        val content = fileContentCache[file] ?: try {
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            "// Ошибка чтения файла: ${e.message}"
        }
        fileContentCache[file] = content

        _uiState.value = _uiState.value.copy(
            openFiles = currentOpen,
            activeFile = file,
            activeContent = content,
            isModified = false
        )
    }

    fun closeFile(file: File) {
        val currentOpen = _uiState.value.openFiles.toMutableList()
        currentOpen.remove(file)
        fileContentCache.remove(file)

        val nextActive = if (file == _uiState.value.activeFile) {
            currentOpen.lastOrNull()
        } else {
            _uiState.value.activeFile
        }

        val nextContent = nextActive?.let { fileContentCache[it] ?: it.readText() } ?: ""

        _uiState.value = _uiState.value.copy(
            openFiles = currentOpen,
            activeFile = nextActive,
            activeContent = nextContent,
            isModified = false
        )
    }

    fun updateContent(newContent: String) {
        val file = _uiState.value.activeFile ?: return
        fileContentCache[file] = newContent
        _uiState.value = _uiState.value.copy(
            activeContent = newContent,
            isModified = true
        )
    }

    fun saveActiveFile() {
        val file = _uiState.value.activeFile ?: return
        val content = _uiState.value.activeContent
        viewModelScope.launch {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content)
                _uiState.value = _uiState.value.copy(isModified = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun jumpToDiagnostic(diagnostic: Diagnostic) {
        val targetFile = File(diagnostic.filePath)
        if (targetFile.exists() && targetFile != _uiState.value.activeFile) {
            openFile(targetFile)
        }
        _uiState.value = _uiState.value.copy(targetJumpDiagnostic = diagnostic)
    }

    fun clearJump() {
        _uiState.value = _uiState.value.copy(targetJumpDiagnostic = null)
    }
}
