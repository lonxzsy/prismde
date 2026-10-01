package com.prismde.feature_files.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismde.core.model.Project
import com.prismde.core.model.ProjectType
import com.prismde.core.theme.DiagnosticSuccess
import com.prismde.feature_files.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerBottomSheet(
    currentProject: Project?,
    onSelectProject: (Project) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var projects by remember { mutableStateOf(ProjectManager.listProjects(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            coroutineScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    ProjectManager.importProjectFromTreeUri(context, uri)
                }
                isImporting = false
                if (imported != null) {
                    projects = ProjectManager.listProjects(context)
                    onSelectProject(imported)
                    onDismiss()
                } else {
                    Toast.makeText(context, "Не удалось импортировать папку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Выбор проекта",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Создать")
                }

                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Из памяти")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isImporting) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "Импорт проекта из выбранной папки...",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Projects List
            if (projects.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Проекты пока не созданы",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Создайте новый проект по шаблону или выберите папку на устройстве.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(projects, key = { it.rootPath }) { project ->
                        val isCurrent = project.rootPath == currentProject?.rootPath
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectProject(project)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Rounded.CheckCircle else Icons.Rounded.Layers,
                                    contentDescription = null,
                                    tint = if (isCurrent) DiagnosticSuccess else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = when (project.detectedType) {
                                            ProjectType.PURE_JNI_SO -> "JNI .so библиотека (Android.mk)"
                                            ProjectType.CMAKE -> "CMake / C++ проект"
                                            ProjectType.SINGLE_FILE_EXECUTABLE -> "C++ Исполняемый файл"
                                            ProjectType.AUTO_DETECT -> "Автоопределение"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { projectToDelete = project }
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New Project Dialog
    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type ->
                showCreateDialog = false
                val newProject = ProjectManager.createProject(context, name, type)
                projects = ProjectManager.listProjects(context)
                onSelectProject(newProject)
                onDismiss()
            }
        )
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { target ->
        val isInternal = try {
            target.rootDir.canonicalPath.startsWith(ProjectManager.getProjectsDir(context).canonicalPath)
        } catch (_: Throwable) {
            false
        }
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(if (isInternal) "Удалить проект?" else "Убрать проект?") },
            text = {
                Text(
                    if (isInternal) "Вы уверены, что хотите удалить проект «${target.name}» со всеми файлами?"
                    else "Убрать проект «${target.name}» из списка проектов? Файлы на устройстве удалены не будут."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        ProjectManager.deleteProject(context, target)
                        projects = ProjectManager.listProjects(context)
                        projectToDelete = null
                        if (target.rootPath == currentProject?.rootPath) {
                            projects.firstOrNull()?.let { onSelectProject(it) }
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isInternal) "Удалить" else "Убрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: ProjectType) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ProjectType.PURE_JNI_SO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый NDK проект", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Имя проекта") },
                    placeholder = { Text("my_native_app") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Тип проекта и система сборки:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                val templates = listOf(
                    Triple(
                        ProjectType.PURE_JNI_SO,
                        "JNI Shared Library (.so)",
                        "Сборка .so библиотек с Android.mk и Application.mk для интеграции в Android"
                    ),
                    Triple(
                        ProjectType.CMAKE,
                        "CMake / C++ проект",
                        "Современный тулчейн CMakeLists.txt и Ninja для гибкой сборки"
                    ),
                    Triple(
                        ProjectType.SINGLE_FILE_EXECUTABLE,
                        "Исполняемый C++ бинарник",
                        "Простой консольный проект (main.cpp) для нативного запуска"
                    )
                )

                templates.forEach { (type, title, desc) ->
                    val isSelected = selectedType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedType = type }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedType = type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = projectName.ifBlank { "ndk_project" }
                    onCreate(finalName, selectedType)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
