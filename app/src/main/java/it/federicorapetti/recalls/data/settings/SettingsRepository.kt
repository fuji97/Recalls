package it.federicorapetti.recalls.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val INTERVAL_HOURS = intPreferencesKey("interval_hours")
        val NOTIFY_EU = booleanPreferencesKey("notify_eu")
        val NOTIFY_IT = booleanPreferencesKey("notify_it")
        val NOTIF_PERMISSION_ASKED = booleanPreferencesKey("notif_permission_asked")
    }

    val intervalHours: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.INTERVAL_HOURS] ?: DEFAULT_INTERVAL_HOURS }

    val notifyEu: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.NOTIFY_EU] ?: true }

    val notifyIt: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.NOTIFY_IT] ?: true }

    val notifPermissionAsked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.NOTIF_PERMISSION_ASKED] ?: false }

    suspend fun setIntervalHours(hours: Int) {
        context.settingsDataStore.edit { it[Keys.INTERVAL_HOURS] = hours }
    }

    suspend fun setNotifyEu(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFY_EU] = enabled }
    }

    suspend fun setNotifyIt(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFY_IT] = enabled }
    }

    suspend fun setNotifPermissionAsked(asked: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIF_PERMISSION_ASKED] = asked }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 6
    }
}
