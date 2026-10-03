package com.prismde.feature_editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.R
import com.prismde.core.model.Project
import java.io.File

@Composable
fun AttachFileDialog(
    project: Project?,
    currentlyAttachedPaths: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<File>) -> Unit
) {
    if (project == null) {
        onDismiss()
        return
    }

    val allFiles = remember(project) {
        val rootPath = project.rootDir.absolutePath
        project.rootDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && !it.path.contains("/build/") && !it.path.contains("\\build\\") }
            .toList()
            .sortedBy { it.name }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPaths by remember {
        mutableStateOf(currentlyAttachedPaths.toMutableSet())
    }

    val filteredFiles = remember(searchQuery, allFiles) {
        if (searchQuery.isBlank()) allFiles
        else allFiles.filter { it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.select_files_to_attach), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (allFiles.size > 5) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search files...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (filteredFiles.isEmpty()) {
                    Text(
                        stringResource(R.string.no_files_in_project),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(filteredFiles, key = { it.absolutePath }) { file ->
                            val relPath = file.absolutePath.removePrefix(project.rootDir.absolutePath).trimStart('/', '\\')
                            val isChecked = relPath in selectedPaths

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedPaths = if (isChecked) {
                                            (selectedPaths - relPath).toMutableSet()
                                        } else {
                                            (selectedPaths + relPath).toMutableSet()
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedPaths = if (checked) {
                                            (selectedPaths + relPath).toMutableSet()
                                        } else {
                                            (selectedPaths - relPath).toMutableSet()
                                        }
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = relPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rootPath = project.rootDir.absolutePath
                    val chosenFiles = allFiles.filter { file ->
                        val relPath = file.absolutePath.removePrefix(rootPath).trimStart('/', '\\')
                        relPath in selectedPaths
                    }
                    onConfirm(chosenFiles)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
