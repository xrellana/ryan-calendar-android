package com.ryancalendar.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryancalendar.android.data.remote.EvalSettingsDto
import com.ryancalendar.android.data.remote.UserDto
import com.ryancalendar.android.domain.eval.EvalSettings
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
        val CACHED_USER_JSON = stringPreferencesKey("cached_user_json")
        val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
        val SERVER_UPDATED_AT = stringPreferencesKey("server_updated_at")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val EVAL_SETTINGS_JSON = stringPreferencesKey("eval_settings_json")
        val EVAL_MERGE_NAMES = booleanPreferencesKey("eval_merge_names")
        val EVAL_LOADED = booleanPreferencesKey("eval_loaded")
        val EVAL_TRAVEL_MERGE = booleanPreferencesKey("eval_travel_merge")
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

    data class EvalMeta(val settings: EvalSettings, val mergeNames: Boolean, val loaded: Boolean)

    val evalMetaFlow: Flow<EvalMeta> = context.dataStore.data.map { prefs ->
        val settings = prefs[Keys.EVAL_SETTINGS_JSON]?.let { raw ->
            try {
                json.decodeFromString<EvalSettingsDto>(raw).toDomain()
            } catch (_: Exception) {
                null
            }
        } ?: EvalSettings.Default
        EvalMeta(
            settings = settings,
            mergeNames = prefs[Keys.EVAL_MERGE_NAMES] ?: true,
            loaded = prefs[Keys.EVAL_LOADED] ?: false
        )
    }

    /** Device-local preference; the web client keeps the same toggle in localStorage. */
    val evalTravelMergeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.EVAL_TRAVEL_MERGE] ?: true
    }

    suspend fun saveEvalMeta(settings: EvalSettingsDto, mergeNames: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EVAL_SETTINGS_JSON] = json.encodeToString(settings)
            prefs[Keys.EVAL_MERGE_NAMES] = mergeNames
            prefs[Keys.EVAL_LOADED] = true
        }
    }

    suspend fun saveEvalMergeNames(mergeNames: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.EVAL_MERGE_NAMES] = mergeNames }
    }

    suspend fun saveEvalTravelMerge(merge: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.EVAL_TRAVEL_MERGE] = merge }
    }

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
            prefs.remove(Keys.EVAL_SETTINGS_JSON)
            prefs.remove(Keys.EVAL_MERGE_NAMES)
            prefs.remove(Keys.EVAL_LOADED)
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
}
