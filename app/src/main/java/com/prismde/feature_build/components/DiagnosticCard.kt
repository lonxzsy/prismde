package com.prismde.feature_build.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onApplyAiFix: ((Diagnostic, String) -> Unit)? = null,
    onAskAi: ((Diagnostic) -> Unit)? = null,
    aiExplanation: String? = null,
    isAiLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Theme-aware palette with high contrast and readable typography
    val (containerColor, borderColor, iconColor, titleColor, textColor, icon) = when (diagnostic.severity) {
        DiagnosticSeverity.ERROR, DiagnosticSeverity.FATAL -> {
            if (isDark) {
                SixTuple(
                    Color(0xFF261214),
                    Color(0xFFE53935).copy(alpha = 0.5f),
                    Color(0xFFFF5252),
                    Color(0xFFFFB4AB),
                    Color(0xFFECE0DF),
                    Icons.Rounded.ErrorOutline
                )
            } else {
                SixTuple(
                    DiagnosticErrorContainer,
                    DiagnosticError.copy(alpha = 0.35f),
                    DiagnosticError,
                    DiagnosticOnErrorContainer,
                    DiagnosticOnErrorContainer.copy(alpha = 0.9f),
                    Icons.Rounded.ErrorOutline
                )
            }
        }
        DiagnosticSeverity.WARNING -> {
            if (isDark) {
                SixTuple(
                    Color(0xFF261D0A),
                    Color(0xFFFFA000).copy(alpha = 0.5f),
                    Color(0xFFFFB74D),
                    Color(0xFFFFDF99),
                    Color(0xFFEFE8DB),
                    Icons.Rounded.WarningAmber
                )
            } else {
                SixTuple(
                    DiagnosticWarningContainer,
                    DiagnosticWarning.copy(alpha = 0.35f),
                    DiagnosticWarning,
                    DiagnosticOnWarningContainer,
                    DiagnosticOnWarningContainer.copy(alpha = 0.9f),
                    Icons.Rounded.WarningAmber
                )
            }
        }
        DiagnosticSeverity.NOTE -> {
            if (isDark) {
                SixTuple(
                    Color(0xFF0F1E29),
                    Color(0xFF0288D1).copy(alpha = 0.5f),
                    Color(0xFF4FC3F7),
                    Color(0xFFB3E5FC),
                    Color(0xFFDEE5E8),
                    Icons.Rounded.Info
                )
            } else {
                SixTuple(
                    DiagnosticNoteContainer,
                    DiagnosticNote.copy(alpha = 0.35f),
                    DiagnosticNote,
                    DiagnosticOnNoteContainer,
                    DiagnosticOnNoteContainer.copy(alpha = 0.9f),
                    Icons.Rounded.Info
                )
            }
        }
    }

    var showAiResult by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onJumpToCode(diagnostic) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon + Title + Location Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = diagnostic.humanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))

                // Line:Col Badge
                Badge(
                    containerColor = (if (isDark) Color(0xFF1E1E24) else MaterialTheme.colorScheme.surface).copy(alpha = 0.8f),
                    contentColor = titleColor
                ) {
                    Text(
                        text = "${File(diagnostic.filePath).name}:${diagnostic.line}:${diagnostic.column}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Explanation in Russian
            Text(
                text = diagnostic.humanExplanation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp,
                    fontSize = 14.sp
                ),
                color = textColor
            )

            // Offline Suggestions / Fix Hints
            if (!diagnostic.offlineHint.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x28FFFFFF) else Color(0x18000000))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TipsAndUpdates,
                            contentDescription = null,
                            tint = titleColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Как исправить (оффлайн):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = diagnostic.offlineHint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            lineHeight = 18.sp,
                            fontSize = 12.5.sp
                        ),
                        color = textColor
                    )
                }
            }

            // Raw compiler output snippet (only if distinct and informative)
            val showRaw = diagnostic.rawMessage.isNotBlank() &&
                    !diagnostic.rawMessage.equals(diagnostic.humanExplanation, ignoreCase = true)
            if (showRaw) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Код ошибки: ${diagnostic.rawMessage}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = textColor.copy(alpha = 0.75f)
                )
            }

            // Action Buttons
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Jump to line and column button
                FilledTonalButton(
                    onClick = { onJumpToCode(diagnostic) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isDark) Color(0xFF2C2D35) else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "К строке ${diagnostic.line}:${diagnostic.column}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.width(8.dp))

                // AI Analysis Button
                OutlinedButton(
                    onClick = {
                        showAiResult = true
                        onAskAi?.invoke(diagnostic)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "AI разбор",
                        style = MaterialTheme.typography.labelMedium,
                        color = titleColor
                    )
                }
            }

            // Quick Fix-It button if present
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
                    Text("Вставить: \"${diagnostic.suggestedFix}\"")
                }
            }

            // AI Explanation result block
            AnimatedVisibility(visible = showAiResult && !aiExplanation.isNullOrBlank()) {
                val fullAiText = aiExplanation ?: ""
                val aiCodeFix = remember(fullAiText) { extractCodeFromAiResponse(fullAiText) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x33000000) else Color(0x15000000))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Разбор AI ассистента:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Formatted Markdown content (bold headers, inline code, code blocks)
                    FormattedMarkdownText(
                        markdown = fullAiText,
                        titleColor = titleColor,
                        textColor = textColor,
                        isDark = isDark
                    )

                    // Prominent Apply AI Fix Button
                    if (!aiCodeFix.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onApplyAiFix?.invoke(diagnostic, aiCodeFix) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFF6750A4) else MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Применить решение AI в код",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SixTuple<A, B, C, D, E, F>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F
)
