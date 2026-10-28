package com.prismde.feature_editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.prismde.core.theme.ExpressiveShapes
import com.prismde.core.theme.PrismMotion
import com.prismde.core.theme.bouncyClickable
import java.io.File

@Composable
fun EditorTabs(
    openFiles: List<File>,
    activeFile: File?,
    modifiedFiles: Set<File> = emptySet(),
    onSelectFile: (File) -> Unit,
    onCloseFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    if (openFiles.isEmpty()) return

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        openFiles.forEach { file ->
            val isActive = file == activeFile
            val bgColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clip(ExpressiveShapes.TabShape)
                    .background(bgColor)
                    .then(
                        if (isActive) {
                            Modifier.border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                shape = ExpressiveShapes.TabShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .bouncyClickable(onClick = { onSelectFile(file) })
                    .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
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
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                    val isFileModified = file in modifiedFiles
                    AnimatedVisibility(
                        visible = isFileModified,
                        enter = fadeIn(animationSpec = tween(220)) + scaleIn(animationSpec = tween(220)),
                        exit = fadeOut(animationSpec = tween(220)) + scaleOut(animationSpec = tween(220))
                    ) {
                        Text(
                            text = " *",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { onCloseFile(file) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close ${file.name}",
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
