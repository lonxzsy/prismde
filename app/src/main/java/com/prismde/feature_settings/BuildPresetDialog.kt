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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.R
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
    var mavenGoals by remember { mutableStateOf(initialConfig.mavenGoals) }
    var mavenCustomFlags by remember { mutableStateOf(initialConfig.mavenCustomFlags) }
    var gradleTasks by remember { mutableStateOf(initialConfig.gradleTasks) }
    var gradleCustomFlags by remember { mutableStateOf(initialConfig.gradleCustomFlags) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.build_preset_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // ABI Selection
                Text(stringResource(R.string.target_abi), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                Text(stringResource(R.string.project_type_build_system), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                listOf(
                    ProjectType.AUTO_DETECT to stringResource(R.string.type_auto_detect),
                    ProjectType.GRADLE to stringResource(R.string.type_gradle),
                    ProjectType.PURE_JNI_SO to stringResource(R.string.type_jni_so),
                    ProjectType.CMAKE to stringResource(R.string.type_cmake),
                    ProjectType.SINGLE_FILE_EXECUTABLE to stringResource(R.string.type_single_file),
                    ProjectType.MAVEN to stringResource(R.string.type_maven)
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
                Text(stringResource(R.string.cpp_standard), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

                val isRu = java.util.Locale.getDefault().language == "ru"

                // Min API Level
                OutlinedTextField(
                    value = minApiStr,
                    onValueChange = { minApiStr = it },
                    label = { Text(if (isRu) "Минимальный Android API" else "Minimum Android API Level") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Custom CFlags
                OutlinedTextField(
                    value = customCFlags,
                    onValueChange = { customCFlags = it },
                    label = { Text(if (isRu) "Флаги компилятора (CFlags)" else "Compiler Flags (CFlags)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Custom LDFlags
                OutlinedTextField(
                    value = customLdFlags,
                    onValueChange = { customLdFlags = it },
                    label = { Text(if (isRu) "Флаги линковщика (LDFlags)" else "Linker Flags (LDFlags)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Maven Goals
                OutlinedTextField(
                    value = mavenGoals,
                    onValueChange = { mavenGoals = it },
                    label = { Text(if (isRu) "Maven цели (Goals, напр: package)" else "Maven Goals (e.g.: package)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Maven Flags
                OutlinedTextField(
                    value = mavenCustomFlags,
                    onValueChange = { mavenCustomFlags = it },
                    label = { Text(if (isRu) "Maven флаги (напр: -DskipTests)" else "Maven Flags (e.g.: -DskipTests)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Gradle Tasks
                OutlinedTextField(
                    value = gradleTasks,
                    onValueChange = { gradleTasks = it },
                    label = { Text(if (isRu) "Gradle задачи (напр: assembleDebug)" else "Gradle Tasks (e.g.: assembleDebug)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Gradle Flags
                OutlinedTextField(
                    value = gradleCustomFlags,
                    onValueChange = { gradleCustomFlags = it },
                    label = { Text(if (isRu) "Gradle флаги (напр: --no-daemon)" else "Gradle Flags (e.g.: --no-daemon)") },
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
                            customLdFlags = customLdFlags,
                            mavenGoals = mavenGoals,
                            mavenCustomFlags = mavenCustomFlags,
                            gradleTasks = gradleTasks,
                            gradleCustomFlags = gradleCustomFlags
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
