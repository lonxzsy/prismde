package com.prismde.feature_build.components

import android.widget.Toast
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.R
import com.prismde.core.model.Diagnostic
import com.prismde.core.model.Project
import com.prismde.core.telemetry.TelegramBugReporter
import com.prismde.core.theme.DiagnosticError
import com.prismde.core.theme.DiagnosticSuccess
import com.prismde.feature_build.engine.BuildOutputEvent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildBottomSheet(
    isVisible: Boolean,
    isBuilding: Boolean,
    buildSuccess: Boolean?,
    diagnostics: List<Diagnostic>,
    logs: List<BuildOutputEvent.LogLine>,
    project: Project? = null,
    onDismiss: () -> Unit,
    onJumpToCode: (Diagnostic) -> Unit,
    onApplyFix: ((Diagnostic) -> Unit)? = null,
    onApplyAiFix: ((Diagnostic) -> Unit)? = null,
    onAskAi: ((Diagnostic) -> Unit)? = null,
    aiExplanations: Map<String, String> = emptyMap(),
    aiLoadingMap: Map<String, Boolean> = emptyMap()
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(buildSuccess, diagnostics.size) {
        if (buildSuccess == false && diagnostics.isEmpty()) {
            selectedTab = 1
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBuilding) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.build_in_progress),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else if (buildSuccess == true) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = DiagnosticSuccess,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.build_successful),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DiagnosticSuccess
                    )
                } else if (buildSuccess == false) {
                    Icon(
                        imageVector = Icons.Rounded.Error,
                        contentDescription = null,
                        tint = DiagnosticError,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (diagnostics.isNotEmpty()) {
                            stringResource(R.string.build_errors_count, diagnostics.size)
                        } else {
                            stringResource(R.string.build_failed)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DiagnosticError
                    )
                }

                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
            }

            if (buildSuccess == false && !isBuilding) {
                var isSendingBugReport by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Сбой компилятора или PrismDE?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                if (!isSendingBugReport) {
                                    isSendingBugReport = true
                                    coroutineScope.launch {
                                        val errSummary = diagnostics.firstOrNull()?.rawMessage
                                            ?: logs.firstOrNull { it.text.contains("error", ignoreCase = true) || it.text.contains("fail", ignoreCase = true) }?.text
                                        val res = TelegramBugReporter.sendReport(
                                            context = context,
                                            reportType = "BUILD_FAILURE",
                                            project = project,
                                            errorSummary = errSummary,
                                            logs = logs.map { it.text }
                                        )
                                        isSendingBugReport = false
                                        res.fold(
                                            onSuccess = { msg ->
                                                Toast.makeText(context, "✔ $msg", Toast.LENGTH_LONG).show()
                                            },
                                            onFailure = { err ->
                                                Toast.makeText(context, "✖ Ошибка отправки: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = !isSendingBugReport,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isSendingBugReport) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Отправка...")
                            } else {
                                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Отправить лог")
                            }
                        }
                    }
                }
            }

            if (isBuilding) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_errors, diagnostics.size)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_build_output, logs.size)) }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Tab Content
            when (selectedTab) {
                0 -> {
                    if (diagnostics.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isBuilding) stringResource(R.string.analyzing_output) else stringResource(R.string.no_errors_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(diagnostics) { diag ->
                                DiagnosticCard(
                                    diagnostic = diag,
                                    onJumpToCode = {
                                        onJumpToCode(it)
                                        onDismiss() // smooth contract sheet to view editor
                                    },
                                    onApplyFix = onApplyFix,
                                    onApplyAiFix = { diag ->
                                        onApplyAiFix?.invoke(diag)
                                        onDismiss()
                                    },
                                    onAskAi = onAskAi,
                                    aiExplanation = aiExplanations[diag.id],
                                    isAiLoading = aiLoadingMap[diag.id] ?: false,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                        }
                    }
                }
                1 -> {
                    BuildLogConsole(
                        logs = logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
