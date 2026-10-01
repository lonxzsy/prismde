package com.prismde.feature_build.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.model.Diagnostic
import com.prismde.core.theme.DiagnosticError
import com.prismde.core.theme.DiagnosticSuccess
import com.prismde.feature_build.engine.BuildOutputEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildBottomSheet(
    isVisible: Boolean,
    isBuilding: Boolean,
    buildSuccess: Boolean?,
    diagnostics: List<Diagnostic>,
    logs: List<BuildOutputEvent.LogLine>,
    onDismiss: () -> Unit,
    onJumpToCode: (Diagnostic) -> Unit,
    onApplyFix: ((Diagnostic) -> Unit)? = null,
    onApplyAiFix: ((Diagnostic, String) -> Unit)? = null,
    onAskAi: ((Diagnostic) -> Unit)? = null,
    aiExplanations: Map<String, String> = emptyMap(),
    aiLoadingMap: Map<String, Boolean> = emptyMap()
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    var selectedTab by remember { mutableStateOf(0) }

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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Выполняется сборка NDK...",
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
                        text = "Сборка успешна!",
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
                        text = "Ошибки сборки (${diagnostics.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DiagnosticError
                    )
                }

                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ошибки (${diagnostics.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Вывод сборки (${logs.size})") }
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
                                text = if (isBuilding) "Анализ вывода компилятора..." else "Ошибок не обнаружено!",
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
                                    onApplyAiFix = { diag, code ->
                                        onApplyAiFix?.invoke(diag, code)
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
