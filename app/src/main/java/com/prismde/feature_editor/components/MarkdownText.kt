package com.prismde.feature_editor.components

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletItem(val text: String, val indentLevel: Int = 0) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String, val indentLevel: Int = 0) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

/**
 * Parses markdown text into structural blocks.
 */
fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    val totalLines = lines.size

    val paragraphAccumulator = StringBuilder()

    fun flushParagraph() {
        if (paragraphAccumulator.isNotBlank()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphAccumulator.toString().trim()))
            paragraphAccumulator.clear()
        }
    }

    while (i < totalLines) {
        val line = lines[i]
        val trimmedLine = line.trim()

        // 1. Code blocks: ```language ... ```
        if (trimmedLine.startsWith("```")) {
            flushParagraph()
            val language = trimmedLine.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < totalLines && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            i++
            continue
        }

        // 2. Horizontal divider: --- or *** or ___
        if (trimmedLine == "---" || trimmedLine == "***" || trimmedLine == "___") {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headers: #, ##, ###, ####
        if (trimmedLine.startsWith("#")) {
            flushParagraph()
            val hashCount = trimmedLine.takeWhile { it == '#' }.length
            val headerText = trimmedLine.substring(hashCount).trim()
            blocks.add(MarkdownBlock.Header(hashCount.coerceIn(1, 4), headerText))
            i++
            continue
        }

        // 4. Blockquote: > text
        if (trimmedLine.startsWith(">")) {
            flushParagraph()
            val quoteText = trimmedLine.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        // 5. Bullet list items: - , * , +
        if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("+ ")) {
            flushParagraph()
            val indent = (line.length - line.trimStart().length) / 2
            val bulletText = trimmedLine.substring(2).trim()
            blocks.add(MarkdownBlock.BulletItem(bulletText, indent))
            i++
            continue
        }

        // 6. Numbered list items: 1. , 2. etc.
        val numberedMatch = Regex("""^(\d+)\.\s+(.*)$""").find(trimmedLine)
        if (numberedMatch != null) {
            flushParagraph()
            val num = numberedMatch.groupValues[1]
            val itemText = numberedMatch.groupValues[2].trim()
            val indent = (line.length - line.trimStart().length) / 2
            blocks.add(MarkdownBlock.NumberedItem(num, itemText, indent))
            i++
            continue
        }

        // 7. Empty line separates paragraphs
        if (trimmedLine.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        // 8. Normal paragraph text accumulation
        if (paragraphAccumulator.isNotEmpty()) {
            paragraphAccumulator.append("\n")
        }
        paragraphAccumulator.append(line)
        i++
    }

    flushParagraph()
    return blocks
}

/**
 * Recursively parses inline markdown (bold, italic, inline code, links) into an AnnotatedString.
 */
fun buildMarkdownAnnotatedString(
    text: String,
    primaryColor: Color,
    codeBgColor: Color,
    codeTextColor: Color,
    linkColor: Color,
    isBoldParent: Boolean = false,
    isItalicParent: Boolean = false
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val len = text.length

    while (i < len) {
        // 1. Bold: **...** or __...__
        if ((text.startsWith("**", i) || text.startsWith("__", i)) && i + 2 < len) {
            val marker = text.substring(i, i + 2)
            val endIdx = text.indexOf(marker, i + 2)
            if (endIdx != -1) {
                val innerText = text.substring(i + 2, endIdx)
                val innerAnnotated = buildMarkdownAnnotatedString(
                    text = innerText,
                    primaryColor = primaryColor,
                    codeBgColor = codeBgColor,
                    codeTextColor = codeTextColor,
                    linkColor = linkColor,
                    isBoldParent = true,
                    isItalicParent = isItalicParent
                )
                builder.append(innerAnnotated)
                i = endIdx + 2
                continue
            }
        }

        // 2. Inline Code: `...`
        if (text[i] == '`' && !text.startsWith("```", i)) {
            val endIdx = text.indexOf('`', i + 1)
            if (endIdx != -1) {
                val codeContent = text.substring(i + 1, endIdx)
                val startSpan = builder.length
                builder.append(" $codeContent ")
                val endSpan = builder.length
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        fontWeight = if (isBoldParent) FontWeight.Bold else FontWeight.Medium,
                        fontStyle = if (isItalicParent) FontStyle.Italic else FontStyle.Normal,
                        background = codeBgColor,
                        color = codeTextColor
                    ),
                    startSpan,
                    endSpan
                )
                i = endIdx + 1
                continue
            }
        }

        // 3. Italic: *...* or _..._
        if ((text[i] == '*' || text[i] == '_') && i + 1 < len && !text.startsWith("**", i) && !text.startsWith("__", i)) {
            val marker = text[i]
            val endIdx = text.indexOf(marker, i + 1)
            if (endIdx != -1 && endIdx > i + 1) {
                val innerText = text.substring(i + 1, endIdx)
                val innerAnnotated = buildMarkdownAnnotatedString(
                    text = innerText,
                    primaryColor = primaryColor,
                    codeBgColor = codeBgColor,
                    codeTextColor = codeTextColor,
                    linkColor = linkColor,
                    isBoldParent = isBoldParent,
                    isItalicParent = true
                )
                builder.append(innerAnnotated)
                i = endIdx + 1
                continue
            }
        }

        // 4. Link: [label](url)
        if (text[i] == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val label = text.substring(i + 1, closeBracket)
                    val startSpan = builder.length
                    builder.append(label)
                    val endSpan = builder.length
                    builder.addStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = if (isBoldParent) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = TextDecoration.Underline
                        ),
                        startSpan,
                        endSpan
                    )
                    i = closeParen + 1
                    continue
                }
            }
        }

        // 5. Plain character
        val startChar = builder.length
        builder.append(text[i])
        val endChar = builder.length
        if (isBoldParent || isItalicParent) {
            builder.addStyle(
                SpanStyle(
                    fontWeight = if (isBoldParent) FontWeight.Bold else null,
                    fontStyle = if (isItalicParent) FontStyle.Italic else null
                ),
                startChar,
                endChar
            )
        }
        i++
    }

    return builder.toAnnotatedString()
}

