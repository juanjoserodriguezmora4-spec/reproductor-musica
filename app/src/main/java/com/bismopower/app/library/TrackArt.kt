package com.bismopower.app.library

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.bismopower.app.data.db.TrackEntity
import java.io.File

/** Esquema propio para pedir la carátula de una canción: bismoart://track/<id>?album=<albumId> */
const val ART_SCHEME = "bismoart"

/**
 * Carátula de UNA canción (no del álbum). Las descargas de Bruma, por ejemplo, no traen
 * nombre de álbum: Android las junta todas en un "álbum" con el nombre de la carpeta y la
 * carátula del álbum sale vacía o repetida. Por eso se lee la imagen de cada archivo.
 */
fun trackArtUri(
    trackId: Long,
    albumId: Long,
    useAlbumArt: Boolean = true,
): Uri =
    Uri
        .Builder()
        .scheme(ART_SCHEME)
        .authority("track")
        .appendPath(trackId.toString())
        .apply { if (useAlbumArt) appendQueryParameter("album", albumId.toString()) }
        .build()

/** Nombre que pone BismoPower cuando la canción no trae álbum. */
const val UNKNOWN_ALBUM = "Álbum desconocido"

/**
 * true si la canción trae un álbum de verdad. Cuando un archivo no tiene álbum, Android le
 * pone el nombre de su carpeta (por ejemplo "Bruma" o "Download") y junta ahí canciones que
 * no tienen nada que ver; esa carátula de "álbum" sería la de una sola de ellas.
 */
fun TrackEntity.hasRealAlbum(): Boolean {
    val a = album.trim()
    if (a.isEmpty() || a == UNKNOWN_ALBUM || a.equals("<unknown>", ignoreCase = true)) return false
    return !a.equals(File(folder).name, ignoreCase = true)
}

/** Carátula de la canción. Solo usa la del álbum como respaldo si el álbum es de verdad. */
fun TrackEntity.artUri(): Uri = trackArtUri(id, albumId, useAlbumArt = hasRealAlbum())

/**
 * Clave del degradado que se ve cuando no hay carátula: las canciones de un mismo álbum
 * comparten color; las sueltas (como las de Bruma) tienen cada una el suyo.
 */
fun TrackEntity.artKey(): String = if (hasRealAlbum()) album else "$title·$artist"

/** Lee carátulas. Hace trabajo de disco: llamar siempre fuera del hilo principal. */
object ArtLoader {
    /**
     * Busca la carátula en este orden y devuelve la primera que encuentre:
     * 1. Miniatura de Android (Android 10+): sale de la imagen que trae el archivo o de un
     *    cover.jpg / folder.jpg en la misma carpeta. Android la guarda en caché, así que es rápida.
     * 2. La imagen incrustada en el archivo, leída directamente (MP3, M4A, FLAC, OGG/Opus).
     * 3. La carátula del álbum que guarda MediaStore.
     */
    fun load(
        context: Context,
        artUri: Uri,
        maxPx: Int,
    ): Bitmap? {
        val trackId = artUri.lastPathSegment?.toLongOrNull() ?: return null
        val albumId = artUri.getQueryParameter("album")?.toLongOrNull()
        val audio = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
        val bitmap =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) systemThumbnail(context, audio, maxPx) else null)
                ?: embedded(context, audio, maxPx)
                ?: albumId?.let { album(context, it, maxPx) }
        // Palette (colores del reproductor) no puede leer bitmaps que viven en la GPU.
        return if (bitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }

    private fun systemThumbnail(
        context: Context,
        audio: Uri,
        maxPx: Int,
    ): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(audio, Size(maxPx, maxPx), null) }.getOrNull()
        } else {
            null
        }

    private fun embedded(
        context: Context,
        audio: Uri,
        maxPx: Int,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audio)
            retriever.embeddedPicture?.let { decode(it, maxPx) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun album(
        context: Context,
        albumId: Long,
        maxPx: Int,
    ): Bitmap? =
        runCatching {
            context.contentResolver.openInputStream(albumArtUri(albumId))?.use { it.readBytes() }?.let { decode(it, maxPx) }
        }.getOrNull()

    /** Decodifica reduciendo la imagen: una carátula de 3000 px no hace falta para una miniatura. */
    private fun decode(
        bytes: ByteArray,
        maxPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
