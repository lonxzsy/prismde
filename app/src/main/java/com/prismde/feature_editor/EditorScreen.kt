package com.prismde.feature_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.feature_build.BuildViewModel
import com.prismde.feature_build.components.BuildBottomSheet
import com.prismde.feature_build.components.ExportSoDialog
import com.prismde.feature_files.components.ProjectPickerBottomSheet
import com.prismde.feature_settings.BuildPresetDialog
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    buildViewModel: BuildViewModel,
    activeNdk: NdkVersion,
    geminiApiKey: String,
    onSelectProject: (Project) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val editorState by editorViewModel.uiState.collectAsState()
    val buildState by buildViewModel.uiState.collectAsState()

    var codeEditorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var buildConfig by remember { mutableStateOf(BuildConfiguration()) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            EditorFloatingBar(
                isBuilding = buildState.isBuilding,
                onBuildClick = {
                    val project = editorState.currentProject
                    if (project != null) {
                        editorViewModel.saveActiveFile()
                        buildViewModel.startBuild(project, activeNdk, buildConfig)
                    }
                },
                onSaveClick = { editorViewModel.saveActiveFile() },
                onUndoClick = { codeEditorInstance?.undo() },
                onRedoClick = { codeEditorInstance?.redo() }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Bar with Project Switcher & Build Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showProjectPicker = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = editorState.currentProject?.name ?: "Выберите проект",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showPresetDialog = true }
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = "Параметры сборки",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tabs Bar
            EditorTabs(
                openFiles = editorState.openFiles,
                activeFile = editorState.activeFile,
                onSelectFile = { editorViewModel.openFile(it) },
                onCloseFile = { editorViewModel.closeFile(it) }
            )

            // Code Editor or Empty Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (editorState.activeFile == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Выберите файл из вкладки «Файлы» для редактирования",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    SoraEditorView(
                        file = editorState.activeFile,
                        content = editorState.activeContent,
                        onContentChanged = { newText ->
                            editorViewModel.updateContent(newText)
                        },
                        diagnostics = buildState.diagnostics,
                        targetJumpDiagnostic = editorState.targetJumpDiagnostic,
                        onEditorReady = { editor ->
                            codeEditorInstance = editor
                        }
                    )
                }
            }

            // Quick C/C++ Symbol Gutter Strip
            SymbolGutterBar(
                onInsertSymbol = { symbol ->
                    codeEditorInstance?.let { editor ->
                        val cursor = editor.cursor
                        editor.text.insert(cursor.leftLine, cursor.leftColumn, symbol)
                    }
                }
            )
        }
    }

    // Build Output BottomSheet
    BuildBottomSheet(
        isVisible = buildState.showBottomSheet,
        isBuilding = buildState.isBuilding,
        buildSuccess = buildState.buildSuccess,
        diagnostics = buildState.diagnostics,
        logs = buildState.logs,
        onDismiss = { buildViewModel.hideBottomSheet() },
        onJumpToCode = { diagnostic ->
            editorViewModel.jumpToDiagnostic(diagnostic)
        },
        onApplyFix = { diagnostic ->
            val fix = diagnostic.suggestedFix ?: return@BuildBottomSheet
            codeEditorInstance?.let { editor ->
                val line = (diagnostic.line - 1).coerceAtLeast(0)
                if (line < editor.lineCount) {
                    val col = (diagnostic.column - 1).coerceAtLeast(0)
                    editor.text.insert(line, col, fix)
                    editorViewModel.saveActiveFile()
                }
            }
        },
        onAskAi = { diagnostic ->
            val contextSnippet = editorState.activeContent.lines()
                .drop((diagnostic.line - 5).coerceAtLeast(0))
                .take(10)
                .joinToString("\n")
            buildViewModel.askAiExplanation(diagnostic, contextSnippet, geminiApiKey)
        },
        aiExplanations = buildState.aiExplanations,
        aiLoadingMap = buildState.aiLoadingMap
    )

    // Export .so Dialog
    if (buildState.showExportDialog && buildState.artifactFile != null) {
        ExportSoDialog(
            soFile = buildState.artifactFile!!,
            onDismiss = { buildViewModel.dismissExportDialog() }
        )
    }

    // Build Preset Dialog
    if (showPresetDialog) {
        BuildPresetDialog(
            initialConfig = buildConfig,
            onDismiss = { showPresetDialog = false },
            onSave = {
                buildConfig = it
                showPresetDialog = false
            }
        )
    }

    // Project Picker Bottom Sheet
    if (showProjectPicker) {
        ProjectPickerBottomSheet(
            currentProject = editorState.currentProject,
            onSelectProject = { project ->
                showProjectPicker = false
                onSelectProject(project)
            },
            onDismiss = { showProjectPicker = false }
        )
    }
}
