package com.bismopower.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    /** Segundos de fundido entre canciones; 0 = apagado (las canciones se unen sin pausas). */
    val crossfadeSec: Int = 0,
    /** Carpeta elegida por el usuario donde guarda sus archivos .lrc (dirección de Android). */
    val lyricsFolder: String? = null,
    /** Orden de la lista de Canciones: TITLE, ARTIST, ALBUM, DATE, DURATION o SHUFFLE. */
    val songSort: String = "TITLE",
    /** Semilla de la mezcla: la lista mezclada queda igual hasta que pidas mezclar otra vez. */
    val shuffleSeed: Long = 0,
    /** Qué se ve en el centro del reproductor: COVER, LYRICS o VIZ (visualizador). */
    val playerView: String = "COVER",
    val vizStyle: String = "RING",
    val vizPalette: String = "ARTWORK",
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
        val CROSSFADE_SEC = intPreferencesKey("crossfade_sec")
        val LYRICS_FOLDER = stringPreferencesKey("lyrics_folder")
        val SONG_SORT = stringPreferencesKey("song_sort")
        val SHUFFLE_SEED = longPreferencesKey("shuffle_seed")
        val PLAYER_VIEW = stringPreferencesKey("player_view")
        val VIZ_STYLE = stringPreferencesKey("viz_style")
        val VIZ_PALETTE = stringPreferencesKey("viz_palette")
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
                crossfadeSec = p[CROSSFADE_SEC] ?: d.crossfadeSec,
                lyricsFolder = p[LYRICS_FOLDER]?.takeIf { it.isNotBlank() },
                songSort = p[SONG_SORT] ?: d.songSort,
                shuffleSeed = p[SHUFFLE_SEED] ?: d.shuffleSeed,
                playerView = p[PLAYER_VIEW] ?: d.playerView,
                vizStyle = p[VIZ_STYLE] ?: d.vizStyle,
                vizPalette = p[VIZ_PALETTE] ?: d.vizPalette,
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

    /** Cambia el orden de Canciones. Al elegir "Aleatorio" se hace una mezcla nueva. */
    suspend fun setSongSort(sort: String) {
        context.dataStore.edit {
            it[SONG_SORT] = sort
            if (sort == "SHUFFLE") it[SHUFFLE_SEED] = System.nanoTime()
        }
    }

    suspend fun <T> set(
        key: Preferences.Key<T>,
        value: T,
    ) {
        context.dataStore.edit { it[key] = value }
    }
}
