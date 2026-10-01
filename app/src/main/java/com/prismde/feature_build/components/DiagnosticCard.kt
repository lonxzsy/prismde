package com.prismde.feature_build.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.DiagnosticSeverity
import com.prismde.core.theme.DiagnosticError
import com.prismde.core.theme.DiagnosticErrorContainer
import com.prismde.core.theme.DiagnosticNote
import com.prismde.core.theme.DiagnosticNoteContainer
import com.prismde.core.theme.DiagnosticOnErrorContainer
import com.prismde.core.theme.DiagnosticOnNoteContainer
import com.prismde.core.theme.DiagnosticOnWarningContainer
import com.prismde.core.theme.DiagnosticWarning
import com.prismde.core.theme.DiagnosticWarningContainer
import java.io.File

@Composable
fun DiagnosticCard(
    diagnostic: Diagnostic,
    onJumpToCode: (Diagnostic) -> Unit,
    onApplyFix: ((Diagnostic) -> Unit)? = null,
    onAskAi: ((Diagnostic) -> Unit)? = null,
    aiExplanation: String? = null,
    isAiLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor, icon) = when (diagnostic.severity) {
        DiagnosticSeverity.ERROR, DiagnosticSeverity.FATAL -> Triple(
            DiagnosticErrorContainer,
            DiagnosticOnErrorContainer,
            Icons.Rounded.ErrorOutline
        )
        DiagnosticSeverity.WARNING -> Triple(
            DiagnosticWarningContainer,
            DiagnosticOnWarningContainer,
            Icons.Rounded.WarningAmber
        )
        DiagnosticSeverity.NOTE -> Triple(
            DiagnosticNoteContainer,
            DiagnosticOnNoteContainer,
            Icons.Rounded.Info
        )
    }

    var showAiResult by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onJumpToCode(diagnostic) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = diagnostic.humanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Line:Col Badge
                Badge(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    contentColor = contentColor
                ) {
                    Text(
                        text = "${File(diagnostic.filePath).name}:${diagnostic.line}:${diagnostic.column}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Human Explanation in Russian
            Text(
                text = diagnostic.humanExplanation,
                style = MaterialTheme.typography.bodyMedium
            )

            // Raw compiler output snippet
            Spacer(Modifier.height(6.dp))
            Text(
                text = diagnostic.rawMessage,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f)
            )

            // Actions row
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Jump to line in code button
                FilledTonalButton(
                    onClick = { onJumpToCode(diagnostic) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("К кодовой строке")
                }

                Spacer(Modifier.width(8.dp))

                // Optional AI Analysis Button
                OutlinedButton(
                    onClick = {
                        showAiResult = true
                        onAskAi?.invoke(diagnostic)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("AI разбор")
                }
            }

            // Quick Clang Fix-It button if present
            if (!diagnostic.suggestedFix.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { onApplyFix?.invoke(diagnostic) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Применить исправление: \"${diagnostic.suggestedFix}\"")
                }
            }

            // AI Explanation block
            AnimatedVisibility(visible = showAiResult && !aiExplanation.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Ответ AI ассистента:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = aiExplanation ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
