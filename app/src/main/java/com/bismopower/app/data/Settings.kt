package com.bismopower.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val theme: String = "SYSTEM",
    val reduceTransparency: Boolean = false,
    val lastScanAt: Long = 0,
)

class SettingsRepository(
    private val context: Context,
) {
    companion object Keys {
        val THEME = stringPreferencesKey("theme")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
    }

    val flow: Flow<AppSettings> =
        context.dataStore.data.map { p ->
            val d = AppSettings()
            AppSettings(
                theme = p[THEME] ?: d.theme,
                reduceTransparency = p[REDUCE_TRANSPARENCY] ?: d.reduceTransparency,
                lastScanAt = p[LAST_SCAN_AT] ?: d.lastScanAt,
            )
        }

    suspend fun current(): AppSettings = flow.first()

    suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        context.dataStore.edit { it[key] = value }
    }
}
