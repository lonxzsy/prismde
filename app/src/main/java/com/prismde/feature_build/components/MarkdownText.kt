package com.prismde.feature_build.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattedMarkdownText(
    markdown: String,
    titleColor: Color,
    textColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdownBlocks(markdown)

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF141418) else Color(0xFFF1F1F4))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                is MarkdownBlock.TextBlock -> {
                    val annotated = buildAnnotatedMarkdown(block.text, titleColor, textColor, isDark)
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        color = textColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val lang: String?) : MarkdownBlock()
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeFenceRegex = Regex("""```(\w*)\n?([\s\S]*?)```""")

    var lastIndex = 0
    codeFenceRegex.findAll(markdown).forEach { match ->
        val textBefore = markdown.substring(lastIndex, match.range.first)
        if (textBefore.trim().isNotEmpty()) {
            blocks.add(MarkdownBlock.TextBlock(textBefore.trim()))
        }
        val lang = match.groupValues[1].ifBlank { null }
        val code = match.groupValues[2].trimEnd()
        blocks.add(MarkdownBlock.CodeBlock(code, lang))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < markdown.length) {
        val remaining = markdown.substring(lastIndex)
        if (remaining.trim().isNotEmpty()) {
            blocks.add(MarkdownBlock.TextBlock(remaining.trim()))
        }
    }

    return blocks
}

fun buildAnnotatedMarkdown(
    text: String,
    titleColor: Color,
    textColor: Color,
    isDark: Boolean
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            // Check for bold **...**
            if (cursor + 1 < len && text[cursor] == '*' && text[cursor + 1] == '*') {
                val end = text.indexOf("**", cursor + 2)
                if (end != -1) {
                    val boldContent = text.substring(cursor + 2, end)
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )
                    )
                    append(boldContent)
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Check for inline code `...`
            if (text[cursor] == '`') {
                val end = text.indexOf('`', cursor + 1)
                if (end != -1) {
                    val codeContent = text.substring(cursor + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF80DEEA) else Color(0xFF006064),
                            background = if (isDark) Color(0x3300E5FF) else Color(0x1F00ACC1)
                        )
                    )
                    append(" $codeContent ")
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Check for italic *...*
            if (text[cursor] == '*' && (cursor + 1 < len && text[cursor + 1] != '*')) {
                val end = text.indexOf('*', cursor + 1)
                if (end != -1) {
                    val italicContent = text.substring(cursor + 1, end)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicContent)
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Regular char
            append(text[cursor])
            cursor++
        }
    }
}

/**
 * Extracts candidate replacement code from AI text (from ```code``` blocks or `code` spans).
 */
fun extractCodeFromAiResponse(aiExplanation: String?): String? {
    if (aiExplanation.isNullOrBlank()) return null

    // Look for ```code``` block first
    val codeBlockRegex = Regex("""```(?:\w+)?\s*\n?([\s\S]*?)```""")
    val blockMatch = codeBlockRegex.find(aiExplanation)
    if (blockMatch != null) {
        val code = blockMatch.groupValues[1].trim()
        if (code.isNotEmpty()) return code
    }

    // Look for `#include ...` or other standard single-line instructions
    val includeRegex = Regex("""(#include\s+[<"][^>"]+[>"])""")
    val includeMatch = includeRegex.find(aiExplanation)
    if (includeMatch != null) {
        return includeMatch.groupValues[1]
    }

    return null
}
