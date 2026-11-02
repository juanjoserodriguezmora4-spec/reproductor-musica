package com.bismopower.app.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.bismopower.app.audio.AudioEffects
import com.bismopower.app.audio.BismoRenderersFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Fundido entre canciones (crossfade).
 *
 * Un solo ExoPlayer no puede sonar dos canciones a la vez, así que unos segundos antes de que
 * termine la canción se crea un segundo reproductor con la siguiente. Mientras uno baja el
 * volumen, el otro lo sube. Al final, el reproductor principal salta a la canción nueva en el
 * mismo punto donde va el segundo, y el segundo se apaga. La notificación, la cola y todo lo
 * demás siguen usando solo el principal.
 *
 * Se cancela si pausas, saltas, mueves la barra o cambias la cola durante el fundido.
 * Con el fundido apagado no se hace nada: ExoPlayer ya une las canciones sin pausas (gapless).
 */
@UnstableApi
class Crossfader(
    private val context: Context,
    private val main: ExoPlayer,
    private val effects: AudioEffects,
    /** Duración del fundido en milisegundos; 0 = apagado. */
    private val fadeMs: () -> Long,
    /** false cuando no debe haber fundido (por ejemplo, el temporizador "al terminar la canción"). */
    private val allowed: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var next: ExoPlayer? = null
    private var nextIndex = C.INDEX_UNSET
    private var startedAt = 0L
    private var length = 0L
    private var handingOff = false

    private val tick =
        object : Runnable {
            override fun run() {
                step()
                if (main.isPlaying || next != null) handler.postDelayed(this, if (next != null) FADE_TICK_MS else WATCH_TICK_MS)
            }
        }

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (handingOff) return
                handler.removeCallbacks(tick)
                if (isPlaying) handler.post(tick) else cancel()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (!handingOff && reason == Player.DISCONTINUITY_REASON_SEEK) cancel()
            }

            override fun onTimelineChanged(
                timeline: Timeline,
                reason: Int,
            ) {
                if (!handingOff && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) cancel()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = cancel()

            override fun onRepeatModeChanged(repeatMode: Int) = cancel()
        }

    fun attach() {
        main.addListener(listener)
        if (main.isPlaying) handler.post(tick)
    }

    fun release() {
        main.removeListener(listener)
        handler.removeCallbacksAndMessages(null)
        releaseNext()
        handingOff = false
    }

    private fun step() {
        val n = next
        if (n != null) {
            if (handingOff) return
            val p = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / length).coerceIn(0f, 1f)
            // Curva de igual potencia: a mitad del fundido las dos suenan al mismo volumen percibido.
            main.volume = cos(p * PI / 2).toFloat()
            n.volume = sin(p * PI / 2).toFloat()
            if (p >= 1f) handOff()
            return
        }
        if (!main.isPlaying || !allowed()) return
        val fade = fadeMs()
        if (fade <= 0) return
        if (main.repeatMode == Player.REPEAT_MODE_ONE || !main.hasNextMediaItem()) return
        val duration = main.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        val remaining = duration - main.currentPosition
        if (remaining > fade || remaining < MIN_REMAINING_MS) return
        start(min(fade, remaining - 300))
    }

    private fun start(fade: Long) {
        val index = main.nextMediaItemIndex
        if (index == C.INDEX_UNSET) return
        val item = main.getMediaItemAt(index)
        val p =
            ExoPlayer
                .Builder(context, BismoRenderersFactory(context, effects, analyze = false))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // El principal ya tiene el "foco de audio"; este solo acompaña unos segundos.
                    false,
                ).build()
        p.volume = 0f
        p.setMediaItem(item)
        p.prepare()
        p.play()
        next = p
        nextIndex = index
        startedAt = SystemClock.elapsedRealtime()
        length = fade.coerceAtLeast(500L)
    }

    /** El principal salta a la canción nueva donde va el segundo; cuando ya suena, el segundo se apaga. */
    private fun handOff() {
        val n = next ?: return
        handingOff = true
        main.volume = 0f
        main.seekTo(nextIndex, n.currentPosition + SEEK_LEAD_MS)
        val waitFrom = SystemClock.elapsedRealtime()
        handler.post(
            object : Runnable {
                override fun run() {
                    val ready = main.playbackState == Player.STATE_READY && main.isPlaying
                    if (ready || SystemClock.elapsedRealtime() - waitFrom > MAX_WAIT_MS) {
                        main.volume = 1f
                        releaseNext()
                        handingOff = false
                        handler.removeCallbacks(tick)
                        handler.post(tick)
                    } else {
                        handler.postDelayed(this, 20)
                    }
                }
            },
        )
    }

    private fun cancel() {
        if (next == null) return
        releaseNext()
        main.volume = 1f
    }

    private fun releaseNext() {
        next?.release()
        next = null
        nextIndex = C.INDEX_UNSET
    }

    private companion object {
        const val FADE_TICK_MS = 40L
        const val WATCH_TICK_MS = 250L
        const val MIN_REMAINING_MS = 1_500L

        /** Lo que suele tardar el principal en volver a sonar después de saltar (archivo local). */
        const val SEEK_LEAD_MS = 120L
        const val MAX_WAIT_MS = 1_500L
    }
}
