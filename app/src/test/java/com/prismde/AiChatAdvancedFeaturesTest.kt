package com.prismde

import com.prismde.feature_build.engine.AiAgentEngine
import com.prismde.feature_build.engine.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatAdvancedFeaturesTest {

    @Test
    fun testTokenEstimator() {
        val english = "Hello world! This is a simple test prompt for LLM."
        val tokens = TokenEstimator.estimateTokens(english)
        assertTrue("English tokens should be reasonable", tokens in 8..20)

        val russian = "Привет мир! Это тестовая строка на русском языке."
        val ruTokens = TokenEstimator.estimateTokens(russian)
        assertTrue("Russian tokens should account for multibyte chars", ruTokens in 15..35)

        assertEquals("128k", TokenEstimator.formatTokenCount(128_000))
        assertEquals("1M", TokenEstimator.formatTokenCount(1_000_000))
        assertEquals("1.5M", TokenEstimator.formatTokenCount(1_500_000))
        assertEquals("450", TokenEstimator.formatTokenCount(450))

        assertEquals(128_000, TokenEstimator.getModelContextLimit("gpt-4o"))
        assertEquals(128_000, TokenEstimator.getModelContextLimit("deepseek-v4.1-flash"))
        assertEquals(1_000_000, TokenEstimator.getModelContextLimit("gemini-1.5-pro"))
        assertEquals(200_000, TokenEstimator.getModelContextLimit("claude-3-5-sonnet"))
    }

    @Test
    fun testBuildProjectToolCallExtraction() {
        val engine = AiAgentEngine()

        val responseXml = """
I will now build the project to verify changes.
<tool_call name="build_project">
</tool_call>
        """.trimIndent()

        val calls = engine.extractToolCalls(responseXml)
        assertEquals(1, calls.size)
        assertEquals("build_project", calls[0].name)

        val selfClosingXml = """
<tool_call name="build_project"/>
        """.trimIndent()
        val scCalls = engine.extractToolCalls(selfClosingXml)
        assertEquals(1, scCalls.size)
        assertEquals("build_project", scCalls[0].name)
    }

    @Test
    fun testHistoryCompaction() {
        val engine = AiAgentEngine()
        val messages = mutableListOf<com.prismde.feature_build.engine.AgentChatMessage>()
        for (i in 1..20) {
            messages.add(
                com.prismde.feature_build.engine.AgentChatMessage(
                    id = "msg_$i",
                    isUser = i % 2 != 0,
                    text = "Message $i: " + "Some relatively long text to consume tokens. ".repeat(15)
                )
            )
        }

        // Test compaction with threshold 1000 tokens
        val compacted = engine.prepareHistoryWithCompaction(messages, maxModelTokens = 1200)
        assertTrue(compacted.size < messages.size)
        assertTrue(compacted.first().text.contains("[Context Compacted"))
    }
}
