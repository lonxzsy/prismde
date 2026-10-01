package com.prismde.feature_build.engine

import com.prismde.core.model.Diagnostic
import com.prismde.core.model.DiagnosticRange
import com.prismde.core.model.DiagnosticSeverity

class ClangDiagnosticParser {

    private val diagnosticRegex = Regex(
        """^(.+?):(\d+):(\d+):\s*(error|warning|fatal error|note):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val fixItRegex = Regex(
        """^fix-it:"(.+?)":\{(\d+):(\d+)-(\d+):(\d+)\}:"(.+)"$"""
    )

    private var lastDiagnostic: Diagnostic? = null

    fun parseLine(line: String): Diagnostic? {
        val trimmed = line.trim()

        // Check if line is a Clang fix-it hint for the preceding diagnostic
        val fixMatch = fixItRegex.find(trimmed)
        if (fixMatch != null) {
            val (file, sLine, sCol, eLine, eCol, fixReplacement) = fixMatch.destructured
            val last = lastDiagnostic
            if (last != null && last.filePath.endsWith(file)) {
                val updated = last.copy(
                    suggestedFix = fixReplacement,
                    fixRange = DiagnosticRange(
                        startLine = sLine.toIntOrNull() ?: 1,
                        startCol = sCol.toIntOrNull() ?: 1,
                        endLine = eLine.toIntOrNull() ?: 1,
                        endCol = eCol.toIntOrNull() ?: 1
                    )
                )
                lastDiagnostic = updated
                return updated
            }
        }

        // Standard Clang diagnostic line
        val match = diagnosticRegex.find(trimmed) ?: return null
        val (filePath, lineStr, colStr, severityStr, rawMsg) = match.destructured

        val severity = when (severityStr.lowercase()) {
            "error" -> DiagnosticSeverity.ERROR
            "fatal error" -> DiagnosticSeverity.FATAL
            "warning" -> DiagnosticSeverity.WARNING
            "note" -> DiagnosticSeverity.NOTE
            else -> DiagnosticSeverity.ERROR
        }

        val (title, explanation, defaultFix) = HumanExplanationEngine.explain(rawMsg)

        val diagnostic = Diagnostic(
            filePath = filePath,
            line = lineStr.toIntOrNull() ?: 1,
            column = colStr.toIntOrNull() ?: 1,
            severity = severity,
            rawMessage = rawMsg,
            humanTitle = title,
            humanExplanation = explanation,
            suggestedFix = defaultFix
        )

        lastDiagnostic = diagnostic
        return diagnostic
    }
}
