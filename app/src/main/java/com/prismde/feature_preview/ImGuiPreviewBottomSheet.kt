package com.prismde.feature_preview

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Visibility
import com.prismde.core.telemetry.TelegramBugReporter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismde.core.model.NdkVersion
import com.prismde.core.model.Project
import com.prismde.core.theme.ExpressiveShapes
import com.prismde.core.theme.bouncyClickable
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImGuiPreviewBottomSheet(
    project: Project,
    ndk: NdkVersion,
    activeFile: File?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCompiling by remember { mutableStateOf(false) }
    var compilingStatus by remember { mutableStateOf(activeFile?.let { "Compile++ : ${it.name}" } ?: "Подготовка...") }
    var buildResult by remember { mutableStateOf<PreviewBuildResult?>(null) }
    var isLandscape by remember { mutableStateOf(true) }

    fun triggerBuild() {
        coroutineScope.launch {
            isCompiling = true
            compilingStatus = activeFile?.let { "Compile++ : ${it.name}" } ?: "Подготовка..."
            buildResult = ImGuiPreviewEngine.buildPreviewSo(project, ndk, context, activeFile) { status ->
                compilingStatus = status
            }
            isCompiling = false
        }
    }

    LaunchedEffect(Unit) {
        triggerBuild()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ExpressiveShapes.SheetShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Preview GUI (ImGui)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeFile?.name ?: project.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = { isLandscape = !isLandscape },
                        modifier = Modifier.bouncyClickable { isLandscape = !isLandscape }
                    ) {
                        Icon(
                            Icons.Rounded.ScreenRotation,
                            contentDescription = "Rotate",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { triggerBuild() },
                        enabled = !isCompiling,
                        modifier = Modifier.bouncyClickable(enabled = !isCompiling) { triggerBuild() }
                    ) {
                        if (isCompiling) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Rebuild", modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            }

            if (isCompiling) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Preview Canvas Container
            Surface(
                shape = ExpressiveShapes.CardShape,
                color = Color.Transparent,
                border = BorderStroke(
                    1.5.dp,
                    if (buildResult?.success == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val result = buildResult
                if (isCompiling) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = compilingStatus,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Сборка превью интерфейса",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (result != null && result.success && result.soFile != null) {
                    // Live Interactive ImGui Preview Viewport with OpenGL ES 3.0 Context!
                    Box(modifier = Modifier.fillMaxSize()) {
                        ImGuiPreviewView(
                            soFile = result.soFile,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (result != null && !result.success) {
                    // Compilation error logs
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Ошибка компиляции превью",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            var isSendingPreviewReport by remember { mutableStateOf(false) }
                            FilledTonalButton(
                                onClick = {
                                    if (!isSendingPreviewReport) {
                                        isSendingPreviewReport = true
                                        coroutineScope.launch {
                                            val res = TelegramBugReporter.sendReport(
                                                context = context,
                                                reportType = "PREVIEW_FAILURE",
                                                project = project,
                                                errorSummary = result.error,
                                                logs = result.logs
                                            )
                                            isSendingPreviewReport = false
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
                                enabled = !isSendingPreviewReport,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (isSendingPreviewReport) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Отправка...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Отправить отчет", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF141218))
                                .padding(8.dp)
                        ) {
                            items(result.logs) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    ),
                                    color = if (log.contains("error", ignoreCase = true)) Color(0xFFFF5252) else Color(0xFFE6E0E9)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
