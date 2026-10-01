package com.prismde.feature_ndk.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.model.NdkVersion
import com.prismde.core.theme.DiagnosticSuccess

@Composable
fun NdkVersionCard(
    ndk: NdkVersion,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadPercent: Float,
    downloadSpeed: Long,
    onDownloadClick: (NdkVersion) -> Unit,
    onSelectActiveClick: (NdkVersion) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ndk.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = ndk.llvmVersion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Badge(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Text(
                        text = "${ndk.archiveSizeBytes / (1024 * 1024)} МБ",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Download Progress Bar
            AnimatedVisibility(visible = isDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            text = "${downloadPercent.toInt()}% скачано",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${downloadSpeed / (1024 * 1024)} МБ/с",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (ndk.isInstalled) {
                    if (isActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = DiagnosticSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Активный NDK",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DiagnosticSuccess
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectActiveClick(ndk) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Выбрать для сборки")
                        }
                    }
                } else {
                    Button(
                        onClick = { onDownloadClick(ndk) },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isDownloading) "Загрузка..." else "Скачать тулчейн")
                    }
                }
            }
        }
    }
}
