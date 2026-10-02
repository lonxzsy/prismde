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
    val modifiedFiles: Set<File> = emptySet(),
    val isModified: Boolean = false,
    val targetJumpDiagnostic: Diagnostic? = null
)

class EditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val fileContentCache = mutableMapOf<File, String>()
    private val fileSavedContent = mutableMapOf<File, String>()

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
        if (!fileSavedContent.containsKey(file)) {
            fileSavedContent[file] = content
        }

        val isFileModified = file in _uiState.value.modifiedFiles

        _uiState.value = _uiState.value.copy(
            openFiles = currentOpen,
            activeFile = file,
            activeContent = content,
            isModified = isFileModified
        )
    }

    fun closeFile(file: File) {
        val currentOpen = _uiState.value.openFiles.toMutableList()
        currentOpen.remove(file)
        fileContentCache.remove(file)
        fileSavedContent.remove(file)
        val updatedModified = _uiState.value.modifiedFiles - file

        val nextActive = if (file == _uiState.value.activeFile) {
            currentOpen.lastOrNull()
        } else {
            _uiState.value.activeFile
        }

        val nextContent = nextActive?.let { fileContentCache[it] ?: it.readText() } ?: ""
        val nextIsModified = nextActive != null && nextActive in updatedModified

        _uiState.value = _uiState.value.copy(
            openFiles = currentOpen,
            activeFile = nextActive,
            activeContent = nextContent,
            modifiedFiles = updatedModified,
            isModified = nextIsModified
        )
    }

    fun updateContent(newContent: String) {
        val file = _uiState.value.activeFile ?: return
        fileContentCache[file] = newContent
        val saved = fileSavedContent[file] ?: ""
        val hasChanged = newContent != saved

        val updatedModified = if (hasChanged) {
            _uiState.value.modifiedFiles + file
        } else {
            _uiState.value.modifiedFiles - file
        }

        _uiState.value = _uiState.value.copy(
            activeContent = newContent,
            modifiedFiles = updatedModified,
            isModified = hasChanged
        )
    }

    fun saveActiveFile() {
        val file = _uiState.value.activeFile ?: return
        val content = _uiState.value.activeContent
        viewModelScope.launch {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content)
                fileSavedContent[file] = content
                val updatedModified = _uiState.value.modifiedFiles - file
                _uiState.value = _uiState.value.copy(
                    modifiedFiles = updatedModified,
                    isModified = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun jumpToDiagnostic(diagnostic: Diagnostic) {
        val targetFile = resolveDiagnosticFile(diagnostic.filePath, _uiState.value.currentProject)
        if (targetFile != null && targetFile.exists() && targetFile != _uiState.value.activeFile) {
            openFile(targetFile)
        }
        _uiState.value = _uiState.value.copy(targetJumpDiagnostic = diagnostic)
    }

    fun clearJump() {
        _uiState.value = _uiState.value.copy(targetJumpDiagnostic = null)
    }
}

/**
 * Robustly resolves a diagnostic file path (which can be absolute, relative to rootDir,
 * relative to jniDir, or simply a filename in the project) to an existing File.
 */
fun resolveDiagnosticFile(filePath: String, project: Project?): File? {
    if (filePath.isBlank()) return null
    val direct = File(filePath)
    if (direct.isAbsolute && direct.exists() && direct.isFile) {
        return direct
    }
    if (project != null) {
        // 1. Try relative to project rootDir
        val relRoot = File(project.rootDir, filePath).normalize()
        if (relRoot.exists() && relRoot.isFile) return relRoot

        // 2. Try relative to jniDir
        val relJni = File(project.jniDir, filePath).normalize()
        if (relJni.exists() && relJni.isFile) return relJni

        // 3. Try stripped ./ or ../
        val cleanRel = filePath.removePrefix("./").removePrefix("../")
        val cleanInRoot = File(project.rootDir, cleanRel).normalize()
        if (cleanInRoot.exists() && cleanInRoot.isFile) return cleanInRoot

        val cleanInJni = File(project.jniDir, cleanRel).normalize()
        if (cleanInJni.exists() && cleanInJni.isFile) return cleanInJni

        // 4. Search by exact filename or suffix match in project directory
        val targetName = direct.name
        val found = project.rootDir.walkTopDown().maxDepth(6).firstOrNull {
            it.isFile && (it.name == targetName || it.absolutePath.endsWith(cleanRel))
        }
        if (found != null) return found
    }
    return if (direct.exists() && direct.isFile) direct else null
}

/**
 * Returns the exact file and its text content for a compiler diagnostic.
 * If the file is currently active in the editor, activeContent is returned to include live unsaved edits.
 * Otherwise, the file is read directly from disk.
 */
fun getFileContentForDiagnostic(
    diagnostic: Diagnostic,
    project: Project?,
    activeFile: File?,
    activeContent: String
): Pair<File?, String> {
    val resolvedFile = resolveDiagnosticFile(diagnostic.filePath, project)
    val content = when {
        resolvedFile != null && activeFile != null && resolvedFile.canonicalPath == activeFile.canonicalPath -> {
            activeContent
        }
        resolvedFile != null && resolvedFile.exists() && resolvedFile.isFile -> {
            try {
                resolvedFile.readText()
            } catch (_: Exception) {
                activeContent
            }
        }
        else -> activeContent
    }
    return Pair(resolvedFile, content)
}

