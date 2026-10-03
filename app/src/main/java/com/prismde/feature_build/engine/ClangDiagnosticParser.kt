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

    private val mavenBracketRegex = Regex(
        """^\[(ERROR|WARNING|INFO)\]\s*(.+?):\[(\d+),(\d+)\]\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val mavenColonRegex = Regex(
        """^\[(ERROR|WARNING|INFO)\]\s*(.+?):(\d+):\s*(error|warning|note):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val kotlinRegex = Regex(
        """^(e|w):\s*(.+?):(?:\s*\()?(\d+)[,:]\s*(\d+)(?:\))?:\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val standardJavacRegex = Regex(
        """^(.+?\.(?:java|kt|groovy|xml)):(\d+):\s*(error|warning):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val aaptRegex = Regex(
        """^(.+?\.(?:xml|png|webp)):(\d+):\s*AAPT:\s*error:\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )


    private var lastDiagnostic: Diagnostic? = null

    fun parseLine(line: String): Diagnostic? {
        val trimmed = line.trim()
        // Handle Gradle wrapper missing Java / JAVA_HOME error
        if (trimmed.contains("JAVA_HOME is not set", ignoreCase = true) ||
            trimmed.contains("no 'java' command could be found", ignoreCase = true)) {
            val isRu = java.util.Locale.getDefault().language == "ru"
            val diagnostic = Diagnostic(
                filePath = "gradlew",
                line = 1,
                column = 1,
                severity = DiagnosticSeverity.ERROR,
                rawMessage = trimmed,
                humanTitle = if (isRu) "JAVA_HOME не установлен (JDK не найден)" else "JAVA_HOME is not set (JDK not found)",
                humanExplanation = if (isRu)
                    "Для запуска Gradle (./gradlew) на Android требуется установленный JDK (Java). Переменная JAVA_HOME не задана, а команда 'java' отсутствует в PATH."
                else
                    "Gradle wrapper (./gradlew) requires Java (JDK) to run. JAVA_HOME is not set and no 'java' command was found in PATH.",
                offlineHint = if (isRu)
                    "• Если вам нужна только нативная C/C++ библиотека (.so): откройте параметры сборки (значок рядом с Build) и выберите «JNI .so библиотека (Android.mk)».\n• Для сборки полного APK через Gradle: установите Termux и выполните команду 'pkg install openjdk-17'."
                else
                    "• To build the native C/C++ library (.so): open Build Settings and select 'JNI .so Library (Android.mk)'.\n• To build full APK with Gradle: install Termux and run 'pkg install openjdk-17'."
            )
            lastDiagnostic = diagnostic
            return diagnostic
        }

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

        // Maven javac format 1: [ERROR] /path/to/File.java:[15,8] cannot find symbol
        val mavenBracketMatch = mavenBracketRegex.find(trimmed)
        if (mavenBracketMatch != null) {
            val (sev, filePath, lineStr, colStr, rawMsg) = mavenBracketMatch.destructured
            val severity = if (sev.equals("ERROR", ignoreCase = true)) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
            val result = HumanExplanationEngine.explain(rawMsg, severity)
            val diagnostic = Diagnostic(
                filePath = filePath.trim(),
                line = lineStr.toIntOrNull() ?: 1,
                column = colStr.toIntOrNull() ?: 1,
                severity = severity,
                rawMessage = rawMsg.trim(),
                humanTitle = result.title,
                humanExplanation = result.explanation,
                offlineHint = result.offlineHint,
                suggestedFix = result.suggestedFix
            )
            lastDiagnostic = diagnostic
            return diagnostic
        }

        // Maven javac format 2: [ERROR] /path/to/File.java:15: error: cannot find symbol
        val mavenColonMatch = mavenColonRegex.find(trimmed)
        if (mavenColonMatch != null) {
            val (_, filePath, lineStr, sevSub, rawMsg) = mavenColonMatch.destructured
            val severity = when (sevSub.lowercase()) {
                "error" -> DiagnosticSeverity.ERROR
                "warning" -> DiagnosticSeverity.WARNING
                else -> DiagnosticSeverity.NOTE
            }
            val result = HumanExplanationEngine.explain(rawMsg, severity)
            val diagnostic = Diagnostic(
                filePath = filePath.trim(),
                line = lineStr.toIntOrNull() ?: 1,
                column = 1,
                severity = severity,
                rawMessage = rawMsg.trim(),
                humanTitle = result.title,
                humanExplanation = result.explanation,
                offlineHint = result.offlineHint,
                suggestedFix = result.suggestedFix
            )
            lastDiagnostic = diagnostic
            return diagnostic
        }

        // Kotlin compiler output: e: /path/to/File.kt: (15, 8): Unresolved reference: xyz
        val kotlinMatch = kotlinRegex.find(trimmed)
        if (kotlinMatch != null) {
            val (sevPrefix, filePath, lineStr, colStr, rawMsg) = kotlinMatch.destructured
            val severity = if (sevPrefix.equals("e", ignoreCase = true)) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
            val result = HumanExplanationEngine.explain(rawMsg, severity)
            val diagnostic = Diagnostic(
                filePath = filePath.trim(),
                line = lineStr.toIntOrNull() ?: 1,
                column = colStr.toIntOrNull() ?: 1,
                severity = severity,
                rawMessage = rawMsg.trim(),
                humanTitle = result.title,
                humanExplanation = result.explanation,
                offlineHint = result.offlineHint,
                suggestedFix = result.suggestedFix
            )
            lastDiagnostic = diagnostic
            return diagnostic
        }

        // Standard javac output without column: /path/to/File.java:15: error: cannot find symbol
        val javacMatch = standardJavacRegex.find(trimmed)
        if (javacMatch != null) {
            val (filePath, lineStr, sevSub, rawMsg) = javacMatch.destructured
            val severity = if (sevSub.equals("error", ignoreCase = true)) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
            val result = HumanExplanationEngine.explain(rawMsg, severity)
            val diagnostic = Diagnostic(
                filePath = filePath.trim(),
                line = lineStr.toIntOrNull() ?: 1,
                column = 1,
                severity = severity,
                rawMessage = rawMsg.trim(),
                humanTitle = result.title,
                humanExplanation = result.explanation,
                offlineHint = result.offlineHint,
                suggestedFix = result.suggestedFix
            )
            lastDiagnostic = diagnostic
            return diagnostic
        }

        // AAPT2 resource compiler output: /path/to/res/layout/foo.xml:12: AAPT: error: resource not found
        val aaptMatch = aaptRegex.find(trimmed)
        if (aaptMatch != null) {
            val (filePath, lineStr, rawMsg) = aaptMatch.destructured
            val severity = DiagnosticSeverity.ERROR
            val result = HumanExplanationEngine.explain(rawMsg, severity)
            val diagnostic = Diagnostic(
                filePath = filePath.trim(),
                line = lineStr.toIntOrNull() ?: 1,
                column = 1,
                severity = severity,
                rawMessage = rawMsg.trim(),
                humanTitle = result.title,
                humanExplanation = result.explanation,
                offlineHint = result.offlineHint,
                suggestedFix = result.suggestedFix
            )
            lastDiagnostic = diagnostic
            return diagnostic
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

        val result = HumanExplanationEngine.explain(rawMsg, severity)

        val diagnostic = Diagnostic(
            filePath = filePath,
            line = lineStr.toIntOrNull() ?: 1,
            column = colStr.toIntOrNull() ?: 1,
            severity = severity,
            rawMessage = rawMsg,
            humanTitle = result.title,
            humanExplanation = result.explanation,
            offlineHint = result.offlineHint,
            suggestedFix = result.suggestedFix
        )

        lastDiagnostic = diagnostic
        return diagnostic
    }
}
