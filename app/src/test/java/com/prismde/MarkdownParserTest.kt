package com.prismde

import androidx.compose.ui.graphics.Color
import com.prismde.feature_editor.components.MarkdownBlock
import com.prismde.feature_editor.components.buildMarkdownAnnotatedString
import com.prismde.feature_editor.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testParseMarkdownBlocksFromUserScreenshot() {
        val markdown = """
The file is now correctly written with all 131 lines intact. The task is complete.

## Summary

I added **4 tabs** with a variety of common UI components to `jni/ui/menu.cpp`. The `menu.h` header required no changes since the public `Draw()` signature is unchanged.

### Changes Made

**`jni/ui/menu.cpp`** — Added module state variables and wrapped the window contents in a tab bar:

- **Visuals tab**: `Enable ESP` checkbox, draw-option checkboxes (`Boxes`, `Names`, `Health`), and a `Max Distance` slider.
- **Aim tab**: `Enable Aim` / `Silent` / `Visible Only` checkboxes, `Smooth` and `FOV` sliders, plus a `Reset Aim` button.
- **Misc tab**: `No Recoil` / `No Spread` / `Fast Reload` checkboxes, a `Combo` for speed mode, and a speed multiplier slider.
- **About tab**: `TextWrapped` description, an `InputText` field, a demo checkbox, and an `Unload` button.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)

        // Verify block types
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.Header && (blocks[1] as MarkdownBlock.Header).level == 2)
        assertEquals("Summary", (blocks[1] as MarkdownBlock.Header).text)

        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
        assertTrue(blocks[3] is MarkdownBlock.Header && (blocks[3] as MarkdownBlock.Header).level == 3)
        assertEquals("Changes Made", (blocks[3] as MarkdownBlock.Header).text)

        assertTrue(blocks[4] is MarkdownBlock.Paragraph)

        // Bullet items
        val bulletItems = blocks.filterIsInstance<MarkdownBlock.BulletItem>()
        assertEquals(4, bulletItems.size)
        assertTrue(bulletItems[0].text.startsWith("**Visuals tab**: `Enable ESP`"))
        assertTrue(bulletItems[1].text.startsWith("**Aim tab**: `Enable Aim`"))
        assertTrue(bulletItems[2].text.startsWith("**Misc tab**: `No Recoil`"))
        assertTrue(bulletItems[3].text.startsWith("**About tab**: `TextWrapped`"))
    }

    @Test
    fun testCodeBlockAndDividerParsing() {
        val markdown = """
Here is the code:

```cpp
#include <jni.h>

void Draw() {
    // UI
}
```

---

> Done!
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.CodeBlock)
        val codeBlock = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("cpp", codeBlock.language)
        assertTrue(codeBlock.code.contains("void Draw()"))

        assertTrue(blocks[2] is MarkdownBlock.Divider)
        assertTrue(blocks[3] is MarkdownBlock.Blockquote)
        assertEquals("Done!", (blocks[3] as MarkdownBlock.Blockquote).text)
    }

    @Test
    fun testBuildMarkdownAnnotatedString() {
        val text = "**`jni/ui/menu.cpp`** — Added module state variables and **4 tabs** with `code`"
        val annotated = buildMarkdownAnnotatedString(
            text = text,
            primaryColor = Color.Blue,
            codeBgColor = Color.Gray,
            codeTextColor = Color.Black,
            linkColor = Color.Cyan
        )

        // Ensure text is extracted cleanly without remaining markdown syntax delimiters
        assertTrue(annotated.text.contains("jni/ui/menu.cpp"))
        assertTrue(annotated.text.contains("4 tabs"))
        assertTrue(annotated.text.contains("code"))
        assertTrue(!annotated.text.contains("**"))
        assertTrue(!annotated.text.contains("`"))
        assertTrue(annotated.spanStyles.isNotEmpty())
    }
}
