package com.prismde.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.prismde.feature_build.engine.AiConfig
import kotlinx.coroutines.flow.combine
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prism_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // "system", "dark", "light"
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_ACTIVE_NDK = stringPreferencesKey("active_ndk")
        val KEY_CUSTOM_NDK_URL = stringPreferencesKey("custom_ndk_url")
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val KEY_EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val KEY_EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val KEY_LAST_PROJECT_PATH = stringPreferencesKey("last_project_path")
        val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        
        // AI Preferences
        val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider") // "gemini_api" or "antigravity"
        val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val KEY_ANTIGRAVITY_MODEL = stringPreferencesKey("antigravity_model")
        val KEY_ANTIGRAVITY_ACCESS_TOKEN = stringPreferencesKey("antigravity_access_token")
        val KEY_ANTIGRAVITY_REFRESH_TOKEN = stringPreferencesKey("antigravity_refresh_token")
        val KEY_ANTIGRAVITY_USER_EMAIL = stringPreferencesKey("antigravity_user_email")
        val KEY_ANTIGRAVITY_USER_NAME = stringPreferencesKey("antigravity_user_name")
        val KEY_ANTIGRAVITY_QUOTA_REMAINING = stringPreferencesKey("antigravity_quota_remaining")
        val KEY_ANTIGRAVITY_QUOTA_RESET_TIME = stringPreferencesKey("antigravity_quota_reset_time")
        val KEY_ANTIGRAVITY_QUOTA_SUMMARY_JSON = stringPreferencesKey("antigravity_quota_summary_json")

        // Custom AI Endpoint Preferences
        val KEY_CUSTOM_AI_BASE_URL = stringPreferencesKey("custom_ai_base_url")
        val KEY_CUSTOM_AI_API_KEY = stringPreferencesKey("custom_ai_api_key")
        val KEY_CUSTOM_AI_MODEL = stringPreferencesKey("custom_ai_model")
        val KEY_CUSTOM_AI_MODELS_URL = stringPreferencesKey("custom_ai_models_url")
        val KEY_CUSTOM_AI_AUTH_TYPE = stringPreferencesKey("custom_ai_auth_type") // "bearer", "header", "none"
        val KEY_CUSTOM_AI_HEADER_NAME = stringPreferencesKey("custom_ai_header_name")
        val KEY_CUSTOM_AI_CACHED_MODELS = stringPreferencesKey("custom_ai_cached_models")
    }

    private val syncPrefs = context.getSharedPreferences("prism_sync_state", Context.MODE_PRIVATE)

    val isSetupCompletedSync: Boolean
        get() = syncPrefs.getBoolean("setup_completed", false)

    val isSetupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { 
        val completed = it[KEY_SETUP_COMPLETED] ?: false
        if (completed && !syncPrefs.getBoolean("setup_completed", false)) {
            syncPrefs.edit().putBoolean("setup_completed", true).apply()
        }
        completed
    }
    val darkModeFlow: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "system" }
    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val activeNdkFlow: Flow<String> = context.dataStore.data.map { it[KEY_ACTIVE_NDK] ?: "r26c" }
    val customNdkUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_NDK_URL] ?: "" }
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { it[KEY_GEMINI_API_KEY] ?: "" }
    val editorFontSizeFlow: Flow<Float> = context.dataStore.data.map { it[KEY_EDITOR_FONT_SIZE] ?: 14f }
    val editorTabSizeFlow: Flow<Int> = context.dataStore.data.map { it[KEY_EDITOR_TAB_SIZE] ?: 4 }
    val editorWordWrapFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_EDITOR_WORD_WRAP] ?: false }
    val lastProjectPathFlow: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_PROJECT_PATH] }

    val aiProviderFlow: Flow<String> = context.dataStore.data.map { it[KEY_AI_PROVIDER] ?: "gemini_api" }
    val geminiModelFlow: Flow<String> = context.dataStore.data.map { it[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash" }
    val antigravityModelFlow: Flow<String> = context.dataStore.data.map {
        val model = it[KEY_ANTIGRAVITY_MODEL] ?: "gemini-3.8-flash"
        val validIds = setOf(
            "gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.1-pro",
            "claude-sonnet-4.6", "claude-opus-4.6", "gpt-oss-120b"
        )
        if (model !in validIds) {
            when (model) {
                "gemini-3.6-flash-high", "gemini-3.6-flash-medium", "gemini-3.6-flash-low" -> "gemini-3.6-flash"
                "gemini-pro-agent", "gemini-3.1-pro-low", "gemini-3-pro" -> "gemini-3.1-pro"
                "claude-sonnet-4-6", "claude-sonnet-4-20250514" -> "claude-sonnet-4.6"
                "claude-opus-4-6-thinking", "claude-opus-4.5" -> "claude-opus-4.6"
                "gpt-oss-120b-medium" -> "gpt-oss-120b"
                else -> "gemini-3.8-flash"
            }
        } else {
            model
        }
    }
    val antigravityAccessTokenFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_ACCESS_TOKEN] ?: "" }
    val antigravityRefreshTokenFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_REFRESH_TOKEN] ?: "" }
    val antigravityUserEmailFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_USER_EMAIL] ?: "" }
    val antigravityUserNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_USER_NAME] ?: "" }
    val antigravityQuotaRemainingFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_QUOTA_REMAINING] ?: "" }
    val antigravityQuotaResetTimeFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_QUOTA_RESET_TIME] ?: "" }
    val antigravityQuotaSummaryJsonFlow: Flow<String?> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_QUOTA_SUMMARY_JSON] }

    val customAiBaseUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_BASE_URL] ?: "" }
    val customAiApiKeyFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_API_KEY] ?: "" }
    val customAiModelFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_MODEL] ?: "" }
    val customAiModelsUrlFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_MODELS_URL] ?: "" }
    val customAiAuthTypeFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_AUTH_TYPE] ?: "bearer" }
    val customAiHeaderNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_CUSTOM_AI_HEADER_NAME] ?: "Authorization" }
    val customAiCachedModelsFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_AI_CACHED_MODELS] ?: ""
        if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    val aiConfigFlow: Flow<AiConfig> = context.dataStore.data.map { prefs ->
        val provider = prefs[KEY_AI_PROVIDER] ?: "gemini_api"
        val apiKey = prefs[KEY_GEMINI_API_KEY] ?: ""
        val gModel = prefs[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash"
        var aModel = prefs[KEY_ANTIGRAVITY_MODEL] ?: "gemini-3.6-flash-high"
        if (aModel.startsWith("gemini-2") || aModel.startsWith("gemini-1") ||
            aModel == "gemini-3.8-flash-high" || aModel == "gemini-3.7-flash-medium" ||
            aModel == "gemini-3.7-flash-high" || aModel.isBlank()) {
            aModel = "gemini-3.6-flash-high"
        }
        val token = prefs[KEY_ANTIGRAVITY_ACCESS_TOKEN] ?: ""
        val rToken = prefs[KEY_ANTIGRAVITY_REFRESH_TOKEN] ?: ""

        val cBaseUrl = prefs[KEY_CUSTOM_AI_BASE_URL] ?: ""
        val cApiKey = prefs[KEY_CUSTOM_AI_API_KEY] ?: ""
        val cModel = prefs[KEY_CUSTOM_AI_MODEL] ?: ""
        val cModelsUrl = prefs[KEY_CUSTOM_AI_MODELS_URL] ?: ""
        val cAuthType = prefs[KEY_CUSTOM_AI_AUTH_TYPE] ?: "bearer"
        val cHeaderName = prefs[KEY_CUSTOM_AI_HEADER_NAME] ?: "Authorization"

        val chosenModel = when (provider) {
            "antigravity" -> aModel
            "custom" -> cModel.ifBlank { "gpt-4o" }
            else -> gModel
        }

        AiConfig(
            provider = provider,
            apiKey = apiKey,
            model = chosenModel,
            antigravityAccessToken = token,
            antigravityRefreshToken = rToken,
            customBaseUrl = cBaseUrl,
            customApiKey = cApiKey,
            customModel = cModel,
            customModelsUrl = cModelsUrl,
            customAuthType = cAuthType,
            customHeaderName = cHeaderName
        )
    }

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { it[KEY_GEMINI_MODEL] = model }
    }

    suspend fun setCustomAiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_BASE_URL] = url }
    }

    suspend fun setCustomAiApiKey(key: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_API_KEY] = key }
    }

    suspend fun setCustomAiModel(model: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_MODEL] = model }
    }

    suspend fun setCustomAiModelsUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_MODELS_URL] = url }
    }

    suspend fun setCustomAiAuthType(authType: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_AUTH_TYPE] = authType }
    }

    suspend fun setCustomAiHeaderName(header: String) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_HEADER_NAME] = header }
    }

    suspend fun setCustomAiCachedModels(models: List<String>) {
        context.dataStore.edit { it[KEY_CUSTOM_AI_CACHED_MODELS] = models.joinToString(",") }
    }

    suspend fun setAntigravityModel(model: String) {
        context.dataStore.edit { it[KEY_ANTIGRAVITY_MODEL] = model }
    }

    suspend fun saveAntigravityAuth(
        accessToken: String,
        refreshToken: String?,
        email: String,
        name: String?,
        quota: String? = null,
        resetTime: String? = null,
        summaryJson: String? = null
    ) {
        context.dataStore.edit {
            it[KEY_ANTIGRAVITY_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) {
                it[KEY_ANTIGRAVITY_REFRESH_TOKEN] = refreshToken
            }
            it[KEY_ANTIGRAVITY_USER_EMAIL] = email
            it[KEY_ANTIGRAVITY_USER_NAME] = name ?: ""
            if (quota != null) it[KEY_ANTIGRAVITY_QUOTA_REMAINING] = quota
            if (resetTime != null) it[KEY_ANTIGRAVITY_QUOTA_RESET_TIME] = resetTime
            if (summaryJson != null) it[KEY_ANTIGRAVITY_QUOTA_SUMMARY_JSON] = summaryJson
            it[KEY_AI_PROVIDER] = "antigravity"
        }
    }

    suspend fun updateAntigravityQuota(quota: String, resetTime: String?, summaryJson: String? = null) {
        context.dataStore.edit {
            it[KEY_ANTIGRAVITY_QUOTA_REMAINING] = quota
            if (resetTime != null) it[KEY_ANTIGRAVITY_QUOTA_RESET_TIME] = resetTime
            if (summaryJson != null) it[KEY_ANTIGRAVITY_QUOTA_SUMMARY_JSON] = summaryJson
        }
    }

    suspend fun clearAntigravityAuth() {
        context.dataStore.edit {
            it.remove(KEY_ANTIGRAVITY_ACCESS_TOKEN)
            it.remove(KEY_ANTIGRAVITY_REFRESH_TOKEN)
            it.remove(KEY_ANTIGRAVITY_USER_EMAIL)
            it.remove(KEY_ANTIGRAVITY_USER_NAME)
            it.remove(KEY_ANTIGRAVITY_QUOTA_REMAINING)
            it.remove(KEY_ANTIGRAVITY_QUOTA_RESET_TIME)
            it.remove(KEY_ANTIGRAVITY_QUOTA_SUMMARY_JSON)
            it[KEY_AI_PROVIDER] = "gemini_api"
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setActiveNdk(tag: String) {
        context.dataStore.edit { it[KEY_ACTIVE_NDK] = tag }
    }

    suspend fun setCustomNdkUrl(url: String) {
        context.dataStore.edit { it[KEY_CUSTOM_NDK_URL] = url }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setEditorFontSize(size: Float) {
        context.dataStore.edit { it[KEY_EDITOR_FONT_SIZE] = size }
    }

    suspend fun setEditorTabSize(tabSize: Int) {
        context.dataStore.edit { it[KEY_EDITOR_TAB_SIZE] = tabSize }
    }

    suspend fun setEditorWordWrap(wrap: Boolean) {
        context.dataStore.edit { it[KEY_EDITOR_WORD_WRAP] = wrap }
    }

    suspend fun setLastProjectPath(path: String) {
        context.dataStore.edit { it[KEY_LAST_PROJECT_PATH] = path }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        syncPrefs.edit().putBoolean("setup_completed", completed).apply()
        context.dataStore.edit { it[KEY_SETUP_COMPLETED] = completed }
    }

    fun markSetupCompletedSync() {
        syncPrefs.edit().putBoolean("setup_completed", true).apply()
    }
}
