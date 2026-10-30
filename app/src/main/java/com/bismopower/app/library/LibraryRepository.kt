package com.bismopower.app.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.bismopower.app.data.SettingsRepository
import com.bismopower.app.data.db.BismoDatabase
import com.bismopower.app.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Permiso necesario para leer la música según la versión de Android. */
val AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Carátula del álbum que guarda MediaStore. Coil y Media3 la leen directamente. */
fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

/** Una carpeta del teléfono que tiene música, para la pantalla de elegir carpetas. */
data class DeviceFolder(
    val path: String,
    val count: Int,
)

/** true si [folder] es [chosen] o está dentro de ella. */
fun isInside(
    folder: String,
    chosen: String,
): Boolean = folder == chosen || folder.startsWith(chosen.trimEnd('/') + "/")

/**
 * Lee la música del teléfono con MediaStore y la guarda en Room para que la biblioteca abra al instante.
 * Si en Ajustes se eligieron carpetas, solo se guardan las canciones de esas carpetas y sus subcarpetas.
 */
class LibraryRepository(
    private val context: Context,
    private val db: BismoDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.tracks()
    private val mutex = Mutex()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val tracks = dao.all()
    val albums = dao.albums()
    val artists = dao.artists()
    val folders = dao.folders()

    fun album(albumId: Long) = dao.byAlbum(albumId)

    fun artist(name: String) = dao.byArtist(name)

    fun folder(path: String) = dao.byFolder(path)

    private var scannedOnce = false

    /** Escanea una sola vez por sesión de la app (al abrirla); el resto lo pide el usuario en Ajustes. */
    suspend fun scanOnce() {
        if (scannedOnce) return
        scannedOnce = true
        scan()
    }

    /** Escanea MediaStore y devuelve cuántas canciones encontró. */
    suspend fun scan(): Int =
        mutex.withLock {
            if (!hasAudioPermission(context)) return 0
            _scanning.value = true
            try {
                val s = settings.current()
                val all = withContext(Dispatchers.IO) { query() }
                val found =
                    if (s.onlyChosenFolders) {
                        all.filter { t -> s.musicFolders.any { isInside(t.folder, it) } }
                    } else {
                        all
                    }
                dao.replaceAll(found)
                found.size
            } finally {
                _scanning.value = false
            }
        }

    /** Todas las carpetas del teléfono que tienen música (sin aplicar el filtro), ordenadas por ruta. */
    suspend fun deviceFolders(): List<DeviceFolder> {
        if (!hasAudioPermission(context)) return emptyList()
        return withContext(Dispatchers.IO) {
            query()
                .groupingBy { it.folder }
                .eachCount()
                .map { (path, count) -> DeviceFolder(path, count) }
                .sortedBy { it.path.lowercase() }
        }
    }

    @Suppress("DEPRECATION") // DATA sigue siendo la forma más fiable de saber la carpeta.
    private fun query(): List<TrackEntity> {
        val collection =
            if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        val projection =
            buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.TRACK)
                add(MediaStore.Audio.Media.YEAR)
                add(MediaStore.Audio.Media.DATA)
                add(MediaStore.Audio.Media.DATE_ADDED)
                if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH)
            }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("15000") // Ignora tonos y notas de voz muy cortas.

        val out = ArrayList<TrackEntity>()
        context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val data = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val relative = c.getColumnIndex("relative_path")

            while (c.moveToNext()) {
                val trackId = c.getLong(id)
                val path = c.getString(data)
                val folder =
                    path?.let { File(it).parent }
                        ?: (if (relative >= 0) c.getString(relative)?.trimEnd('/') else null)
                        ?: "Desconocida"
                val rawArtist = c.getString(artist)
                out +=
                    TrackEntity(
                        id = trackId,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId).toString(),
                        title = c.getString(title)?.takeIf { it.isNotBlank() } ?: path?.let { File(it).nameWithoutExtension } ?: "Sin título",
                        artist = rawArtist?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: "Artista desconocido",
                        album = c.getString(album)?.takeIf { it.isNotBlank() } ?: "Álbum desconocido",
                        albumId = c.getLong(albumId),
                        durationMs = c.getLong(duration),
                        trackNumber = c.getInt(track) % 1000, // Algunos formatos guardan disco × 1000 + pista.
                        year = c.getInt(year),
                        folder = folder,
                        dateAdded = c.getLong(added),
                    )
            }
        }
        return out
    }
}
