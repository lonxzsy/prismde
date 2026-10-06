package com.prismde.feature_settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.prismde.feature_build.engine.BuildToolInstaller
import com.prismde.feature_build.engine.CustomEndpointClient
import com.prismde.feature_setup.DonationModalSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prismde.R
import com.prismde.core.datastore.SettingsRepository
import com.prismde.feature_build.engine.AntigravityAuthManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateNdkManager: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val authManager = remember { AntigravityAuthManager() }
    val customAiClient = remember { CustomEndpointClient() }
    val isRu = remember { java.util.Locale.getDefault().language == "ru" }

    val darkMode by settingsRepository.darkModeFlow.collectAsState(initial = "system")
    val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)
    val activeNdk by settingsRepository.activeNdkFlow.collectAsState(initial = "r26c")
    val geminiKey by settingsRepository.geminiApiKeyFlow.collectAsState(initial = "")
    val fontSize by settingsRepository.editorFontSizeFlow.collectAsState(initial = 14f)
    val wordWrap by settingsRepository.editorWordWrapFlow.collectAsState(initial = false)

    val aiProvider by settingsRepository.aiProviderFlow.collectAsState(initial = "gemini_api")
    val geminiModel by settingsRepository.geminiModelFlow.collectAsState(initial = "gemini-2.5-flash")
    val antigravityModel by settingsRepository.antigravityModelFlow.collectAsState(initial = "gemini-3.8-flash")
    val antigravityAccessToken by settingsRepository.antigravityAccessTokenFlow.collectAsState(initial = "")
    val antigravityRefreshToken by settingsRepository.antigravityRefreshTokenFlow.collectAsState(initial = "")
    val antigravityUserEmail by settingsRepository.antigravityUserEmailFlow.collectAsState(initial = "")
    val antigravityUserName by settingsRepository.antigravityUserNameFlow.collectAsState(initial = "")
    val antigravityQuotaRemaining by settingsRepository.antigravityQuotaRemainingFlow.collectAsState(initial = "")
    val antigravityQuotaResetTime by settingsRepository.antigravityQuotaResetTimeFlow.collectAsState(initial = "")
    val antigravityQuotaSummaryJson by settingsRepository.antigravityQuotaSummaryJsonFlow.collectAsState(initial = null)

    val customBaseUrl by settingsRepository.customAiBaseUrlFlow.collectAsState(initial = "")
    val customApiKey by settingsRepository.customAiApiKeyFlow.collectAsState(initial = "")
    val customModel by settingsRepository.customAiModelFlow.collectAsState(initial = "")
    val customModelsUrl by settingsRepository.customAiModelsUrlFlow.collectAsState(initial = "")
    val customAuthType by settingsRepository.customAiAuthTypeFlow.collectAsState(initial = "bearer")
    val customHeaderName by settingsRepository.customAiHeaderNameFlow.collectAsState(initial = "Authorization")
    val customCachedModels by settingsRepository.customAiCachedModelsFlow.collectAsState(initial = emptyList())

    val autoInstallTools by settingsRepository.autoInstallToolsFlow.collectAsState(initial = true)
    val customJavaHome by settingsRepository.customJavaHomeFlow.collectAsState(initial = "")
    var customJavaHomeInput by remember(customJavaHome) { mutableStateOf(customJavaHome) }

    var isInstallingMaven by remember { mutableStateOf(false) }
    var mavenInstallProgress by remember { mutableStateOf(0f) }
    var mavenInstallStatus by remember { mutableStateOf("") }
    var mavenRefreshTrigger by remember { mutableStateOf(0) }

    var isInstallingJdk by remember { mutableStateOf(false) }
    var jdkInstallProgress by remember { mutableStateOf(0f) }
    var jdkInstallStatus by remember { mutableStateOf("") }

    var isInstallingGradle by remember { mutableStateOf(false) }
    var gradleInstallProgress by remember { mutableStateOf(0f) }
    var gradleInstallStatus by remember { mutableStateOf("") }

    var isInstallingPlatform by remember { mutableStateOf(false) }
    var platformInstallProgress by remember { mutableStateOf(0f) }
    var platformInstallStatus by remember { mutableStateOf("") }

    var isInstallingBuildTools by remember { mutableStateOf(false) }
    var buildToolsInstallProgress by remember { mutableStateOf(0f) }
    var buildToolsInstallStatus by remember { mutableStateOf("") }

    val isMavenInstalled = remember(mavenRefreshTrigger) {
        BuildToolInstaller.isMavenInstalled(context)
    }
    val isGradleInstalled = remember(mavenRefreshTrigger) {
        BuildToolInstaller.isGradleInstalled(context)
    }
    val isPlatformInstalled = remember(mavenRefreshTrigger) {
        BuildToolInstaller.isAndroidPlatformInstalled(context, 34)
    }
    val isBuildToolsInstalled = remember(mavenRefreshTrigger) {
        BuildToolInstaller.isAndroidBuildToolsInstalled(context, "34.0.0")
    }
    val isInternalJdkInstalled = remember(mavenRefreshTrigger) {
        BuildToolInstaller.isJdkInstalled(context)
    }
    val javaInfo = remember(customJavaHomeInput, mavenRefreshTrigger) {
        BuildToolInstaller.detectJavaEnvironment(context, customJavaHome = customJavaHomeInput)
    }

    val quotaGroups = remember(antigravityQuotaSummaryJson) {
        AntigravityAuthManager.parseQuotaSummaryJson(antigravityQuotaSummaryJson)
    }

    var geminiKeyInput by remember(geminiKey) { mutableStateOf(geminiKey) }
    var authCodeInput by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var isRefreshingQuota by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var dynamicAntigravityModels by remember { mutableStateOf(AntigravityAuthManager.DEFAULT_ANTIGRAVITY_MODELS) }
    var showDonationSheet by remember { mutableStateOf(false) }

    var customBaseUrlInput by remember(customBaseUrl) { mutableStateOf(customBaseUrl) }
    var customApiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }
    var customModelInput by remember(customModel) { mutableStateOf(customModel) }
    var customModelsUrlInput by remember(customModelsUrl) { mutableStateOf(customModelsUrl) }
    var customHeaderNameInput by remember(customHeaderName) { mutableStateOf(customHeaderName) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isFetchingCustomModels by remember { mutableStateOf(false) }
    var customFetchStatusMessage by remember { mutableStateOf<String?>(null) }
    var customFetchError by remember { mutableStateOf<String?>(null) }
    var showCustomModelMenu by remember { mutableStateOf(false) }
    var showAgyMenu by remember { mutableStateOf(false) }
    var showGeminiMenu by remember { mutableStateOf(false) }

    // Auto-migrate stale / non-existent model ids to the curated 7 models
    LaunchedEffect(antigravityModel) {
        val validIds = AntigravityAuthManager.DEFAULT_ANTIGRAVITY_MODELS.map { it.id }.toSet()
        if (antigravityModel !in validIds) {
            val migrated = when (antigravityModel) {
                "gemini-3.6-flash-high", "gemini-3.6-flash-medium", "gemini-3.6-flash-low" -> "gemini-3.6-flash"
                "gemini-pro-agent", "gemini-3.1-pro-low", "gemini-3-pro" -> "gemini-3.1-pro"
                "claude-sonnet-4-6", "claude-sonnet-4-20250514" -> "claude-sonnet-4.6"
                "claude-opus-4-6-thinking", "claude-opus-4.5" -> "claude-opus-4.6"
                "gpt-oss-120b-medium" -> "gpt-oss-120b"
                else -> "gemini-3.8-flash"
            }
            settingsRepository.setAntigravityModel(migrated)
        }
    }

    // Automatically refresh models and quota if token exists on entry
    LaunchedEffect(antigravityAccessToken) {
        if (antigravityAccessToken.isNotBlank()) {
            val qRes = authManager.fetchModelsAndQuota(antigravityAccessToken)
            if (qRes.isSuccess) {
                val qInfo = qRes.getOrThrow()
                if (qInfo.models.isNotEmpty()) {
                    dynamicAntigravityModels = qInfo.models
                }
                val quotaPercent = "${(qInfo.averageQuotaFraction * 100).toInt()}%"
                settingsRepository.updateAntigravityQuota(quotaPercent, qInfo.resetTime, qInfo.rawSummaryJson)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        // SECTION: Appearance
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance), icon = Icons.Rounded.Palette)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isRu) "Динамические цвета (Material You)" else "Dynamic Colors (Material You)", style = MaterialTheme.typography.bodyLarge)
                        Text(if (isRu) "Адаптация палитры под обои системы" else "Adaptive color palette based on system wallpaper", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { coroutineScope.launch { settingsRepository.setDynamicColor(it) } }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentThemeLabel = if (darkMode == "dark") stringResource(R.string.theme_dark) else if (darkMode == "light") stringResource(R.string.theme_light) else stringResource(R.string.theme_system)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.bodyLarge)
                        Text(if (isRu) "Текущая: $currentThemeLabel" else "Current: $currentThemeLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            val next = when (darkMode) {
                                "system" -> "dark"
                                "dark" -> "light"
                                else -> "system"
                            }
                            coroutineScope.launch { settingsRepository.setDarkMode(next) }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(currentThemeLabel)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: Code Editor
        SettingsSectionHeader(title = stringResource(R.string.settings_editor_section), icon = Icons.Rounded.Code)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.font_size_label, fontSize.toInt()), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = fontSize,
                    onValueChange = { coroutineScope.launch { settingsRepository.setEditorFontSize(it) } },
                    valueRange = 10f..26f,
                    steps = 15
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.word_wrap), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.word_wrap_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = wordWrap,
                        onCheckedChange = { coroutineScope.launch { settingsRepository.setEditorWordWrap(it) } }
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: NDK Management
        SettingsSectionHeader(title = "Android NDK", icon = Icons.Rounded.Memory)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onNavigateNdkManager() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.manage_toolchains), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.active_toolchain, activeNdk), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: Build Tools & SDK (Maven, Gradle, JDK, Android SDK)
        SettingsSectionHeader(
            title = if (isRu) "Инструменты сборки (Maven, Gradle, JDK, Android SDK)" else "Build Tools & SDK (Maven, Gradle, JDK, Android SDK)",
            icon = Icons.Rounded.Code
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Auto-install toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRu) "Авто-установка утилит сборки" else "Auto-install build tools",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isRu) "Автоматически загружать Maven и недостающие компоненты при старте сборки"
                            else "Automatically download Maven and missing tools when starting build",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoInstallTools,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { settingsRepository.setAutoInstallTools(enabled) }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Maven Tool Card Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Apache Maven 3.9.6",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (isMavenInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isMavenInstalled) (if (isRu) "Установлен" else "Installed") else (if (isRu) "Не установлен" else "Not installed"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMavenInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isMavenInstalled) {
                                val exe = BuildToolInstaller.getMavenExecutable(context)
                                exe?.parentFile?.parentFile?.name ?: "tools/maven"
                            } else {
                                if (isRu) "Не установлен (~9 МБ)" else "Not installed (~9 MB)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    if (isInstallingMaven) {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            if (mavenInstallProgress > 0f) {
                                Text("${mavenInstallProgress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isInstallingMaven = true
                                    mavenInstallProgress = 0f
                                    val ok = BuildToolInstaller.installMaven(context) { status, pct ->
                                        mavenInstallStatus = status
                                        mavenInstallProgress = pct
                                    }
                                    isInstallingMaven = false
                                    mavenRefreshTrigger++
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isMavenInstalled) {
                                    if (isRu) "Переустановить" else "Reinstall"
                                } else {
                                    if (isRu) "Установить" else "Install"
                                }
                            )
                        }
                    }
                }

                if (isInstallingMaven && mavenInstallStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (mavenInstallProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mavenInstallStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Gradle Build Tool Card Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Gradle ${BuildToolInstaller.GRADLE_VERSION}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (isGradleInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isGradleInstalled) (if (isRu) "Установлен" else "Installed") else (if (isRu) "Не установлен" else "Not installed"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isGradleInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isGradleInstalled) {
                                val exe = BuildToolInstaller.getGradleExecutable(context)
                                exe?.parentFile?.parentFile?.name ?: "tools/gradle"
                            } else {
                                if (isRu) "Автономный Gradle (~125 МБ) или через gradlew" else "Standalone Gradle (~125 MB) or via gradlew"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    if (isInstallingGradle) {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            if (gradleInstallProgress > 0f) {
                                Text("${gradleInstallProgress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isInstallingGradle = true
                                    gradleInstallProgress = 0f
                                    val ok = BuildToolInstaller.installGradle(context) { status, pct ->
                                        gradleInstallStatus = status
                                        gradleInstallProgress = pct
                                    }
                                    isInstallingGradle = false
                                    mavenRefreshTrigger++
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) (if (isRu) "✔ Gradle успешно установлен!" else "✔ Gradle installed successfully!")
                                        else (if (isRu) "✖ Ошибка установки: $gradleInstallStatus" else "✖ Install error: $gradleInstallStatus"),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isGradleInstalled) {
                                    if (isRu) "Переустановить" else "Reinstall"
                                } else {
                                    if (isRu) "Установить" else "Install"
                                }
                            )
                        }
                    }
                }

                if (isInstallingGradle && gradleInstallStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (gradleInstallProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = gradleInstallStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Java JDK Environment Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Java Development Kit (JDK 17)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (javaInfo.isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (javaInfo.isAvailable) (if (isRu) "Доступен" else "Available") else (if (isRu) "Не найден" else "Missing"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (javaInfo.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isInternalJdkInstalled) {
                                val javaBin = BuildToolInstaller.getJdkExecutable(context)
                                if (isRu) "Встроенный PrismDE JDK (${javaBin?.parentFile?.parentFile?.name ?: "tools/jdk"})"
                                else "Internal PrismDE JDK (${javaBin?.parentFile?.parentFile?.name ?: "tools/jdk"})"
                            } else if (BuildToolInstaller.hasAnyJdkInstalled(context)) {
                                if (isRu) "Требуется обновление OpenJDK 17 (исправление Tagged Pointers для Android 12+)"
                                else "OpenJDK 17 update required (Tagged Pointers fix for Android 12+)"
                            } else if (javaInfo.isAvailable) {
                                javaInfo.sourceDescription
                            } else {
                                if (isRu) "Автономный OpenJDK 17 LTS (загрузка прямо в приложение, ~96 МБ)"
                                else "Standalone OpenJDK 17 LTS (direct in-app download, ~96 MB)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    if (isInstallingJdk) {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            if (jdkInstallProgress > 0f) {
                                Text("${jdkInstallProgress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isInstallingJdk = true
                                    jdkInstallProgress = 0f
                                    val ok = BuildToolInstaller.installJdk(context) { status, pct ->
                                        jdkInstallStatus = status
                                        jdkInstallProgress = pct
                                    }
                                    isInstallingJdk = false
                                    mavenRefreshTrigger++
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) (if (isRu) "✔ OpenJDK 17 успешно установлен во внутреннее хранилище!" else "✔ OpenJDK 17 installed successfully into internal storage!")
                                        else (if (isRu) "✖ Ошибка установки: $jdkInstallStatus" else "✖ Install error: $jdkInstallStatus"),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isInternalJdkInstalled) {
                                    if (isRu) "Переустановить" else "Reinstall"
                                } else if (BuildToolInstaller.hasAnyJdkInstalled(context)) {
                                    if (isRu) "Обновить" else "Update"
                                } else {
                                    if (isRu) "Установить" else "Install"
                                }
                            )
                        }
                    }
                }

                if (isInstallingJdk && jdkInstallStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (jdkInstallProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = jdkInstallStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(10.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isRu) "💡 На Android песочница безопасности (SELinux/UID) блокирует доступ к каталогам других приложений (например, Termux). Поэтому OpenJDK 17 устанавливается прямо внутрь PrismDE и работает на 100% автономно без других программ."
                        else "💡 On Android, the security sandbox (SELinux/UID) blocks access to other apps' directories (like Termux). Therefore OpenJDK 17 installs directly inside PrismDE and runs 100% autonomously without external apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Android SDK Platform 34 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Android SDK Platform 34",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (isPlatformInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isPlatformInstalled) (if (isRu) "Установлен" else "Installed") else (if (isRu) "Не установлен" else "Not installed"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPlatformInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isPlatformInstalled) {
                                val sdk = BuildToolInstaller.findExistingSdk(context)
                                "${sdk.name}/platforms/android-34"
                            } else {
                                if (isRu) "Базовая библиотека Android (android.jar, ~58 МБ)" else "Android Platform library (android.jar, ~58 MB)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    if (isInstallingPlatform) {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            if (platformInstallProgress > 0f) {
                                Text("${platformInstallProgress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isInstallingPlatform = true
                                    platformInstallProgress = 0f
                                    val ok = BuildToolInstaller.installAndroidPlatform(context, 34) { status, pct ->
                                        platformInstallStatus = status
                                        platformInstallProgress = pct
                                    }
                                    isInstallingPlatform = false
                                    mavenRefreshTrigger++
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) (if (isRu) "✔ Android SDK Platform 34 успешно установлена!" else "✔ Android SDK Platform 34 installed successfully!")
                                        else (if (isRu) "✖ Ошибка установки: $platformInstallStatus" else "✖ Install error: $platformInstallStatus"),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isPlatformInstalled) {
                                    if (isRu) "Переустановить" else "Reinstall"
                                } else {
                                    if (isRu) "Установить" else "Install"
                                }
                            )
                        }
                    }
                }

                if (isInstallingPlatform && platformInstallStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (platformInstallProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = platformInstallStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Android SDK Build-Tools 34.0.0 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Android Build-Tools 34.0.0",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                color = if (isBuildToolsInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isBuildToolsInstalled) (if (isRu) "Установлен" else "Installed") else (if (isRu) "Не установлен" else "Not installed"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBuildToolsInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isBuildToolsInstalled) {
                                val sdk = BuildToolInstaller.findExistingSdk(context)
                                "${sdk.name}/build-tools/34.0.0"
                            } else {
                                if (isRu) "Компилятор DEX (d8), упаковщик и подпись APK (~55 МБ)" else "DEX compiler (d8), packaging and APK signer (~55 MB)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    if (isInstallingBuildTools) {
                        Column(horizontalAlignment = Alignment.End) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            if (buildToolsInstallProgress > 0f) {
                                Text("${buildToolsInstallProgress.toInt()}%", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isInstallingBuildTools = true
                                    buildToolsInstallProgress = 0f
                                    val ok = BuildToolInstaller.installBuildTools(context, "34.0.0") { status, pct ->
                                        buildToolsInstallStatus = status
                                        buildToolsInstallProgress = pct
                                    }
                                    isInstallingBuildTools = false
                                    mavenRefreshTrigger++
                                    android.widget.Toast.makeText(
                                        context,
                                        if (ok) (if (isRu) "✔ Android Build-Tools 34.0.0 успешно установлены!" else "✔ Android Build-Tools 34.0.0 installed successfully!")
                                        else (if (isRu) "✖ Ошибка установки: $buildToolsInstallStatus" else "✖ Install error: $buildToolsInstallStatus"),
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isBuildToolsInstalled) {
                                    if (isRu) "Переустановить" else "Reinstall"
                                } else {
                                    if (isRu) "Установить" else "Install"
                                }
                            )
                        }
                    }
                }

                if (isInstallingBuildTools && buildToolsInstallStatus.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (buildToolsInstallProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildToolsInstallStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Custom JAVA_HOME input
                OutlinedTextField(
                    value = customJavaHomeInput,
                    onValueChange = {
                        customJavaHomeInput = it
                        coroutineScope.launch { settingsRepository.setCustomJavaHome(it) }
                    },
                    label = { Text(if (isRu) "Пользовательский путь JAVA_HOME (опционально)" else "Custom JAVA_HOME path (optional)") },
                    placeholder = { Text("/data/data/com.termux/files/usr/lib/jvm/openjdk-17") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: AI Assistant & Models
        SettingsSectionHeader(title = stringResource(R.string.settings_ai_section), icon = Icons.Rounded.AutoAwesome)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Dedicated Active Service Selector
                Text(
                    text = stringResource(R.string.ai_service_provider),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.ai_choose_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = (aiProvider == "antigravity"),
                        onClick = {
                            coroutineScope.launch { settingsRepository.setAiProvider("antigravity") }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = (aiProvider == "antigravity")) {
                                Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        },
                        label = {
                            Text("Antigravity", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    )
                    SegmentedButton(
                        selected = (aiProvider == "gemini_api"),
                        onClick = {
                            coroutineScope.launch { settingsRepository.setAiProvider("gemini_api") }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = (aiProvider == "gemini_api")) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        },
                        label = {
                            Text("AI Studio", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    )
                    SegmentedButton(
                        selected = (aiProvider == "custom"),
                        onClick = {
                            coroutineScope.launch { settingsRepository.setAiProvider("custom") }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = (aiProvider == "custom")) {
                                Icon(Icons.Rounded.Dns, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                            }
                        },
                        label = {
                            Text(stringResource(R.string.provider_custom), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Service status summary card (Material Design 3)
                val isConnected = when (aiProvider) {
                    "antigravity" -> antigravityAccessToken.isNotBlank()
                    "custom" -> customBaseUrl.isNotBlank()
                    else -> geminiKey.isNotBlank()
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = if (isConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = when (aiProvider) {
                                        "antigravity" -> "Google Antigravity"
                                        "custom" -> stringResource(R.string.custom_endpoint_title)
                                        else -> "Google AI Studio"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (aiProvider == "antigravity") {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = if (isRu) "Рекомендуется" else "Recommended",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = when (aiProvider) {
                                "antigravity" -> {
                                    if (antigravityAccessToken.isNotBlank()) {
                                        stringResource(R.string.ai_status_antigravity_connected, antigravityUserEmail.ifBlank { "Google" }, antigravityModel)
                                    } else {
                                        stringResource(R.string.ai_status_antigravity_not)
                                    }
                                }
                                "custom" -> {
                                    if (customBaseUrl.isNotBlank()) {
                                        val modelDisplay = customModel.ifBlank { "default" }
                                        if (isRu) "Подключено: $customBaseUrl ($modelDisplay)" else "Connected: $customBaseUrl ($modelDisplay)"
                                    } else {
                                        if (isRu) "Укажите Base URL и модель" else "Specify Base URL and model"
                                    }
                                }
                                else -> {
                                    if (geminiKey.isNotBlank()) {
                                        stringResource(R.string.ai_status_gemini_key_set, geminiModel)
                                    } else {
                                        stringResource(R.string.ai_status_gemini_key_not)
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))

                when (aiProvider) {
                    "antigravity" -> {
                        // Google Antigravity Configuration Panel
                    val isAuthenticated = antigravityAccessToken.isNotBlank()

                    if (!isAuthenticated) {
                        Text(
                            text = stringResource(R.string.antigravity_connect_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.antigravity_connect_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val url = authManager.buildAuthorizationUrl()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.antigravity_step_1))
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.antigravity_step_2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value = authCodeInput,
                            onValueChange = { authCodeInput = it },
                            placeholder = { Text("4/0AeanS0... или http://localhost:51121/...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        authCodeInput = clip
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.paste_button))
                                }
                            }
                        )

                        if (!authErrorMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = authErrorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isAuthenticating = true
                                    authErrorMessage = null
                                    val tokenRes = authManager.exchangeCodeForTokens(authCodeInput)
                                    if (tokenRes.isSuccess) {
                                        val tokenInfo = tokenRes.getOrThrow()
                                        val userRes = authManager.fetchUserInfo(tokenInfo.accessToken)
                                        val email = userRes.getOrNull()?.email ?: "Google Account"
                                        val name = userRes.getOrNull()?.name

                                        val quotaRes = authManager.fetchModelsAndQuota(tokenInfo.accessToken)
                                        val quotaInfo = quotaRes.getOrNull()
                                        val quotaPercent = quotaInfo?.let { "${(it.averageQuotaFraction * 100).toInt()}%" } ?: "100%"
                                        val resetTime = quotaInfo?.resetTime

                                        if (quotaInfo != null && quotaInfo.models.isNotEmpty()) {
                                            dynamicAntigravityModels = quotaInfo.models
                                        }

                                        settingsRepository.saveAntigravityAuth(
                                            accessToken = tokenInfo.accessToken,
                                            refreshToken = tokenInfo.refreshToken,
                                            email = email,
                                            name = name,
                                            quota = quotaPercent,
                                            resetTime = resetTime,
                                            summaryJson = quotaInfo?.rawSummaryJson
                                        )
                                        // Automatically set as active service!
                                        settingsRepository.setAiProvider("antigravity")
                                        authCodeInput = ""
                                    } else {
                                        authErrorMessage = tokenRes.exceptionOrNull()?.message ?: context.getString(R.string.failed_to_authorize)
                                    }
                                    isAuthenticating = false
                                }
                            },
                            enabled = authCodeInput.isNotBlank() && !isAuthenticating,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.authorizing))
                            } else {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.antigravity_step_3))
                            }
                        }
                    } else {
                        // User is authenticated in Antigravity
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = antigravityUserEmail.ifBlank { "Google Antigravity" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (antigravityUserName.isNotBlank()) {
                                            Text(
                                                text = antigravityUserName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.ai_connected),
                                                color = Color(0xFF2E7D32),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(14.dp))

                                // Quota info with full breakdown by model groups (Gemini, Claude & GPT)
                                if (quotaGroups.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        quotaGroups.forEach { group ->
                                            val isGeminiGroup = group.groupName.contains("Gemini", ignoreCase = true)
                                            val groupTitle = if (isGeminiGroup) stringResource(R.string.quota_gemini_group) else stringResource(R.string.quota_claude_group)
                                            val groupIcon = if (isGeminiGroup) Icons.Rounded.AutoAwesome else Icons.Rounded.SmartToy

                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = groupIcon,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = groupTitle,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    Spacer(Modifier.height(8.dp))

                                                    group.buckets.forEachIndexed { bIndex, bucket ->
                                                        if (bIndex > 0) {
                                                            Spacer(Modifier.height(8.dp))
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                                            Spacer(Modifier.height(8.dp))
                                                        }

                                                        val bucketTitle = when (bucket.window) {
                                                            "5h" -> stringResource(R.string.quota_5h)
                                                            "weekly" -> stringResource(R.string.quota_weekly)
                                                            else -> bucket.displayName
                                                        }

                                                        val percent = bucket.remainingPercent
                                                        val fraction = bucket.remainingFraction.coerceIn(0f, 1f)

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = bucketTitle,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            Text(
                                                                text = "$percent%",
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (percent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                            )
                                                        }

                                                        Spacer(Modifier.height(4.dp))

                                                        LinearProgressIndicator(
                                                            progress = { fraction },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(6.dp)
                                                                .clip(RoundedCornerShape(3.dp)),
                                                            color = if (percent < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                        )

                                                        if (!bucket.resetTime.isNullOrBlank()) {
                                                            Spacer(Modifier.height(4.dp))
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Schedule,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(
                                                                    text = stringResource(R.string.quota_reset_label, AntigravityAuthManager.formatResetTime(bucket.resetTime)),
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val quotaPercentInt = antigravityQuotaRemaining.removeSuffix("%").toIntOrNull() ?: 100
                                    val quotaFraction = quotaPercentInt / 100f

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.quota_remains_approx), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "$quotaPercentInt%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (quotaPercentInt < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    LinearProgressIndicator(
                                        progress = { quotaFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )

                                    if (antigravityQuotaResetTime.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Schedule,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.quota_reset_label, AntigravityAuthManager.formatResetTime(antigravityQuotaResetTime)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isRefreshingQuota = true
                                                var token = antigravityAccessToken
                                                val quotaRes = authManager.fetchModelsAndQuota(token)
                                                if (quotaRes.isSuccess) {
                                                    val qInfo = quotaRes.getOrThrow()
                                                    val quotaPercent = "${(qInfo.averageQuotaFraction * 100).toInt()}%"
                                                    settingsRepository.updateAntigravityQuota(quotaPercent, qInfo.resetTime, qInfo.rawSummaryJson)
                                                    if (qInfo.models.isNotEmpty()) {
                                                        dynamicAntigravityModels = qInfo.models
                                                    }
                                                } else if (antigravityRefreshToken.isNotBlank()) {
                                                    val refreshRes = authManager.refreshAccessToken(antigravityRefreshToken)
                                                    if (refreshRes.isSuccess) {
                                                        token = refreshRes.getOrThrow()
                                                        val q2 = authManager.fetchModelsAndQuota(token)
                                                        val qInfo = q2.getOrNull()
                                                        val quotaPercent = qInfo?.let { "${(it.averageQuotaFraction * 100).toInt()}%" } ?: "100%"
                                                        settingsRepository.saveAntigravityAuth(
                                                            accessToken = token,
                                                            refreshToken = antigravityRefreshToken,
                                                            email = antigravityUserEmail,
                                                            name = antigravityUserName,
                                                            quota = quotaPercent,
                                                            resetTime = qInfo?.resetTime,
                                                            summaryJson = qInfo?.rawSummaryJson
                                                        )
                                                    }
                                                }
                                                isRefreshingQuota = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isRefreshingQuota) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.refresh_quota), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsRepository.clearAntigravityAuth()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.disconnect), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Antigravity Model Picker
                        Text(stringResource(R.string.antigravity_model_choice), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        val selectedAgyObj = dynamicAntigravityModels.find { it.id == antigravityModel }
                        val agyDisplay = selectedAgyObj?.displayName ?: antigravityModel
                        val agyProvName = detectModelProvider(antigravityModel)
                        val agyProvColor = getProviderColor(agyProvName)
                        val agyProvIcon = getProviderIcon(agyProvName)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showAgyMenu = true }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(agyProvColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(agyProvIcon, contentDescription = null, tint = agyProvColor, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = agyDisplay, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Surface(
                                            color = agyProvColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(text = agyProvName, style = MaterialTheme.typography.labelSmall, color = agyProvColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (!selectedAgyObj?.description.isNullOrBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(text = selectedAgyObj!!.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(text = stringResource(R.string.current_model_label, antigravityModel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = if (isRu) "Выбрать" else "Select", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        if (showAgyMenu) {
                            val options = dynamicAntigravityModels.map {
                                ModelOption(id = it.id, displayName = it.displayName, description = it.description, provider = detectModelProvider(it.id))
                            }
                            ModelPickerDialog(
                                title = if (isRu) "Модели Antigravity" else "Antigravity Models",
                                subtitle = if (isRu) "Официальные и партнерские модели" else "Official & partner models",
                                models = options,
                                selectedModelId = antigravityModel,
                                allowManualInput = false,
                                isRu = isRu,
                                onModelSelected = { chosen ->
                                    coroutineScope.launch { settingsRepository.setAntigravityModel(chosen) }
                                },
                                onDismissRequest = { showAgyMenu = false }
                            )
                        }
                    }
                    }
                    "gemini_api" -> {
                        // Google AI Studio Configuration Panel
                        Text(
                            text = "Google AI Studio (Gemini API)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.gemini_studio_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = geminiKeyInput,
                            onValueChange = {
                                geminiKeyInput = it
                                coroutineScope.launch { settingsRepository.setGeminiApiKey(it.trim()) }
                            },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(stringResource(R.string.gemini_model_choice), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        val currentTitle = AntigravityAuthManager.DEFAULT_GEMINI_API_MODELS.find { it.first == geminiModel }?.second ?: geminiModel
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showGeminiMenu = true }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0D9488).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = currentTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Surface(
                                            color = Color(0xFF0D9488).copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(text = "Google", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0D9488), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(text = stringResource(R.string.current_model_label, geminiModel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = if (isRu) "Выбрать" else "Select", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        if (showGeminiMenu) {
                            val options = AntigravityAuthManager.DEFAULT_GEMINI_API_MODELS.map { (id, title) ->
                                ModelOption(id = id, displayName = title, description = "Google AI Studio", provider = "Google")
                            }
                            ModelPickerDialog(
                                title = if (isRu) "Модели Gemini API" else "Gemini API Models",
                                subtitle = "Google AI Studio",
                                models = options,
                                selectedModelId = geminiModel,
                                allowManualInput = false,
                                isRu = isRu,
                                onModelSelected = { chosen ->
                                    coroutineScope.launch { settingsRepository.setGeminiModel(chosen) }
                                },
                                onDismissRequest = { showGeminiMenu = false }
                            )
                        }
                    }
                    "custom" -> {
                        // Custom AI Endpoint Configuration Panel
                        Text(
                            text = stringResource(R.string.custom_endpoint_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.custom_endpoint_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))

                        // Base URL
                        OutlinedTextField(
                            value = customBaseUrlInput,
                            onValueChange = {
                                customBaseUrlInput = it
                                coroutineScope.launch { settingsRepository.setCustomAiBaseUrl(it.trim()) }
                            },
                            label = { Text(stringResource(R.string.custom_base_url_label)) },
                            placeholder = { Text(stringResource(R.string.custom_base_url_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        customBaseUrlInput = clip.trim()
                                        coroutineScope.launch { settingsRepository.setCustomAiBaseUrl(clip.trim()) }
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.paste_button))
                                }
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        // API Key with visibility toggle
                        OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = {
                                customApiKeyInput = it
                                coroutineScope.launch { settingsRepository.setCustomAiApiKey(it.trim()) }
                            },
                            label = { Text(stringResource(R.string.custom_api_key_label)) },
                            placeholder = { Text(stringResource(R.string.custom_api_key_hint)) },
                            singleLine = true,
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (isApiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                    IconButton(onClick = {
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            customApiKeyInput = clip.trim()
                                            coroutineScope.launch { settingsRepository.setCustomAiApiKey(clip.trim()) }
                                        }
                                    }) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = stringResource(R.string.paste_button))
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        // Auth Type selection
                        Text(
                            text = stringResource(R.string.custom_auth_type_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))

                        val authTypes = listOf("bearer" to "Bearer", "header" to "Header", "none" to "None")
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            authTypes.forEachIndexed { idx, (typeKey, typeLabel) ->
                                SegmentedButton(
                                    selected = (customAuthType == typeKey),
                                    onClick = {
                                        coroutineScope.launch { settingsRepository.setCustomAiAuthType(typeKey) }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = authTypes.size),
                                    label = { Text(typeLabel, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }

                        if (customAuthType == "header") {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customHeaderNameInput,
                                onValueChange = {
                                    customHeaderNameInput = it
                                    coroutineScope.launch { settingsRepository.setCustomAiHeaderName(it.trim()) }
                                },
                                label = { Text(stringResource(R.string.custom_header_name_label)) },
                                placeholder = { Text("api-key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Custom Models URL (Optional)
                        OutlinedTextField(
                            value = customModelsUrlInput,
                            onValueChange = {
                                customModelsUrlInput = it
                                coroutineScope.launch { settingsRepository.setCustomAiModelsUrl(it.trim()) }
                            },
                            label = { Text(stringResource(R.string.custom_models_url_label)) },
                            placeholder = { Text(stringResource(R.string.custom_models_url_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        // Fetch Models Button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isFetchingCustomModels = true
                                    customFetchStatusMessage = null
                                    customFetchError = null
                                    settingsRepository.setCustomAiBaseUrl(customBaseUrlInput.trim())
                                    settingsRepository.setCustomAiApiKey(customApiKeyInput.trim())
                                    settingsRepository.setCustomAiModelsUrl(customModelsUrlInput.trim())
                                    settingsRepository.setCustomAiHeaderName(customHeaderNameInput.trim())

                                    val res = customAiClient.fetchModels(
                                        baseUrl = customBaseUrlInput.trim(),
                                        apiKey = customApiKeyInput.trim(),
                                        authType = customAuthType,
                                        headerName = customHeaderNameInput.trim(),
                                        customModelsUrl = customModelsUrlInput.trim()
                                    )
                                    if (res.isSuccess) {
                                        val list = res.getOrThrow()
                                        settingsRepository.setCustomAiCachedModels(list)
                                        customFetchStatusMessage = context.getString(R.string.models_fetched_success, list.size)
                                        if (list.isNotEmpty() && customModelInput.isBlank()) {
                                            customModelInput = list.first()
                                            settingsRepository.setCustomAiModel(list.first())
                                        }
                                        if (list.isNotEmpty()) {
                                            showCustomModelMenu = true
                                        }
                                    } else {
                                        customFetchError = res.exceptionOrNull()?.localizedMessage ?: "Failed to fetch models"
                                    }
                                    isFetchingCustomModels = false
                                }
                            },
                            enabled = customBaseUrlInput.isNotBlank() && !isFetchingCustomModels,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isFetchingCustomModels) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.fetching_models))
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.fetch_models_btn))
                            }
                        }

                        if (!customFetchStatusMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = customFetchStatusMessage!!,
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (!customFetchError.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = customFetchError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Model Picker / Manual Entry
                        Text(
                            text = stringResource(R.string.select_custom_model),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))

                        val activeCustomModel = customModelInput.ifBlank { customCachedModels.firstOrNull() ?: "" }
                        val customProvName = if (activeCustomModel.isNotBlank()) detectModelProvider(activeCustomModel) else "Custom"
                        val customProvColor = getProviderColor(customProvName)
                        val customProvIcon = getProviderIcon(customProvName)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (customCachedModels.isNotEmpty()) {
                                        showCustomModelMenu = true
                                    }
                                }
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(customProvColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(customProvIcon, contentDescription = null, tint = customProvColor, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = activeCustomModel.ifBlank { if (isRu) "Модель не выбрана" else "No model selected" },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (activeCustomModel.isNotBlank()) {
                                            Surface(
                                                color = customProvColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = customProvName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = customProvColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (customCachedModels.isNotEmpty()) {
                                            if (isRu) "Нажмите для выбора из списка (${customCachedModels.size} моделей)"
                                            else "Tap to select from list (${customCachedModels.size} models)"
                                        } else {
                                            if (isRu) "Загрузите модели кнопкой выше или укажите вручную ниже"
                                            else "Fetch models above or enter identifier below"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isRu) "Выбрать" else "Select",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        if (showCustomModelMenu && customCachedModels.isNotEmpty()) {
                            val options = customCachedModels.map { ModelOption(id = it, displayName = it) }
                            ModelPickerDialog(
                                title = if (isRu) "Выберите модель ИИ" else "Select AI Model",
                                subtitle = customBaseUrlInput.ifBlank { "Custom Endpoint" },
                                models = options,
                                selectedModelId = activeCustomModel,
                                allowManualInput = true,
                                isRu = isRu,
                                onModelSelected = { chosen ->
                                    customModelInput = chosen
                                    coroutineScope.launch { settingsRepository.setCustomAiModel(chosen) }
                                },
                                onDismissRequest = { showCustomModelMenu = false }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Manual Model Input Fallback
                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = {
                                customModelInput = it
                                coroutineScope.launch { settingsRepository.setCustomAiModel(it.trim()) }
                            },
                            label = { Text(if (customCachedModels.isNotEmpty()) stringResource(R.string.custom_model_manual_hint) else stringResource(R.string.select_custom_model)) },
                            placeholder = { Text("gpt-4o, deepseek-chat, llama3:8b...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: App Update
        SettingsSectionHeader(title = stringResource(R.string.settings_updates_section), icon = Icons.Rounded.SystemUpdate)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PrismDE v1.0.0", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.repository_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onCheckUpdates,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.check_updates))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: Support / Donation
        SettingsSectionHeader(title = stringResource(R.string.settings_support_section), icon = Icons.Rounded.Favorite)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFE91E63).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.donate_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.donate_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showDonationSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open_donation_details))
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showDonationSheet) {
        DonationModalSheet(
            onDismiss = { showDonationSheet = false }
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
