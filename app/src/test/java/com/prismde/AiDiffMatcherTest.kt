package com.prismde

import com.prismde.feature_editor.AiDiffMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class AiDiffMatcherTest {

    @Test
    fun testTrimsIdenticalPrefixAndSuffix() {
        val file = """
            void calculate() {
                int x = 10;
                int y = 20;
                int total = x + ;
                return total;
            }
        """.trimIndent()

        // AI generated surrounding function but only fixed line 3
        val aiSnippet = """
            void calculate() {
                int x = 10;
                int y = 20;
                int total = x + y;
                return total;
            }
        """.trimIndent()

        // Error is at line 3 ("int total = x + ;")
        val plan = AiDiffMatcher.computePlan(file, targetLine = 3, rawAiCode = aiSnippet)

        // It should match and replace ONLY line 3
        assertEquals(3, plan.startLine)
        assertEquals(3, plan.endLine)
        assertEquals("    int total = x + y;", plan.replacementText)
    }

    @Test
    fun testSingleLineReplacement() {
        val file = """
            #include <pthread.h>
            #include <unistd.h
            int main() {}
        """.trimIndent()

        val aiSnippet = """#include <unistd.h>"""

        val plan = AiDiffMatcher.computePlan(file, targetLine = 1, rawAiCode = aiSnippet)

        assertEquals(1, plan.startLine)
        assertEquals(1, plan.endLine)
        assertEquals("""#include <unistd.h>""", plan.replacementText)
    }
}
