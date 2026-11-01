package com.bismopower.app.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Cuenta una reproducción cuando la canción lleva sonando 30 segundos (o la mitad, si es más
 * corta). Así no cuentan las canciones que se saltan al empezar. Repetir la misma canción
 * cuenta otra vez.
 */
class PlayCounter(
    private val player: Player,
    private val onCount: (trackId: Long) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var counted = false

    private val tick =
        object : Runnable {
            override fun run() {
                check()
                if (player.isPlaying) handler.postDelayed(this, CHECK_EVERY_MS)
            }
        }

    private val listener =
        object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                counted = false
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(tick)
                if (isPlaying) handler.postDelayed(tick, CHECK_EVERY_MS)
            }
        }

    fun attach() {
        player.addListener(listener)
        if (player.isPlaying) handler.postDelayed(tick, CHECK_EVERY_MS)
    }

    fun release() {
        player.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
    }

    private fun check() {
        if (counted) return
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val duration = player.duration
        val needed = if (duration > 0) minOf(MIN_PLAY_MS, duration / 2) else MIN_PLAY_MS
        if (player.currentPosition >= needed) {
            counted = true
            onCount(id)
        }
    }

    private companion object {
        const val MIN_PLAY_MS = 30_000L
        const val CHECK_EVERY_MS = 5_000L
    }
}
