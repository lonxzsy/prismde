package com.prismde.core.model

import java.util.UUID

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    NOTE,
    FATAL
}

data class Diagnostic(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val rawMessage: String,
    val humanTitle: String,
    val humanExplanation: String,
    val offlineHint: String? = null,
    val suggestedFix: String? = null,
    val fixRange: DiagnosticRange? = null
)

data class DiagnosticRange(
    val startLine: Int,
    val startCol: Int,
    val endLine: Int,
    val endCol: Int
)
