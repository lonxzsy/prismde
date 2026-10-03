package com.prismde.feature_settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val provider: String = detectModelProvider(id)
)

fun detectModelProvider(modelId: String): String {
    val lower = modelId.lowercase()
    return when {
        lower.contains("claude") || lower.contains("anthropic") -> "Anthropic"
        lower.contains("deepseek") -> "DeepSeek"
        lower.contains("gemini") || lower.contains("google") -> "Google"
        lower.contains("gpt") || lower.contains("openai") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") || lower.contains("chatgpt") -> "OpenAI"
        lower.contains("llama") || lower.contains("meta") -> "Meta"
        lower.contains("mistral") || lower.contains("codestral") -> "Mistral"
        lower.contains("qwen") -> "Alibaba"
        lower.contains("grok") || lower.contains("xai") -> "xAI"
        lower.contains("composer") -> "Composer"
        else -> "Other"
    }
}

fun getProviderColor(provider: String): Color {
    return when (provider) {
        "Anthropic" -> Color(0xFFD97706) // Amber/Orange
        "DeepSeek" -> Color(0xFF0284C7)  // Deep Sky Blue
        "Google" -> Color(0xFF0D9488)    // Teal/Cyan
        "OpenAI" -> Color(0xFF10B981)    // Emerald Green
        "Meta" -> Color(0xFF6366F1)      // Indigo
        "Mistral" -> Color(0xFFEA580C)   // Sunset Orange
        "Alibaba" -> Color(0xFF8B5CF6)   // Purple
        "xAI" -> Color(0xFF64748B)       // Slate
        else -> Color(0xFF6B7280)        // Gray
    }
}

fun getProviderIcon(provider: String): ImageVector {
    return when (provider) {
        "Anthropic" -> Icons.Rounded.Psychology
        "DeepSeek" -> Icons.Rounded.Code
        "Google" -> Icons.Rounded.AutoAwesome
        "OpenAI" -> Icons.Rounded.Bolt
        "Meta" -> Icons.Rounded.Hub
        "Mistral" -> Icons.Rounded.Air
        "Alibaba" -> Icons.Rounded.Cloud
        else -> Icons.Rounded.SmartToy
    }
}

@Composable
fun ModelPickerDialog(
    title: String,
    subtitle: String? = null,
    models: List<ModelOption>,
    selectedModelId: String,
    allowManualInput: Boolean = true,
    isRu: Boolean = java.util.Locale.getDefault().language == "ru",
    onModelSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var manualInputMode by remember { mutableStateOf(false) }
    var manualModelText by remember { mutableStateOf("") }

    // Group available categories
    val categories = remember(models) {
        val provs = models.map { it.provider }.distinct().sorted()
        listOf("All") + provs
    }

    val filteredModels = remember(models, searchQuery, selectedCategory) {
        models.filter { model ->
            val matchesCategory = selectedCategory == "All" || model.provider == selectedCategory
            val matchesSearch = if (searchQuery.isBlank()) true else {
                model.id.contains(searchQuery, ignoreCase = true) ||
                        model.displayName.contains(searchQuery, ignoreCase = true) ||
                        model.provider.contains(searchQuery, ignoreCase = true) ||
                        (model.description?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${models.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(if (isRu) "Поиск модели (например, claude, deepseek)..." else "Search model (e.g. claude, deepseek)...")
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Chips (if there are multiple providers)
                if (categories.size > 2) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { category ->
                            val isSelected = selectedCategory == category
                            val count = if (category == "All") models.size else models.count { it.provider == category }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = "$category ($count)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Model List
                Box(modifier = Modifier.weight(1f)) {
                    if (filteredModels.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isRu) "Модели не найдены" else "No models found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (allowManualInput) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { manualInputMode = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isRu) "Указать вручную" else "Enter manually")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(filteredModels, key = { it.id }) { model ->
                                val isSelected = model.id == selectedModelId
                                val provColor = getProviderColor(model.provider)
                                val provIcon = getProviderIcon(model.provider)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            onModelSelected(model.id)
                                            onDismissRequest()
                                        }
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Provider Icon Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(provColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = provIcon,
                                                contentDescription = null,
                                                tint = provColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        // Model info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = model.displayName.ifBlank { model.id },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                // Provider tag pill
                                                Surface(
                                                    color = provColor.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = model.provider,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = provColor,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            if (!model.description.isNullOrBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = model.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (model.displayName.isNotBlank() && model.displayName != model.id) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = model.id,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        // Selection checkmark or radio
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else {
                                            RadioButton(
                                                selected = false,
                                                onClick = {
                                                    onModelSelected(model.id)
                                                    onDismissRequest()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Manual Input section if enabled
                AnimatedVisibility(visible = manualInputMode && allowManualInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        OutlinedTextField(
                            value = manualModelText,
                            onValueChange = { manualModelText = it },
                            label = { Text(if (isRu) "Имя модели вручную" else "Custom model identifier") },
                            placeholder = { Text("gpt-4o, deepseek-chat, custom-model...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { manualInputMode = false }) {
                                Text(if (isRu) "Отмена" else "Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val trimmed = manualModelText.trim()
                                    if (trimmed.isNotBlank()) {
                                        onModelSelected(trimmed)
                                        onDismissRequest()
                                    }
                                },
                                enabled = manualModelText.isNotBlank(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(if (isRu) "Применить" else "Apply")
                            }
                        }
                    }
                }

                // Bottom bar
                if (!manualInputMode && allowManualInput) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                manualInputMode = true
                                manualModelText = selectedModelId
                            }
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isRu) "Ввести модель вручную" else "Enter custom name")
                        }

                        Button(
                            onClick = onDismissRequest,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isRu) "Закрыть" else "Close")
                        }
                    }
                }
            }
        }
    }
}
