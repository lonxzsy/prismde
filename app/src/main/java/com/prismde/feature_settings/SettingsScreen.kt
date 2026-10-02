package com.prismde.feature_settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SystemUpdate
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    val darkMode by settingsRepository.darkModeFlow.collectAsState(initial = "system")
    val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)
    val activeNdk by settingsRepository.activeNdkFlow.collectAsState(initial = "r26c")
    val geminiKey by settingsRepository.geminiApiKeyFlow.collectAsState(initial = "")
    val fontSize by settingsRepository.editorFontSizeFlow.collectAsState(initial = 14f)
    val wordWrap by settingsRepository.editorWordWrapFlow.collectAsState(initial = false)

    val aiProvider by settingsRepository.aiProviderFlow.collectAsState(initial = "gemini_api")
    val geminiModel by settingsRepository.geminiModelFlow.collectAsState(initial = "gemini-2.5-flash")
    val antigravityModel by settingsRepository.antigravityModelFlow.collectAsState(initial = "gemini-3.8-flash-high")
    val antigravityAccessToken by settingsRepository.antigravityAccessTokenFlow.collectAsState(initial = "")
    val antigravityRefreshToken by settingsRepository.antigravityRefreshTokenFlow.collectAsState(initial = "")
    val antigravityUserEmail by settingsRepository.antigravityUserEmailFlow.collectAsState(initial = "")
    val antigravityUserName by settingsRepository.antigravityUserNameFlow.collectAsState(initial = "")
    val antigravityQuotaRemaining by settingsRepository.antigravityQuotaRemainingFlow.collectAsState(initial = "")
    val antigravityQuotaResetTime by settingsRepository.antigravityQuotaResetTimeFlow.collectAsState(initial = "")

    var geminiKeyInput by remember(geminiKey) { mutableStateOf(geminiKey) }
    var authCodeInput by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var isRefreshingQuota by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var dynamicAntigravityModels by remember { mutableStateOf(AntigravityAuthManager.DEFAULT_ANTIGRAVITY_MODELS) }

    var configTab by remember(aiProvider) { mutableIntStateOf(if (aiProvider == "antigravity") 0 else 1) }

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
                settingsRepository.updateAntigravityQuota(quotaPercent, qInfo.resetTime)
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
        SettingsSectionHeader(title = "Внешний вид и стиль", icon = Icons.Rounded.Palette)
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
                        Text("Динамические цвета (Material You)", style = MaterialTheme.typography.bodyLarge)
                        Text("Адаптация палитры под обои системы", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Тема оформления", style = MaterialTheme.typography.bodyLarge)
                        Text("Текущая: ${if (darkMode == "dark") "Тёмная" else if (darkMode == "light") "Светлая" else "Системная"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(if (darkMode == "dark") "Тёмная" else if (darkMode == "light") "Светлая" else "Авто")
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: Code Editor
        SettingsSectionHeader(title = "Редактор кода", icon = Icons.Rounded.Code)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Размер шрифта: ${fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
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
                        Text("Перенос длинных строк", style = MaterialTheme.typography.bodyLarge)
                        Text("Автоматический перенос строк без горизонтального скролла", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Менеджер версий NDK", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Активная версия: $activeNdk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: AI Assistant & Models
        SettingsSectionHeader(title = "AI Ассистент и Модели", icon = Icons.Rounded.AutoAwesome)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Dedicated Active Service Selector
                Text(
                    text = "Используемый AI сервис:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Выберите, какой сервис будет анализировать ошибки и предлагать исправления кода в проекте:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                // Option 1: Google Antigravity
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            coroutineScope.launch { settingsRepository.setAiProvider("antigravity") }
                            configTab = 0
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (aiProvider == "antigravity") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (aiProvider == "antigravity") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (aiProvider == "antigravity"),
                            onClick = {
                                coroutineScope.launch { settingsRepository.setAiProvider("antigravity") }
                                configTab = 0
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Google Antigravity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Рекомендуется", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "Модели поколения Gemini 3 и Claude с квотой Google аккаунта",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val authStatusText = if (antigravityAccessToken.isNotBlank()) "🟢 Подключен (${antigravityUserEmail.ifBlank { "Google" }})" else "⚪ Требуется вход ниже"
                            Text(
                                text = authStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (antigravityAccessToken.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Option 2: Google AI Studio
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            coroutineScope.launch { settingsRepository.setAiProvider("gemini_api") }
                            configTab = 1
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (aiProvider == "gemini_api") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (aiProvider == "gemini_api") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (aiProvider == "gemini_api"),
                            onClick = {
                                coroutineScope.launch { settingsRepository.setAiProvider("gemini_api") }
                                configTab = 1
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Google AI Studio (Gemini API)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Модели Gemini 2.5 / 2.0 через личный API-ключ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val keyStatusText = if (geminiKey.isNotBlank()) "🟢 API-ключ сохранен" else "⚪ Ключ не введен"
                            Text(
                                text = keyStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (geminiKey.isNotBlank()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Active status summary pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Сейчас активен: ${if (aiProvider == "antigravity") "Google Antigravity ($antigravityModel)" else "Google AI Studio ($geminiModel)"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(14.dp))

                // Tabs for configuration panels
                Text(
                    text = "Настройки параметров сервисов:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = configTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = configTab == 0,
                        onClick = { configTab = 0 },
                        text = { Text("Google Antigravity", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = configTab == 1,
                        onClick = { configTab = 1 },
                        text = { Text("Gemini API Key", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (configTab == 0) {
                    // Google Antigravity Configuration Panel
                    val isAuthenticated = antigravityAccessToken.isNotBlank()

                    if (!isAuthenticated) {
                        Text(
                            text = "Подключение к Google Antigravity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Интеграция с Google Antigravity открывает доступ к передовым моделям генерации кода Gemini 3 и Claude с автоматической квотой.",
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
                            Text("1. Войти в Google (Открыть браузер)")
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "2. Скопируйте код или ссылку из адресной строки браузера и вставьте сюда:",
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
                                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Вставить")
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
                                            resetTime = resetTime
                                        )
                                        // Automatically set as active service!
                                        settingsRepository.setAiProvider("antigravity")
                                        authCodeInput = ""
                                    } else {
                                        authErrorMessage = tokenRes.exceptionOrNull()?.message ?: "Не удалось авторизоваться"
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
                                Text("Авторизация...")
                            } else {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("3. Подключить Antigravity и активировать")
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
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Активен",
                                            color = Color(0xFF2E7D32),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(14.dp))

                                // Quota info
                                val quotaPercentInt = antigravityQuotaRemaining.removeSuffix("%").toIntOrNull() ?: 100
                                val quotaFraction = quotaPercentInt / 100f

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Остаток квоты токенов:", style = MaterialTheme.typography.bodyMedium)
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
                                    Text(
                                        text = "Сброс квоты: $antigravityQuotaResetTime",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                                    settingsRepository.updateAntigravityQuota(quotaPercent, qInfo.resetTime)
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
                                                            resetTime = qInfo?.resetTime
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
                                            Text("Обновить квоту", style = MaterialTheme.typography.labelMedium)
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
                                        Text("Выйти", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Antigravity Model Picker
                        Text("Модель генерации кода Antigravity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        var showAgyMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showAgyMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val display = dynamicAntigravityModels.find { it.id == antigravityModel }?.displayName ?: antigravityModel
                                        Text(text = display, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Идентификатор: $antigravityModel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = showAgyMenu,
                                onDismissRequest = { showAgyMenu = false }
                            ) {
                                dynamicAntigravityModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(m.displayName, fontWeight = FontWeight.SemiBold)
                                                Text(m.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsRepository.setAntigravityModel(m.id)
                                            }
                                            showAgyMenu = false
                                        },
                                        trailingIcon = {
                                            if (m.id == antigravityModel) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Google AI Studio Configuration Panel
                    Text(
                        text = "Google AI Studio (Gemini API)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Использует персональный API ключ от Google AI Studio (aistudio.google.com).",
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

                    Text("Модель Gemini", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))

                    var showGeminiMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showGeminiMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val currentTitle = AntigravityAuthManager.DEFAULT_GEMINI_API_MODELS.find { it.first == geminiModel }?.second ?: geminiModel
                                    Text(text = currentTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(text = "Идентификатор: $geminiModel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = showGeminiMenu,
                            onDismissRequest = { showGeminiMenu = false }
                        ) {
                            AntigravityAuthManager.DEFAULT_GEMINI_API_MODELS.forEach { (id, title) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(title, fontWeight = FontWeight.SemiBold)
                                            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            settingsRepository.setGeminiModel(id)
                                        }
                                        showGeminiMenu = false
                                    },
                                    trailingIcon = {
                                        if (id == geminiModel) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: App Update
        SettingsSectionHeader(title = "Обновления приложения", icon = Icons.Rounded.SystemUpdate)
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
                    Text("Репозиторий: github.com/lonxzsy/prismde", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onCheckUpdates,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Проверить")
                }
            }
        }

        Spacer(Modifier.height(40.dp))
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
