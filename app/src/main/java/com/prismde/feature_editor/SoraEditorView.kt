package com.prismde.feature_editor

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.prismde.core.model.Diagnostic
import com.prismde.feature_editor.language.PrismCodeLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub
import java.io.File

@Composable
fun SoraEditorView(
    file: File?,
    content: String,
    onContentChanged: (String) -> Unit,
    diagnostics: List<Diagnostic>,
    targetJumpDiagnostic: Diagnostic?,
    modifier: Modifier = Modifier,
    onEditorReady: (CodeEditor) -> Unit = {}
) {
    val m3ColorScheme = MaterialTheme.colorScheme
    val isDark = m3ColorScheme.surface.luminance() < 0.5f

    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var currentAttachedPath by remember { mutableStateOf<String?>(null) }
    var lastAppliedDark by remember { mutableStateOf<Boolean?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(14f)
                isLineNumberEnabled = true
                isWordwrap = false

                colorScheme = buildEditorColorScheme(m3ColorScheme, isDark)
                lastAppliedDark = isDark

                // Set initial language based on file extension
                setEditorLanguage(PrismCodeLanguage.forFile(file))
                currentAttachedPath = file?.absolutePath

                subscribeAlways(ContentChangeEvent::class.java) {
                    onContentChanged(text.toString())
                }

                editorInstance = this
                onEditorReady(this)
            }
        },
        update = { editor ->
            // Update color scheme if theme or luminance changed
            if (lastAppliedDark != isDark) {
                lastAppliedDark = isDark
                editor.colorScheme = buildEditorColorScheme(m3ColorScheme, isDark)
            }

            // Switch language if opened file changed
            if (file?.absolutePath != currentAttachedPath) {
                currentAttachedPath = file?.absolutePath
                editor.setEditorLanguage(PrismCodeLanguage.forFile(file))
            }

            // Only update text if different to avoid cursor resetting
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
        }
    )

    // Handle single-tap jump to exact line and column with glowing red pulse animation!
    LaunchedEffect(targetJumpDiagnostic) {
        val target = targetJumpDiagnostic ?: return@LaunchedEffect
        editorInstance?.let { editor ->
            try {
                val targetLine = (target.line - 1).coerceAtLeast(0)
                val targetCol = (target.column - 1).coerceAtLeast(0)
                if (targetLine < editor.lineCount) {
                    val lineLength = editor.text.getColumnCount(targetLine)
                    val safeCol = targetCol.coerceIn(0, lineLength)

                    // Position cursor or select character at targetCol
                    if (safeCol < lineLength) {
                        val endCol = (safeCol + 1).coerceAtMost(lineLength)
                        editor.setSelectionRegion(targetLine, safeCol, targetLine, endCol)
                    } else {
                        editor.setSelection(targetLine, safeCol)
                    }

                    // Jump vertically and ensure both row AND column are horizontally scrolled into view
                    editor.jumpToLine(targetLine)
                    editor.ensureSelectionVisible()
                    editor.requestFocus()

                    // Animate red blinking for ~2.5 seconds to clearly indicate the problem location
                    val originalCurrentLine = editor.colorScheme.getColor(EditorColorScheme.CURRENT_LINE)
                    val originalSelectedBg = editor.colorScheme.getColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND)
                    val pulseRedLine = android.graphics.Color.argb(160, 239, 83, 80) // Translucent glowing red
                    val pulseRedSelection = android.graphics.Color.argb(230, 244, 67, 54) // Bright red highlight

                    try {
                        repeat(5) {
                            editor.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, pulseRedLine)
                            editor.colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, pulseRedSelection)
                            editor.postInvalidate()
                            kotlinx.coroutines.delay(260)
                            editor.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, originalCurrentLine)
                            editor.colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, originalSelectedBg)
                            editor.postInvalidate()
                            kotlinx.coroutines.delay(200)
                        }
                    } finally {
                        editor.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, originalCurrentLine)
                        editor.colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, originalSelectedBg)
                        editor.postInvalidate()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private fun buildEditorColorScheme(colorScheme: ColorScheme, isDark: Boolean): EditorColorScheme {
    val base = if (isDark) SchemeDarcula() else SchemeGitHub()
    return base.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, colorScheme.surface.toArgb())
        setColor(EditorColorScheme.TEXT_NORMAL, colorScheme.onSurface.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, colorScheme.outline.copy(alpha = 0.65f).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colorScheme.primary.toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, colorScheme.outlineVariant.copy(alpha = 0.4f).toArgb())
        setColor(EditorColorScheme.CURRENT_LINE, colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.5f).toArgb())
        setColor(EditorColorScheme.SELECTION_INSERT, colorScheme.primary.toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colorScheme.primary.copy(alpha = 0.25f).toArgb())
        setColor(EditorColorScheme.TEXT_SELECTED, colorScheme.onPrimaryContainer.toArgb())

        // Syntax highlighting aligned with Material 3 tokens
        setColor(EditorColorScheme.KEYWORD, colorScheme.primary.toArgb())
        setColor(EditorColorScheme.ANNOTATION, colorScheme.tertiary.toArgb())
        setColor(EditorColorScheme.FUNCTION_NAME, colorScheme.secondary.toArgb())
        setColor(EditorColorScheme.COMMENT, colorScheme.outline.toArgb())
        setColor(EditorColorScheme.OPERATOR, colorScheme.onSurfaceVariant.toArgb())

        // Clear green for literals (strings, numbers)
        val literalColor = if (isDark) 0xFF81C784.toInt() else 0xFF2E7D32.toInt()
        setColor(EditorColorScheme.LITERAL, literalColor)

        // Diagnostic squiggles
        setColor(EditorColorScheme.PROBLEM_ERROR, colorScheme.error.toArgb())
        setColor(EditorColorScheme.PROBLEM_WARNING, colorScheme.tertiary.toArgb())
    }
}
