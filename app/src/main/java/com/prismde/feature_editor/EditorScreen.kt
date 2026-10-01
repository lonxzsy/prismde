package com.prismde.feature_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    var aiApplyingMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            EditorFloatingBar(
                isModified = editorState.isModified,
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
            // Top Bar with Project Switcher & Build Actions
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Top Bar "Собрать" Button
                    Button(
                        onClick = {
                            val project = editorState.currentProject
                            if (project != null) {
                                editorViewModel.saveActiveFile()
                                buildViewModel.startBuild(project, activeNdk, buildConfig)
                            }
                        },
                        enabled = !buildState.isBuilding,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (buildState.isBuilding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Сборка...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Собрать",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Собрать",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
            }

            // Tabs Bar
            EditorTabs(
                openFiles = editorState.openFiles,
                activeFile = editorState.activeFile,
                modifiedFiles = editorState.modifiedFiles,
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

                    // Floating animated banner indicating the exact line & symbol
                    val jumpTarget = editorState.targetJumpDiagnostic
                    if (jumpTarget != null) {
                        JumpDiagnosticBanner(
                            jumpTarget = jumpTarget,
                            onDismiss = { editorViewModel.clearJump() },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    // Floating banner when AI is applying code changes in real time
                    val currentAiMsg = aiApplyingMessage
                    if (currentAiMsg != null) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = currentAiMsg,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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
            buildViewModel.hideBottomSheet()
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
        onApplyAiFix = { diagnostic, replacementCode ->
            buildViewModel.hideBottomSheet()
            editorViewModel.jumpToDiagnostic(diagnostic)
            coroutineScope.launch {
                val targetFile = java.io.File(diagnostic.filePath)
                if (targetFile.exists() && targetFile != editorState.activeFile) {
                    editorViewModel.openFile(targetFile)
                    kotlinx.coroutines.delay(250)
                }

                val editor = codeEditorInstance ?: return@launch
                val targetLine = (diagnostic.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                val lineLen = editor.text.getColumnCount(targetLine)

                // 1. Position cursor on target line & ensure visible
                editor.jumpToLine(targetLine)
                editor.setSelection(targetLine, lineLen)
                editor.ensureSelectionVisible()
                editor.requestFocus()

                // 2. Real-time "Thinking.." display right on the line
                aiApplyingMessage = "AI обдумывает исправление..."
                val thinkingBase = " // 💭 Thinking"
                editor.text.insert(targetLine, lineLen, "$thinkingBase.")
                editor.setSelection(targetLine, editor.text.getColumnCount(targetLine))
                editor.ensureSelectionVisible()

                kotlinx.coroutines.delay(300)
                var curLen = editor.text.getColumnCount(targetLine)
                editor.text.replace(targetLine, lineLen, targetLine, curLen, "$thinkingBase..")
                editor.setSelection(targetLine, editor.text.getColumnCount(targetLine))

                kotlinx.coroutines.delay(350)
                curLen = editor.text.getColumnCount(targetLine)
                editor.text.replace(targetLine, lineLen, targetLine, curLen, "$thinkingBase...")
                editor.setSelection(targetLine, editor.text.getColumnCount(targetLine))

                kotlinx.coroutines.delay(450)

                // 3. Clear line and stream-type replacement character-by-character in real time!
                val currentLineTotal = editor.text.getColumnCount(targetLine)
                editor.text.delete(targetLine, 0, targetLine, currentLineTotal)
                editor.setSelection(targetLine, 0)

                for (i in replacementCode.indices) {
                    val charStr = replacementCode[i].toString()
                    val col = editor.text.getColumnCount(targetLine)
                    editor.text.insert(targetLine, col, charStr)
                    editor.setSelection(targetLine, col + 1)
                    if (i % 3 == 0) {
                        editor.ensureSelectionVisible()
                    }
                    kotlinx.coroutines.delay(20)
                }

                editor.ensureSelectionVisible()

                // 4. Save and finish
                editorViewModel.updateContent(editor.text.toString())
                editorViewModel.saveActiveFile()

                aiApplyingMessage = "✔ Изменения AI успешно внесены!"
                kotlinx.coroutines.delay(2500)
                aiApplyingMessage = null
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

@Composable
private fun JumpDiagnosticBanner(
    jumpTarget: com.prismde.core.model.Diagnostic,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember(jumpTarget) { mutableStateOf(true) }

    LaunchedEffect(jumpTarget) {
        kotlinx.coroutines.delay(4000)
        isVisible = false
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier.padding(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Строка ${jumpTarget.line}, символ ${jumpTarget.column}: ${jumpTarget.humanTitle}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Закрыть",
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isVisible = false
                            onDismiss()
                        }
                )
            }
        }
    }
}
