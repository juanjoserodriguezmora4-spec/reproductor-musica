package com.bismopower.app.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    val currentId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    /** Clave del degradado cuando no hay carátula. */
    val artKey: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val count: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** Una canción de la cola, tal como la muestra la pantalla de la cola. */
data class QueueItem(
    val index: Int,
    val id: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
    val artKey: String,
)

private const val EXTRA_ALBUM_ID = "albumId"
private const val EXTRA_ART_KEY = "artKey"

fun TrackEntity.toMediaItem(): MediaItem {
    val mediaUri = Uri.parse(uri)
    val metadata =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri())
            .setTrackNumber(trackNumber)
            .setExtras(
                Bundle().apply {
                    putLong(EXTRA_ALBUM_ID, albumId)
                    putString(EXTRA_ART_KEY, artKey())
                },
            )
            .build()
    return MediaItem
        .Builder()
        .setMediaId(id.toString())
        .setUri(mediaUri)
        .setMediaMetadata(metadata)
        .setRequestMetadata(
            MediaItem.RequestMetadata
                .Builder()
                .setMediaUri(mediaUri)
                .build(),
        ).build()
}

/** Conecta la interfaz con el servicio de reproducción. Todas las llamadas van en el hilo principal. */
class PlayerConnection(
    private val app: Application,
) {
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    private val pending = mutableListOf<(MediaController) -> Unit>()
    private var afterCurrent: (() -> Unit)? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())

    /** La cola completa; solo se reconstruye cuando cambia la lista de canciones. */
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                refresh()
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue()
                // Si era la última canción no hay "siguiente": el temporizador termina igual.
                if (player.playbackState == Player.STATE_ENDED) {
                    afterCurrent?.let {
                        afterCurrent = null
                        it()
                    }
                }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // Temporizador "al terminar la canción": la siguiente empezó sola, así que se pausa.
                val done = afterCurrent ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    afterCurrent = null
                    controller?.pause()
                    controller?.seekTo(0L)
                    done()
                }
            }
        }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh()
            refreshQueue()
            val actions = pending.toList()
            pending.clear()
            actions.forEach { it(c) }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending += block
            connect()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val cur = c.currentMediaItem
        val meta = cur?.mediaMetadata
        _state.update {
            it.copy(
                currentId = cur?.mediaId?.toLongOrNull(),
                title = meta?.title?.toString() ?: "",
                artist = meta?.artist?.toString() ?: "",
                album = meta?.albumTitle?.toString() ?: "",
                artwork = meta?.artworkUri?.toString(),
                artKey = meta?.extras?.getString(EXTRA_ART_KEY) ?: meta?.title?.toString() ?: "",
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0),
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                index = c.currentMediaItemIndex,
                count = c.mediaItemCount,
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
            )
        }
    }

    private fun refreshQueue() {
        val c = controller ?: return
        _queue.value =
            List(c.mediaItemCount) { i ->
                val item = c.getMediaItemAt(i)
                val meta = item.mediaMetadata
                QueueItem(
                    index = i,
                    id = item.mediaId.toLongOrNull(),
                    title = meta.title?.toString() ?: "",
                    artist = meta.artist?.toString() ?: "",
                    album = meta.albumTitle?.toString() ?: "",
                    artwork = meta.artworkUri?.toString(),
                    artKey = meta.extras?.getString(EXTRA_ART_KEY) ?: meta.title?.toString() ?: "",
                )
            }
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    /** Reproduce una lista de canciones empezando en [start]. */
    fun play(
        tracks: List<TrackEntity>,
        start: Int,
        shuffle: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        val index = if (shuffle) tracks.indices.random() else start.coerceIn(0, tracks.size - 1)
        withController { c ->
            c.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
        }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }

    fun pause() = withController { it.pause() }

    /** Volumen propio del reproductor (0 a 1), aparte del volumen del teléfono. Lo usa el temporizador. */
    fun setVolume(volume: Float) = withController { it.volume = volume.coerceIn(0f, 1f) }

    /** Pausa cuando termine la canción actual y luego llama a [onDone]. Con null se cancela. */
    fun pauseAfterCurrent(onDone: (() -> Unit)?) {
        afterCurrent = onDone
    }

    /** Pone estas canciones justo después de la que está sonando. Si no suena nada, las reproduce. */
    fun playNext(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(tracks, 0)
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
            }
        }
    }

    /** Añade canciones al final de la cola. Si no suena nada, las reproduce. */
    fun addToQueue(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                play(tracks, 0)
            } else {
                c.addMediaItems(tracks.map { it.toMediaItem() })
            }
        }
    }

    /** Mueve una canción de la cola de [from] a [to] (los dos son posiciones en la cola). */
    fun moveItem(
        from: Int,
        to: Int,
    ) = withController {
        if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount && from != to) it.moveMediaItem(from, to)
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seek(ms: Long) = withController { it.seekTo(ms) }

    /** Siguiente canción sin reiniciar la actual (lo usan los gestos sobre la carátula). */
    fun nextTrack() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    /** Canción anterior siempre, aunque la actual ya lleve varios segundos sonando. */
    fun previousTrack() = withController { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }

    /** Adelanta (positivo) o retrocede (negativo) [deltaMs] dentro de la canción. */
    fun seekBy(deltaMs: Long) =
        withController {
            val dur = it.duration
            val target = (it.currentPosition + deltaMs).coerceAtLeast(0L)
            it.seekTo(if (dur > 0) target.coerceAtMost(dur - 500).coerceAtLeast(0L) else target)
        }

    /** Salta a la canción [index] de la cola. */
    fun playAt(index: Int) =
        withController {
            if (index in 0 until it.mediaItemCount) {
                it.seekTo(index, 0L)
                it.play()
            }
        }

    /** Quita de la cola la canción [index]. */
    fun removeAt(index: Int) = withController { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }

    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    fun cycleRepeat() =
        withController {
            it.repeatMode =
                when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }
}
