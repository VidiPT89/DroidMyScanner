package dev.ividi.myscanner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * Persists theme, language and first-launch state across app restarts.
 */
class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SCAN_FALLBACK_SHOWN = booleanPreferencesKey("scan_fallback_shown")
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: AppThemeMode.DARK.name
        runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.DARK)
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.name
        runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.ENGLISH)
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    val scanFallbackShown: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SCAN_FALLBACK_SHOWN] ?: false
    }

    suspend fun setScanFallbackShown(shown: Boolean) {
        context.dataStore.edit { it[Keys.SCAN_FALLBACK_SHOWN] = shown }
    }
}
