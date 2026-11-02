package com.bismopower.app.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import com.bismopower.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Busca la letra de una canción, en este orden:
 *
 * 1. Un archivo .lrc con el mismo nombre al lado de la canción (cancion.mp3 → cancion.lrc).
 *    Desde Android 11 las apps no pueden leer archivos que no son música, así que esto solo
 *    funciona en teléfonos más viejos; para los nuevos está el punto 2.
 * 2. Un .lrc con ese nombre dentro de la "carpeta de letras" elegida en Ajustes (o sus subcarpetas).
 * 3. La letra guardada dentro del propio archivo de audio (ver [TagLyrics]).
 */
class LyricsRepository(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val cache = LruCache<Long, Lyrics>(40)
    private val missing = HashSet<Long>()
    private val mutex = Mutex()

    /** Archivos .lrc de la carpeta de letras: nombre sin extensión (en minúsculas) → dirección. */
    private var index: Map<String, Uri>? = null
    private var indexFor: String? = null
    private var indexBuiltAt = 0L

    /** Olvida lo que ya buscó (por ejemplo, al cambiar la carpeta de letras). */
    fun invalidate() {
        synchronized(missing) { missing.clear() }
        cache.evictAll()
        index = null
        indexFor = null
    }

    suspend fun forTrack(trackId: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            cache.get(trackId)?.let { return@withContext it }
            if (synchronized(missing) { trackId in missing }) return@withContext null
            val found = runCatching { search(trackId) }.getOrNull()
            if (found != null) cache.put(trackId, found) else synchronized(missing) { missing += trackId }
            found
        }

    @Suppress("DEPRECATION") // DATA sigue siendo la forma de saber la ruta del archivo.
    private suspend fun search(trackId: Long): Lyrics? {
        val audio = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
        var path: String? = null
        var displayName: String? = null
        context.contentResolver
            .query(audio, arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    path = c.getString(0)
                    displayName = c.getString(1)
                }
            }
        val base = (displayName ?: path?.let { File(it).name })?.substringBeforeLast('.')

        // 1. Al lado de la canción.
        path?.let { p ->
            val lrc = File(p.substringBeforeLast('.') + ".lrc")
            val text = runCatching { if (lrc.canRead()) lrc.readText() else null }.getOrNull()
            if (text != null) LrcParser.parse(text, "archivo .lrc")?.let { return it }
        }

        // 2. En la carpeta de letras.
        if (base != null) {
            val uri = lyricsIndex()[base.lowercase()]
            if (uri != null) {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (text != null) LrcParser.parse(text, "archivo .lrc")?.let { return it }
            }
        }

        // 3. Dentro del archivo de audio.
        val (head, tail) = readEnds(audio) ?: return null
        val embedded = TagLyrics.find(head, tail) ?: return null
        return LrcParser.parse(embedded, "incluida en la canción")
    }

    /** El principio (512 KB) y el final (2 MB) del archivo: ahí están las etiquetas. */
    private fun readEnds(audio: Uri): Pair<ByteArray, ByteArray>? =
        context.contentResolver.openFileDescriptor(audio, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val size = ch.size()
                val headLen = minOf(size, HEAD_BYTES.toLong()).toInt()
                val head = ByteBuffer.allocate(headLen)
                ch.position(0)
                while (head.hasRemaining() && ch.read(head) > 0) Unit
                val tailLen = minOf((size - headLen).coerceAtLeast(0L), TAIL_BYTES.toLong()).toInt()
                val tail = ByteBuffer.allocate(tailLen)
                if (tailLen > 0) {
                    ch.position(size - tailLen)
                    while (tail.hasRemaining() && ch.read(tail) > 0) Unit
                }
                head.array() to tail.array()
            }
        }

    /** Arma (o reutiliza) la lista de .lrc de la carpeta elegida. Se rehace como mucho cada minuto. */
    private suspend fun lyricsIndex(): Map<String, Uri> =
        mutex.withLock {
            val folder = settings.current().lyricsFolder ?: return@withLock emptyMap()
            val cached = index
            val fresh = SystemClock.elapsedRealtime() - indexBuiltAt < 60_000
            if (cached != null && indexFor == folder && fresh) return@withLock cached
            val tree = Uri.parse(folder)
            val map = HashMap<String, Uri>()
            runCatching { collect(tree, DocumentsContract.getTreeDocumentId(tree), map, depth = 0) }
            index = map
            indexFor = folder
            indexBuiltAt = SystemClock.elapsedRealtime()
            map
        }

    private fun collect(
        tree: Uri,
        parentId: String,
        out: MutableMap<String, Uri>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val dirs = ArrayList<String>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    dirs += id
                } else if (name.endsWith(".lrc", ignoreCase = true)) {
                    out[name.substringBeforeLast('.').lowercase()] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                }
            }
        }
        dirs.forEach { collect(tree, it, out, depth + 1) }
    }

    private companion object {
        const val HEAD_BYTES = 512 * 1024
        const val TAIL_BYTES = 2 * 1024 * 1024
        const val MAX_DEPTH = 4
    }
}
