package com.prismde.feature_setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismde.core.datastore.SettingsRepository
import com.prismde.core.model.DefaultNdkCatalog
import com.prismde.core.theme.DiagnosticSuccess
import com.prismde.feature_ndk.NdkViewModel
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(
    settingsRepository: SettingsRepository,
    ndkViewModel: NdkViewModel,
    onCompleteSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var currentStep by remember { mutableIntStateOf(0) }

    val darkMode by settingsRepository.darkModeFlow.collectAsState(initial = "system")
    val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)
    val ndkState by ndkViewModel.uiState.collectAsState()

    val targetNdk = ndkState.versions.find { it.versionTag == DefaultNdkCatalog.DEFAULT_ACTIVE_TAG }
        ?: DefaultNdkCatalog.AVAILABLE_VERSIONS.first()

    val isInstalled = targetNdk.isInstalled
    val isDownloading = ndkState.downloadingTag == targetNdk.versionTag

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step Progress Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index == currentStep
                    val isPast = index < currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isActive) 24.dp else 10.dp, 10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else if (isPast) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f),
                label = "wizardSteps"
            ) { step ->
                when (step) {
                    0 -> StepWelcome(
                        darkMode = darkMode,
                        dynamicColor = dynamicColor,
                        onThemeChange = { coroutineScope.launch { settingsRepository.setDarkMode(it) } },
                        onDynamicColorChange = { coroutineScope.launch { settingsRepository.setDynamicColor(it) } }
                    )
                    1 -> StepNdkDownload(
                        targetNdk = targetNdk,
                        isDownloading = isDownloading,
                        isInstalled = isInstalled,
                        downloadPercent = ndkState.downloadPercent,
                        downloadSpeed = ndkState.downloadSpeed,
                        statusMessage = ndkState.statusMessage,
                        onStartInstall = { ndkViewModel.downloadNdk(targetNdk) }
                    )
                    2 -> StepFinished()
                }
            }

            // Bottom Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0 && currentStep < 2) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Назад")
                    }
                } else if (currentStep == 0) {
                    TextButton(onClick = { currentStep = 1 }) {
                        Text("Пропустить")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep++
                        } else {
                            coroutineScope.launch {
                                settingsRepository.setSetupCompleted(true)
                                onCompleteSetup()
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = when (currentStep) {
                            0 -> "Далее"
                            1 -> if (isInstalled) "Далее" else "Продолжить"
                            else -> "Перейти к коду"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StepWelcome(
    darkMode: String,
    dynamicColor: Boolean,
    onThemeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.RocketLaunch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Добро пожаловать в PrismDE",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Нативная среда для разработки и компиляции C/C++ проектов с NDK прямо на вашем Android-устройстве.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Тема интерфейса:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "Авто", "light" to "Светлая", "dark" to "Тёмная").forEach { (mode, label) ->
                        val isSelected = darkMode == mode
                        Button(
                            onClick = { onThemeChange(mode) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Material You", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Динамические цвета системы", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dynamicColor, onCheckedChange = onDynamicColorChange)
                }
            }
        }
    }
}

@Composable
private fun StepNdkDownload(
    targetNdk: com.prismde.core.model.NdkVersion,
    isDownloading: Boolean,
    isInstalled: Boolean,
    downloadPercent: Float,
    downloadSpeed: Long,
    statusMessage: String?,
    onStartInstall: () -> Unit
) {
    // Automatically trigger installation on entering this step if not yet installed
    LaunchedEffect(Unit) {
        if (!isInstalled && !isDownloading) {
            onStartInstall()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isInstalled) Icons.Rounded.CheckCircle else Icons.Rounded.Memory,
            contentDescription = null,
            tint = if (isInstalled) DiagnosticSuccess else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isInstalled) "NDK готов к работе!" else "Установка Android NDK",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Для сборки нативных C/C++ проектов и создания .so библиотек PrismDE устанавливает тулчейн Clang/LLVM из официального релиза.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isInstalled) DiagnosticSuccess.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetNdk.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Размер архива: ${targetNdk.archiveSizeBytes / (1024 * 1024)} МБ (AArch64)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else if (isInstalled) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = DiagnosticSuccess)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            text = "${downloadPercent.toInt()}% загружено",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${downloadSpeed / (1024 * 1024)} МБ/с",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!isInstalled && !isDownloading) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onStartInstall,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Начать загрузку NDK")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepFinished() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = DiagnosticSuccess,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Настройка завершена!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Для вас уже подготовлен тестовый JNI-проект с нативной библиотекой. Нажмите кнопку ниже, чтобы открыть редактор кода и собрать ваш первый .so файл.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