/**
 * Animated cursor indicating that the AI is actively outputting text.
 */
@Composable
fun BlinkingCursor(color: Color = MaterialTheme.colorScheme.primary) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Text(
        text = "▌",
        color = color.copy(alpha = alpha),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 2.dp)
    )
}

/**
 * MarkdownText renders AI responses with syntax styling, headers, code blocks, lists, and inline tags.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    isStreaming: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex

            when (block) {
                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                        3 -> MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                        else -> MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val annotatedHeader = remember(block.text) {
                        buildMarkdownAnnotatedString(block.text, primaryColor, codeBgColor, codeTextColor, linkColor)
                    }
                    Row(
                        modifier = Modifier.padding(top = if (index > 0) 6.dp else 0.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = annotatedHeader,
                            style = headerStyle,
                            color = if (block.level <= 2) MaterialTheme.colorScheme.primary else textColor
                        )
                        if (isLastBlock && isStreaming) {
                            BlinkingCursor()
                        }
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    val annotatedText = remember(block.text) {
                        buildMarkdownAnnotatedString(block.text, primaryColor, codeBgColor, codeTextColor, linkColor)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLastBlock && isStreaming) {
                            BlinkingCursor()
                        }
                    }
                }

                is MarkdownBlock.BulletItem -> {
                    val annotatedItem = remember(block.text) {
                        buildMarkdownAnnotatedString(block.text, primaryColor, codeBgColor, codeTextColor, linkColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12 + 4).dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = annotatedItem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLastBlock && isStreaming) {
                            BlinkingCursor()
                        }
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    val annotatedItem = remember(block.text) {
                        buildMarkdownAnnotatedString(block.text, primaryColor, codeBgColor, codeTextColor, linkColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indentLevel * 12 + 4).dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = annotatedItem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLastBlock && isStreaming) {
                            BlinkingCursor()
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    val annotatedQuote = remember(block.text) {
                        buildMarkdownAnnotatedString(block.text, primaryColor, codeBgColor, codeTextColor, linkColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = annotatedQuote,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // If streaming but no blocks yet
        if (blocks.isEmpty() && isStreaming) {
            BlinkingCursor()
        }
    }
}

/**
 * Beautiful styled card for fenced code blocks with language indicator and copy button.
 */
@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar: language label + copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Code content with horizontal scroll
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
