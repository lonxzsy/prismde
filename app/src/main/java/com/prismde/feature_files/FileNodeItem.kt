package com.prismde.feature_files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun FileNodeItem(
    file: File,
    depth: Int = 0,
    activeFile: File?,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(depth == 0) }
    val isDirectory = file.isDirectory
    val isActive = file == activeFile

    val icon: ImageVector = when {
        isDirectory && isExpanded -> Icons.Rounded.FolderOpen
        isDirectory -> Icons.Rounded.Folder
        file.name.endsWith(".cpp") || file.name.endsWith(".c") || file.name.endsWith(".cc") -> Icons.Rounded.Code
        file.name == "Android.mk" || file.name == "Application.mk" || file.name == "CMakeLists.txt" -> Icons.Rounded.Settings
        else -> Icons.Rounded.InsertDriveFile
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onFileClick(file)
                    }
                },
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 18 + 12).dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive || isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }

        if (isDirectory) {
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    val children = file.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                    for (child in children) {
                        if (!child.name.startsWith(".")) {
                            FileNodeItem(
                                file = child,
                                depth = depth + 1,
                                activeFile = activeFile,
                                onFileClick = onFileClick
                            )
                        }
                    }
                }
            }
        }
    }
}
