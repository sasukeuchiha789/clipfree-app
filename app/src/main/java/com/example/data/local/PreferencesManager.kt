package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ProviderSwitchEvent
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clipfree_prefs")

class PreferencesManager(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val SMART_AI_CACHE = booleanPreferencesKey("smart_ai_cache")
        val CLOUD_SYNC_CONSENT = booleanPreferencesKey("cloud_sync_consent")
        val EXTERNAL_AI_CONSENT = booleanPreferencesKey("external_ai_consent")
        val GUEST_MODE = booleanPreferencesKey("guest_mode")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val LAST_SWITCH_FROM = stringPreferencesKey("last_switch_from")
        val LAST_SWITCH_TO = stringPreferencesKey("last_switch_to")
        val LAST_SWITCH_REASON = stringPreferencesKey("last_switch_reason")
        val LAST_SWITCH_TIME = longPreferencesKey("last_switch_time")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { raw ->
            ThemeMode.entries.firstOrNull { it.name == raw }
        } ?: ThemeMode.SYSTEM

        val switchFrom = prefs[Keys.LAST_SWITCH_FROM]
        val switchTo = prefs[Keys.LAST_SWITCH_TO]
        val switchReason = prefs[Keys.LAST_SWITCH_REASON]
        val switchTime = prefs[Keys.LAST_SWITCH_TIME] ?: 0L
        val lastSwitch = if (!switchFrom.isNullOrBlank() && !switchTo.isNullOrBlank()) {
            ProviderSwitchEvent(
                fromProvider = switchFrom,
                toProvider = switchTo,
                reason = switchReason.orEmpty(),
                timestampMs = switchTime
            )
        } else null

        UserPreferences(
            themeMode = theme,
            language = prefs[Keys.LANGUAGE] ?: "English",
            smartAiCachingEnabled = prefs[Keys.SMART_AI_CACHE] ?: true,
            cloudSyncConsent = prefs[Keys.CLOUD_SYNC_CONSENT] ?: false,
            externalAiProcessingConsent = prefs[Keys.EXTERNAL_AI_CONSENT] ?: true,
            isGuestModeActive = prefs[Keys.GUEST_MODE] ?: false,
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID] ?: "firebase_gemini_flash",
            lastSwitchEvent = lastSwitch
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setSmartAiCaching(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_AI_CACHE] = enabled }
    }

    suspend fun setCloudSyncConsent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_SYNC_CONSENT] = enabled }
    }

    suspend fun setExternalAiConsent(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXTERNAL_AI_CONSENT] = enabled }
    }

    suspend fun setGuestMode(active: Boolean) {
        context.dataStore.edit { it[Keys.GUEST_MODE] = active }
    }

    suspend fun setActiveProvider(providerId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PROVIDER_ID] = providerId }
    }

    suspend fun recordProviderSwitch(event: ProviderSwitchEvent) {
        context.dataStore.edit {
            it[Keys.ACTIVE_PROVIDER_ID] = event.toProvider
            it[Keys.LAST_SWITCH_FROM] = event.fromProvider
            it[Keys.LAST_SWITCH_TO] = event.toProvider
            it[Keys.LAST_SWITCH_REASON] = event.reason
            it[Keys.LAST_SWITCH_TIME] = event.timestampMs
        }
    }
}
