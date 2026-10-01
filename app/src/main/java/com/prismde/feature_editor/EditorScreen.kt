package com.prismde.feature_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.NdkVersion
import com.prismde.feature_build.BuildViewModel
import com.prismde.feature_build.components.BuildBottomSheet
import com.prismde.feature_build.components.ExportSoDialog
import com.prismde.feature_settings.BuildPresetDialog
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    buildViewModel: BuildViewModel,
    activeNdk: NdkVersion,
    geminiApiKey: String,
    modifier: Modifier = Modifier
) {
    val editorState by editorViewModel.uiState.collectAsState()
    val buildState by buildViewModel.uiState.collectAsState()

    var codeEditorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var buildConfig by remember { mutableStateOf(BuildConfiguration()) }
    var showPresetDialog by remember { mutableStateOf(false) }

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
                        onContentChanged = { editorViewModel.updateContent(it) },
                        diagnostics = buildState.diagnostics,
                        targetJumpDiagnostic = editorState.targetJumpDiagnostic,
                        onEditorReady = { codeEditorInstance = it }
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

    // Build & Diagnostics Bottom Sheet
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
}
