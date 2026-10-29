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
import com.bismopower.app.library.albumArtUri
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
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val count: Int = 0,
)

private const val EXTRA_ALBUM_ID = "albumId"

fun TrackEntity.toMediaItem(): MediaItem {
    val mediaUri = Uri.parse(uri)
    val metadata =
        MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(albumArtUri(albumId))
            .setTrackNumber(trackNumber)
            .setExtras(Bundle().apply { putLong(EXTRA_ALBUM_ID, albumId) })
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
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                refresh()
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
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0),
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                index = c.currentMediaItemIndex,
                count = c.mediaItemCount,
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

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seek(ms: Long) = withController { it.seekTo(ms) }

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
