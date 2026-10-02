package com.prismde.feature_editor

data class ReplacementPlan(
    val scanStartLine: Int, // Line to start reading animation from
    val startLine: Int,     // 0-indexed line in file to replace from
    val endLine: Int,       // 0-indexed line in file to replace to (inclusive)
    val replacementText: String
)

object AiDiffMatcher {

    /**
     * Analyzes file lines, target error line, and AI code snippet.
     * Computes the exact replacement slice by matching common context lines
     * and trimming identical prefixes and suffixes so that unchanged code is never duplicated.
     */
    fun computePlan(fileContent: String, targetLine: Int, rawAiCode: String): ReplacementPlan {
        val fileLines = fileContent.lines()
        val safeTarget = targetLine.coerceIn(0, (fileLines.size - 1).coerceAtLeast(0))
        val aiSnippet = rawAiCode.trimEnd()
        val aiLines = aiSnippet.lines()

        val scanStart = (safeTarget - 12).coerceAtLeast(0)

        if (aiLines.size <= 1) {
            return ReplacementPlan(
                scanStartLine = scanStart,
                startLine = safeTarget,
                endLine = safeTarget,
                replacementText = aiSnippet
            )
        }

        // Search for matching window in file around targetLine
        val searchStart = (safeTarget - aiLines.size - 10).coerceAtLeast(0)
        val searchEnd = (safeTarget + 10).coerceAtMost((fileLines.size - 1).coerceAtLeast(0))

        var bestStart = -1
        var bestScore = -1

        for (candidateStart in searchStart..searchEnd) {
            var score = 0
            val maxCompare = aiLines.size.coerceAtMost(fileLines.size - candidateStart)
            for (i in 0 until maxCompare) {
                val fileL = fileLines[candidateStart + i].trim()
                val aiL = aiLines[i].trim()
                if (fileL.isNotEmpty() && fileL == aiL) {
                    score += 2
                } else if (fileL.isNotEmpty() && aiL.isNotEmpty() && (fileL.contains(aiL) || aiL.contains(fileL))) {
                    score += 1
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestStart = candidateStart
            }
        }

        // If strong match found, trim identical prefix and suffix
        if (bestStart >= 0 && bestScore >= 2) {
            var matchStart = bestStart
            var matchEnd = (bestStart + aiLines.size - 1).coerceAtMost(fileLines.size - 1)
            var aiStart = 0
            var aiEnd = aiLines.size - 1

            // Trim identical prefix lines
            while (matchStart < matchEnd && aiStart < aiEnd &&
                fileLines[matchStart].trim() == aiLines[aiStart].trim() &&
                matchStart < safeTarget
            ) {
                matchStart++
                aiStart++
            }

            // Trim identical suffix lines
            while (matchEnd > matchStart && aiEnd > aiStart &&
                fileLines[matchEnd].trim() == aiLines[aiEnd].trim() &&
                matchEnd > safeTarget
            ) {
                matchEnd--
                aiEnd--
            }

            val effectiveAiLines = aiLines.subList(aiStart, aiEnd + 1)
            val effectiveReplacement = effectiveAiLines.joinToString("\n")

            return ReplacementPlan(
                scanStartLine = (bestStart - 5).coerceAtLeast(0),
                startLine = matchStart,
                endLine = matchEnd,
                replacementText = effectiveReplacement
            )
        }

        // Fallback: replace targetLine
        return ReplacementPlan(
            scanStartLine = scanStart,
            startLine = safeTarget,
            endLine = safeTarget,
            replacementText = aiSnippet
        )
    }
}
