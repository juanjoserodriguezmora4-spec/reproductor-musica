package com.bismopower.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val theme: String = "SYSTEM",
    val colorTheme: String = "BRUMA",
    val reduceTransparency: Boolean = false,
    val lastScanAt: Long = 0,
    /** true = solo se escanean las carpetas de [musicFolders] (y sus subcarpetas). */
    val onlyChosenFolders: Boolean = false,
    val musicFolders: Set<String> = emptySet(),
)

class SettingsRepository(
    private val context: Context,
) {
    companion object Keys {
        val THEME = stringPreferencesKey("theme")
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        val LAST_SCAN_AT = longPreferencesKey("last_scan_at")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val ONLY_CHOSEN_FOLDERS = booleanPreferencesKey("only_chosen_folders")
        val MUSIC_FOLDERS = stringSetPreferencesKey("music_folders")
    }

    val flow: Flow<AppSettings> =
        context.dataStore.data.map { p ->
            val d = AppSettings()
            AppSettings(
                theme = p[THEME] ?: d.theme,
                colorTheme = p[COLOR_THEME] ?: d.colorTheme,
                reduceTransparency = p[REDUCE_TRANSPARENCY] ?: d.reduceTransparency,
                lastScanAt = p[LAST_SCAN_AT] ?: d.lastScanAt,
                onlyChosenFolders = p[ONLY_CHOSEN_FOLDERS] ?: d.onlyChosenFolders,
                musicFolders = p[MUSIC_FOLDERS] ?: d.musicFolders,
            )
        }

    suspend fun current(): AppSettings = flow.first()

    /** Guarda de una vez qué carpetas se escanean. */
    suspend fun setFolders(
        onlyChosen: Boolean,
        folders: Set<String>,
    ) {
        context.dataStore.edit {
            it[ONLY_CHOSEN_FOLDERS] = onlyChosen
            it[MUSIC_FOLDERS] = folders
        }
    }

    suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        context.dataStore.edit { it[key] = value }
    }
}
