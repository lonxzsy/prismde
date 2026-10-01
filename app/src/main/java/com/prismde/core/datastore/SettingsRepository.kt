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
}
