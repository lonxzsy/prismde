package com.prismde.feature_build.components

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.prismde.R
import com.prismde.core.theme.DiagnosticSuccess
import java.io.File

@Composable
fun ExportSoDialog(
    soFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isApk = soFile.extension.equals("apk", ignoreCase = true)
    val isSo = soFile.extension.equals("so", ignoreCase = true)

    val titleText = when {
        isApk -> stringResource(R.string.apk_built)
        isSo -> stringResource(R.string.library_built)
        else -> stringResource(R.string.artifact_built)
    }

    val descText = when {
        isApk -> stringResource(R.string.apk_built_desc)
        isSo -> stringResource(R.string.library_built_desc)
        else -> stringResource(R.string.artifact_built_desc)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = DiagnosticSuccess,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(titleText)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.file_label, soFile.name),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.size_kb, soFile.length() / 1024),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (isSo) {
                            Text(
                                text = stringResource(R.string.architecture_label, soFile.parentFile?.name ?: "arm64-v8a"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = soFile.absolutePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (isApk) {
                    Button(
                        onClick = {
                            installApk(context, soFile)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.install_apk))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = {
                        shareSoFile(context, soFile)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share_so))
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(soFile.absolutePath))
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.copy_path))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun installApk(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareSoFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = if (file.extension.equals("apk", ignoreCase = true)) "application/vnd.android.package-archive" else "application/octet-stream"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_so_chooser)))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
