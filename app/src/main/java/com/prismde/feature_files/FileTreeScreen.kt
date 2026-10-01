package com.prismde.feature_files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.model.Project
import com.prismde.feature_files.components.ProjectPickerBottomSheet
import java.io.File

@Composable
fun FileTreeScreen(
    currentProject: Project?,
    activeFile: File?,
    onOpenFile: (File) -> Unit,
    onCreateFile: (name: String, content: String, isFolder: Boolean) -> Unit,
    onSelectProject: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    Scaffold(
        floatingActionButton = {
            if (currentProject != null) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Создать файл")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (currentProject == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Нет активного проекта",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Выберите существующий проект или создайте новый проект из шаблона.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { showProjectPicker = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Выбрать или создать проект")
                        }
                    }
                }
            } else {
                // Project Header Banner
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showProjectPicker = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentProject.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentProject.rootPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = { showProjectPicker = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Проекты")
                        }

                        IconButton(onClick = { refreshKey++ }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Обновить дерево")
                        }
                    }
                }

                // File Tree
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 8.dp)
                ) {
                    val rootFiles = currentProject.rootDir.listFiles()?.sortedWith(
                        compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
                    ) ?: emptyList()

                    for (file in rootFiles) {
                        if (!file.name.startsWith(".")) {
                            FileNodeItem(
                                file = file,
                                depth = 0,
                                activeFile = activeFile,
                                onFileClick = onOpenFile
                            )
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, content, isFolder ->
                showCreateDialog = false
                onCreateFile(name, content, isFolder)
                refreshKey++
            }
        )
    }

    if (showProjectPicker) {
        ProjectPickerBottomSheet(
            currentProject = currentProject,
            onSelectProject = { project ->
                showProjectPicker = false
                onSelectProject(project)
                refreshKey++
            },
            onDismiss = { showProjectPicker = false }
        )
    }
}
