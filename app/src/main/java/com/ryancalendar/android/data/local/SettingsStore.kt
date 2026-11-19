package com.ryancalendar.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryancalendar.android.data.remote.UserDto
import com.ryancalendar.android.domain.model.ReminderSettings
import com.ryancalendar.android.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ryan_calendar_prefs")

class SettingsStore(
    private val context: Context,
    private val json: Json
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val CACHED_USER_JSON = stringPreferencesKey("cached_user_json")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        val SERVER_UPDATED_AT = stringPreferencesKey("server_updated_at")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    }

    @Volatile
    private var cachedBaseUrl: String = ""

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVER_URL].orEmpty()
    }

    val cachedUserFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.CACHED_USER_JSON] ?: return@map null
        try {
            json.decodeFromString<UserDto>(raw).toDomain()
        } catch (_: Exception) {
            null
        }
    }

    val lastSyncMsFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_SYNC_MS] ?: 0L
    }

    val reminderSettingsFlow: Flow<ReminderSettings> = context.dataStore.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            hour = prefs[Keys.REMINDER_HOUR] ?: 8,
            minute = prefs[Keys.REMINDER_MINUTE] ?: 30
        )
    }

    suspend fun initCache() {
        cachedBaseUrl = serverUrlFlow.first()
    }

    fun getCachedServerUrlSync(): String = cachedBaseUrl

    suspend fun getServerUrl(): String {
        val url = serverUrlFlow.first()
        cachedBaseUrl = url
        return url
    }

    suspend fun setServerUrl(rawUrl: String): String {
        val normalized = normalizeServerUrl(rawUrl)
        cachedBaseUrl = normalized
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = normalized
        }
        return normalized
    }

    suspend fun saveUser(user: UserProfile?) {
        context.dataStore.edit { prefs ->
            if (user == null) {
                prefs.remove(Keys.CACHED_USER_JSON)
            } else {
                val dto = UserDto(
                    id = user.id,
                    username = user.username,
                    displayName = user.displayName,
                    role = user.role,
                    isActive = user.isActive,
                    icsToken = user.icsToken,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt,
                    lastLoginAt = user.lastLoginAt
                )
                prefs[Keys.CACHED_USER_JSON] = json.encodeToString(dto)
            }
        }
    }

    suspend fun getCachedUser(): UserProfile? = cachedUserFlow.first()

    suspend fun saveSyncMeta(lastSyncMs: Long, serverUpdatedAt: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_MS] = lastSyncMs
            prefs[Keys.SERVER_UPDATED_AT] = serverUpdatedAt
        }
    }

    suspend fun getServerUpdatedAt(): String =
        context.dataStore.data.map { it[Keys.SERVER_UPDATED_AT].orEmpty() }.first()

    suspend fun clearSessionMeta() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CACHED_USER_JSON)
            prefs.remove(Keys.LAST_SYNC_MS)
            prefs.remove(Keys.SERVER_UPDATED_AT)
        }
    }

    suspend fun saveReminderSettings(settings: ReminderSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REMINDER_ENABLED] = settings.enabled
            prefs[Keys.REMINDER_HOUR] = settings.hour.coerceIn(0, 23)
            prefs[Keys.REMINDER_MINUTE] = settings.minute.coerceIn(0, 59)
        }
    }

    suspend fun getReminderSettings(): ReminderSettings = reminderSettingsFlow.first()

    companion object {
        fun normalizeServerUrl(input: String): String {
            var url = input.trim()
            if (url.isEmpty()) return ""
            if (!url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                url = "https://$url"
            }
            return url.trimEnd('/')
        }
    }
}
