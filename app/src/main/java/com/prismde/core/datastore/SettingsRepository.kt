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
    val antigravityModelFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_MODEL] ?: "gemini-3.8-flash-high" }
    val antigravityAccessTokenFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_ACCESS_TOKEN] ?: "" }
    val antigravityRefreshTokenFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_REFRESH_TOKEN] ?: "" }
    val antigravityUserEmailFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_USER_EMAIL] ?: "" }
    val antigravityUserNameFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_USER_NAME] ?: "" }
    val antigravityQuotaRemainingFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_QUOTA_REMAINING] ?: "" }
    val antigravityQuotaResetTimeFlow: Flow<String> = context.dataStore.data.map { it[KEY_ANTIGRAVITY_QUOTA_RESET_TIME] ?: "" }

    val aiConfigFlow: Flow<AiConfig> = context.dataStore.data.map { prefs ->
        val provider = prefs[KEY_AI_PROVIDER] ?: "gemini_api"
        val apiKey = prefs[KEY_GEMINI_API_KEY] ?: ""
        val gModel = prefs[KEY_GEMINI_MODEL] ?: "gemini-2.5-flash"
        val aModel = prefs[KEY_ANTIGRAVITY_MODEL] ?: "gemini-3.8-flash-high"
        val token = prefs[KEY_ANTIGRAVITY_ACCESS_TOKEN] ?: ""
        val rToken = prefs[KEY_ANTIGRAVITY_REFRESH_TOKEN] ?: ""

        val chosenModel = if (provider == "antigravity") aModel else gModel
        AiConfig(
            provider = provider,
            apiKey = apiKey,
            model = chosenModel,
            antigravityAccessToken = token,
            antigravityRefreshToken = rToken
        )
    }

    suspend fun setAiProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { it[KEY_GEMINI_MODEL] = model }
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
        resetTime: String? = null
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
            it[KEY_AI_PROVIDER] = "antigravity"
        }
    }

    suspend fun updateAntigravityQuota(quota: String, resetTime: String?) {
        context.dataStore.edit {
            it[KEY_ANTIGRAVITY_QUOTA_REMAINING] = quota
            if (resetTime != null) it[KEY_ANTIGRAVITY_QUOTA_RESET_TIME] = resetTime
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
