package com.prismde.feature_editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun EditorTabs(
    openFiles: List<File>,
    activeFile: File?,
    onSelectFile: (File) -> Unit,
    onCloseFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (openFiles.isEmpty()) return

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        openFiles.forEach { file ->
            val isActive = file == activeFile
            val bgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .clickable { onSelectFile(file) }
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = textColor
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCloseFile(file) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
