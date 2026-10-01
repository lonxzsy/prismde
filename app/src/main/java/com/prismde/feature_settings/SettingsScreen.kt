package com.prismde.feature_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prismde.core.datastore.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateNdkManager: () -> Unit,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    val darkMode by settingsRepository.darkModeFlow.collectAsState(initial = "system")
    val dynamicColor by settingsRepository.dynamicColorFlow.collectAsState(initial = true)
    val activeNdk by settingsRepository.activeNdkFlow.collectAsState(initial = "r26c")
    val geminiKey by settingsRepository.geminiApiKeyFlow.collectAsState(initial = "")
    val fontSize by settingsRepository.editorFontSizeFlow.collectAsState(initial = 14f)
    val wordWrap by settingsRepository.editorWordWrapFlow.collectAsState(initial = false)

    var geminiKeyInput by remember(geminiKey) { mutableStateOf(geminiKey) }

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
                Icon(Icons.Rounded.NavigateNext, contentDescription = null)
            }
        }

        Spacer(Modifier.height(20.dp))

        // SECTION: AI Assistant
        SettingsSectionHeader(title = "AI Ассистент (Опционально)", icon = Icons.Rounded.AutoAwesome)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Google Gemini API Key",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Используется для подробного анализа сложных ошибок компилятора прямо в карточке сборки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = {
                        geminiKeyInput = it
                        coroutineScope.launch { settingsRepository.setGeminiApiKey(it.trim()) }
                    },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
