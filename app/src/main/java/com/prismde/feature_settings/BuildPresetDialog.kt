package com.prismde.feature_settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.model.AndroidAbi
import com.prismde.core.model.BuildConfiguration
import com.prismde.core.model.CppStandard
import com.prismde.core.model.OptimizationLevel
import com.prismde.core.model.ProjectType

@Composable
fun BuildPresetDialog(
    initialConfig: BuildConfiguration,
    onDismiss: () -> Unit,
    onSave: (BuildConfiguration) -> Unit
) {
    var selectedAbi by remember { mutableStateOf(initialConfig.selectedAbi) }
    var selectedStandard by remember { mutableStateOf(initialConfig.cppStandard) }
    var selectedOptimization by remember { mutableStateOf(initialConfig.optimizationLevel) }
    var selectedProjectType by remember { mutableStateOf(initialConfig.projectType) }
    var minApiStr by remember { mutableStateOf(initialConfig.minApiLevel.toString()) }
    var customCFlags by remember { mutableStateOf(initialConfig.customCFlags) }
    var customLdFlags by remember { mutableStateOf(initialConfig.customLdFlags) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Параметры сборки NDK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // ABI Selection
                Text("Целевая архитектура (ABI):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                AndroidAbi.values().forEach { abi ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAbi = abi }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedAbi == abi, onClick = { selectedAbi = abi })
                        Spacer(Modifier.width(6.dp))
                        Text(abi.abiString)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Project Type
                Text("Режим сборки:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                listOf(
                    ProjectType.AUTO_DETECT to "Автоопределение",
                    ProjectType.PURE_JNI_SO to "Только .so библиотека (JNI)",
                    ProjectType.CMAKE to "CMake / Ninja",
                    ProjectType.SINGLE_FILE_EXECUTABLE to "Исполняемый бинарник"
                ).forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProjectType = type }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedProjectType == type, onClick = { selectedProjectType = type })
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // C++ Standard
                Text("Стандарт C++:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                CppStandard.values().forEach { std ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStandard = std }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedStandard == std, onClick = { selectedStandard = std })
                        Spacer(Modifier.width(6.dp))
                        Text(std.name)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Min API Level
                OutlinedTextField(
                    value = minApiStr,
                    onValueChange = { minApiStr = it },
                    label = { Text("Минимальный Android API") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Custom CFlags
                OutlinedTextField(
                    value = customCFlags,
                    onValueChange = { customCFlags = it },
                    label = { Text("Флаги компилятора (CFlags)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Custom LDFlags
                OutlinedTextField(
                    value = customLdFlags,
                    onValueChange = { customLdFlags = it },
                    label = { Text("Флаги линковщика (LDFlags)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val api = minApiStr.toIntOrNull() ?: 24
                    onSave(
                        initialConfig.copy(
                            selectedAbi = selectedAbi,
                            cppStandard = selectedStandard,
                            optimizationLevel = selectedOptimization,
                            projectType = selectedProjectType,
                            minApiLevel = api,
                            customCFlags = customCFlags,
                            customLdFlags = customLdFlags
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
