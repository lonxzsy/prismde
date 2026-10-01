package com.prismde.feature_editor

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.prismde.core.model.Diagnostic
import com.prismde.feature_editor.language.PrismCodeLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
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
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var currentAttachedPath by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(14f)
                isLineNumberEnabled = true
                isWordwrap = false

                // Modern Expressive dark theme (One Dark / Material Dark)
                val scheme = SchemeDarcula().apply {
                    setColor(EditorColorScheme.ANNOTATION, 0xFFE5C07B.toInt())       // Gold #include / directives
                    setColor(EditorColorScheme.KEYWORD, 0xFFC678DD.toInt())          // Purple keywords
                    setColor(EditorColorScheme.FUNCTION_NAME, 0xFF61AFEF.toInt())    // Soft blue functions
                    setColor(EditorColorScheme.LITERAL, 0xFF98C379.toInt())          // Fresh green strings/numbers
                    setColor(EditorColorScheme.COMMENT, 0xFF7F848E.toInt())          // Muted slate comments
                    setColor(EditorColorScheme.OPERATOR, 0xFF56B6C2.toInt())         // Cyan operators
                    setColor(EditorColorScheme.TEXT_NORMAL, 0xFFABB2BF.toInt())      // Clean text
                    setColor(EditorColorScheme.WHOLE_BACKGROUND, 0xFF1E1E2E.toInt()) // Deep Dark theme
                    setColor(EditorColorScheme.LINE_NUMBER, 0xFF5C6370.toInt())
                    setColor(EditorColorScheme.LINE_NUMBER_CURRENT, 0xFFE06C75.toInt())
                }
                colorScheme = scheme

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

    // Handle single-tap jump to line and column from compiler diagnostics!
    LaunchedEffect(targetJumpDiagnostic) {
        val target = targetJumpDiagnostic ?: return@LaunchedEffect
        editorInstance?.let { editor ->
            try {
                val targetLine = (target.line - 1).coerceAtLeast(0)
                val targetCol = (target.column - 1).coerceAtLeast(0)
                if (targetLine < editor.lineCount) {
                    val lineLength = editor.text.getColumnCount(targetLine)
                    val safeCol = targetCol.coerceIn(0, lineLength)
                    editor.setSelection(targetLine, safeCol)
                    editor.jumpToLine(targetLine)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
