package com.prismde

import com.prismde.core.model.DiagnosticSeverity
import com.prismde.feature_build.engine.ClangDiagnosticParser
import com.prismde.feature_build.engine.HumanExplanationEngine
import io.github.rosemoe.sora.text.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClangDiagnosticParserTest {

    private val parser = ClangDiagnosticParser()

    @Test
    fun testParseClangError() {
        val line = "jni/native-lib.cpp:14:5: error: use of undeclared identifier 'count'"
        val diag = parser.parseLine(line)

        assertNotNull(diag)
        assertEquals("jni/native-lib.cpp", diag?.filePath)
        assertEquals(14, diag?.line)
        assertEquals(5, diag?.column)
        assertEquals(DiagnosticSeverity.ERROR, diag?.severity)
        assertTrue(diag?.humanTitle?.contains("Неизвестный идентификатор") == true)
        assertTrue(diag?.humanExplanation?.contains("count") == true)
    }

    @Test
    fun testParseMissingSemicolon() {
        val line = "main.cpp:20:12: error: expected ';' after expression"
        val diag = parser.parseLine(line)

        assertNotNull(diag)
        assertEquals("Пропущена точка с запятой (;)", diag?.humanTitle)
        assertEquals(";", diag?.suggestedFix)
    }

    @Test
    fun testParseWarning() {
        val line = "main.cpp:8:10: warning: unused variable 'y' [-Wunused-variable]"
        val diag = parser.parseLine(line)

        assertNotNull(diag)
        assertEquals(DiagnosticSeverity.WARNING, diag?.severity)
        assertEquals(8, diag?.line)
        assertEquals(10, diag?.column)
    }

    @Test
    fun testParseClangFixIt() {
        val errorLine = "main.cpp:15:3: error: use of undeclared identifier 'cutt'; did you mean 'cout'?"
        parser.parseLine(errorLine)

        val fixItLine = """fix-it:"main.cpp":{15:3-15:7}:"cout""""
        val updated = parser.parseLine(fixItLine)

        assertNotNull(updated)
        assertEquals("cout", updated?.suggestedFix)
        assertEquals(15, updated?.fixRange?.startLine)
        assertEquals(3, updated?.fixRange?.startCol)
    }

    @Test
    fun testNonDiagnosticLineReturnsNull() {
        val line = "Scanning dependencies of target myapp"
        val diag = parser.parseLine(line)
        assertNull(diag)
    }
}
