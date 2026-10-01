package com.prismde.feature_editor

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.prismde.core.model.Diagnostic
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
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
    var editorInstance: CodeEditor? = remember { null }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(14f)
                isLineNumberEnabled = true
                isWordwrap = false
                colorScheme = SchemeDarcula()

                subscribeAlways(ContentChangeEvent::class.java) {
                    onContentChanged(text.toString())
                }

                editorInstance = this
                onEditorReady(this)
            }
        },
        update = { editor ->
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
