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
import com.prismde.feature_build.engine.AiConfig
import com.prismde.feature_build.components.ExportSoDialog
import com.prismde.feature_files.components.ProjectPickerBottomSheet
import com.prismde.feature_settings.BuildPresetDialog
import com.prismde.feature_editor.language.PrismCodeLanguage
import io.github.rosemoe.sora.widget.CodeEditor

data class PendingAiDiff(
    val originalText: String,
    val startLine: Int,
    val endLine: Int
)

@Composable
fun EditorScreen(
    editorViewModel: EditorViewModel,
    buildViewModel: BuildViewModel,
    activeNdk: NdkVersion,
    aiConfig: AiConfig,
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
    var pendingAiDiff by remember { mutableStateOf<PendingAiDiff?>(null) }

    LaunchedEffect(editorState.activeFile) {
        pendingAiDiff = null
    }

    Scaffold(
        floatingActionButton = {
            EditorFloatingBar(
                isModified = editorState.isModified,
                onSaveClick = { editorViewModel.saveActiveFile() },
                onUndoClick = { codeEditorInstance?.undo() },
                onRedoClick = { codeEditorInstance?.redo() },
                hasPendingAiDiff = pendingAiDiff != null,
                onAcceptChanges = {
                    pendingAiDiff = null
                    codeEditorInstance?.let { editor ->
                        editor.setSelection(editor.cursor.leftLine, editor.cursor.leftColumn)
                        val lang = editor.editorLanguage
                        if (lang is PrismCodeLanguage) {
                            lang.diffGreenRange = null
                            editor.rerunAnalysis()
                            editor.postInvalidate()
                        }
                    }
                    editorViewModel.saveActiveFile()
                },
                onDiscardChanges = {
                    val diff = pendingAiDiff
                    if (diff != null) {
                        codeEditorInstance?.let { editor ->
                            editor.setText(diff.originalText)
                            editor.setSelection(editor.cursor.leftLine, editor.cursor.leftColumn)
                            val lang = editor.editorLanguage
                            if (lang is PrismCodeLanguage) {
                                lang.diffGreenRange = null
                                editor.rerunAnalysis()
                                editor.postInvalidate()
                            }
                        }
                        editorViewModel.updateContent(diff.originalText)
                        editorViewModel.saveActiveFile()
                    }
                    pendingAiDiff = null
                }
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
                        diffGreenRange = pendingAiDiff?.let { it.startLine..it.endLine },
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
            val targetFile = resolveDiagnosticFile(diagnostic.filePath, editorState.currentProject)
            if (targetFile != null && targetFile != editorState.activeFile) {
                editorViewModel.openFile(targetFile)
            }
            editorViewModel.jumpToDiagnostic(diagnostic)
        },
        onApplyFix = { diagnostic ->
            val fix = diagnostic.suggestedFix ?: return@BuildBottomSheet
            val targetFile = resolveDiagnosticFile(diagnostic.filePath, editorState.currentProject)
            if (targetFile != null && targetFile != editorState.activeFile) {
                editorViewModel.openFile(targetFile)
            }
            codeEditorInstance?.let { editor ->
                val line = (diagnostic.line - 1).coerceAtLeast(0)
                if (line < editor.lineCount) {
                    val col = (diagnostic.column - 1).coerceAtLeast(0)
                    editor.text.insert(line, col, fix)
                    editorViewModel.saveActiveFile()
                }
            }
        },
        onApplyAiFix = { diagnostic ->
            val targetFile = resolveDiagnosticFile(diagnostic.filePath, editorState.currentProject)
            buildViewModel.hideBottomSheet()
            if (targetFile != null && targetFile != editorState.activeFile) {
                editorViewModel.openFile(targetFile)
            }
            editorViewModel.jumpToDiagnostic(diagnostic)
            coroutineScope.launch {
                if (targetFile != null && targetFile.canonicalPath != editorState.activeFile?.canonicalPath) {
                    editorViewModel.openFile(targetFile)
                    kotlinx.coroutines.delay(250)
                }

                val editor = codeEditorInstance ?: return@launch
                val originalText = editor.text.toString()
                val targetLine = (diagnostic.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))

                val serviceLabel = if (aiConfig.provider == "antigravity") "Antigravity (${aiConfig.model})" else "Gemini API (${aiConfig.model})"
                aiApplyingMessage = "AI ($serviceLabel) генерирует исправление..."

                val allLines = originalText.lines()
                val contextSnippet = if (allLines.size <= 300) {
                    originalText
                } else {
                    val start = (diagnostic.line - 40).coerceAtLeast(0)
                    allLines.drop(start).take(80).joinToString("\n")
                }

                val fixResult = buildViewModel.generateAiFix(diagnostic, contextSnippet, aiConfig)
                val replacementCode = fixResult.getOrNull()

                if (replacementCode.isNullOrBlank()) {
                    val error = fixResult.exceptionOrNull()?.message ?: "Не удалось сгенерировать код исправления"
                    aiApplyingMessage = "Ошибка: $error"
                    kotlinx.coroutines.delay(3500)
                    aiApplyingMessage = null
                    return@launch
                }

                // 1. Calculate intelligent diff plan (smart matching context, prefix/suffix trimming)
                val plan = AiDiffMatcher.computePlan(originalText, targetLine, replacementCode)

                // 2. Stylish scanning animation: sweep down from plan.scanStartLine to plan.startLine
                aiApplyingMessage = "AI анализирует контекст строки..."
                val scanStart = plan.scanStartLine.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                val scanEnd = plan.startLine.coerceIn(scanStart, (editor.lineCount - 1).coerceAtLeast(0))

                for (scanLine in scanStart..scanEnd) {
                    if (scanLine < editor.lineCount) {
                        val colCount = editor.text.getColumnCount(scanLine)
                        editor.setSelectionRegion(scanLine, 0, scanLine, colCount)
                        editor.jumpToLine(scanLine)
                        editor.ensureSelectionVisible()
                        kotlinx.coroutines.delay(35)
                    }
                }

                aiApplyingMessage = "AI применяет изменения..."
                kotlinx.coroutines.delay(100)

                // 3. Clear the exact slice to replace
                val safeStart = plan.startLine.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                val safeEnd = plan.endLine.coerceIn(safeStart, (editor.lineCount - 1).coerceAtLeast(0))
                val endLineLen = editor.text.getColumnCount(safeEnd)

                editor.text.delete(safeStart, 0, safeEnd, endLineLen)
                editor.setSelection(safeStart, 0)
                editor.jumpToLine(safeStart)
                editor.ensureSelectionVisible()

                // 4. Stream-type replacement character-by-character in real time!
                val cleanedReplacement = plan.replacementText.trimEnd()
                var curLine = safeStart
                var curCol = 0

                for (char in cleanedReplacement) {
                    val charStr = char.toString()
                    editor.text.insert(curLine, curCol, charStr)
                    if (char == '\n') {
                        curLine++
                        curCol = 0
                    } else {
                        curCol++
                    }
                    editor.setSelection(curLine, curCol)
                    editor.ensureSelectionVisible()
                    kotlinx.coroutines.delay(14)
                }

                editor.jumpToLine(curLine)
                editor.ensureSelectionVisible()

                // 5. Diff green highlighting and Pending state
                val newEndLine = curLine
                val diffRange = safeStart..newEndLine
                pendingAiDiff = PendingAiDiff(
                    originalText = originalText,
                    startLine = safeStart,
                    endLine = newEndLine
                )

                val lang = editor.editorLanguage
                if (lang is PrismCodeLanguage) {
                    lang.diffGreenRange = diffRange
                    editor.rerunAnalysis()
                    editor.postInvalidate()
                }

                editorViewModel.updateContent(editor.text.toString())

                aiApplyingMessage = "✔ Изменения внесены. Проверьте и примите или отклоните."
                kotlinx.coroutines.delay(3000)
                aiApplyingMessage = null
            }
        },
        onAskAi = { diagnostic ->
            val (_, fileText) = getFileContentForDiagnostic(
                diagnostic = diagnostic,
                project = editorState.currentProject,
                activeFile = editorState.activeFile,
                activeContent = editorState.activeContent
            )
            val allLines = fileText.lines()
            val contextSnippet = if (allLines.size <= 300) {
                fileText
            } else {
                val start = (diagnostic.line - 40).coerceAtLeast(0)
                allLines.drop(start).take(80).joinToString("\n")
            }
            buildViewModel.askAiExplanation(diagnostic, contextSnippet, aiConfig)
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
