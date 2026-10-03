package com.prismde.feature_build.engine

object TokenEstimator {

    /**
     * Fast, accurate token estimator matching cl100k / modern LLM tokenizers.
     * Takes into account ASCII words and non-ASCII (Cyrillic, CJK, code symbols).
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var nonAsciiCount = 0
        for (i in 0 until text.length) {
            if (text[i].code > 127) nonAsciiCount++
        }
        val asciiCount = text.length - nonAsciiCount
        val estimated = (asciiCount / 3.8) + (nonAsciiCount / 1.6) + 1.0
        return estimated.toInt().coerceAtLeast(1)
    }

    /**
     * Returns the context window limit in tokens for the specified model.
     */
    fun getModelContextLimit(modelName: String, provider: String = ""): Int {
        val lower = modelName.lowercase()
        return when {
            lower.contains("gpt-4o") || lower.contains("gpt-4-turbo") -> 128_000
            lower.contains("o1") || lower.contains("o3") -> 128_000
            lower.contains("deepseek") -> 128_000
            lower.contains("claude-3-5") || lower.contains("claude-3-7") || lower.contains("claude-3") -> 200_000
            lower.contains("gemini-1.5") || lower.contains("gemini-2.0") || lower.contains("gemini-3.8") -> 1_000_000
            lower.contains("gpt-3.5") -> 16_384
            lower.contains("qwen") || lower.contains("llama3") || lower.contains("mistral") -> 32_768
            provider == "gemini_api" || provider == "antigravity" -> 1_000_000
            else -> 128_000 // Default for modern models
        }
    }

    /**
     * Formats integer token count into human-friendly representation (e.g. 1.2k, 128k, 1.0M).
     */
    fun formatTokenCount(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> {
                val m = tokens / 1_000_000.0
                if (tokens % 1_000_000 == 0) "${tokens / 1_000_000}M"
                else String.format(java.util.Locale.US, "%.1fM", m)
            }
            tokens >= 1_000 -> {
                val k = tokens / 1_000.0
                if (tokens % 1_000 == 0) "${tokens / 1_000}k"
                else String.format(java.util.Locale.US, "%.1fk", k)
            }
            else -> tokens.toString()
        }
    }
}
