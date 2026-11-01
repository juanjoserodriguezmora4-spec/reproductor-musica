package com.bismopower.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado del temporizador: a qué hora se apaga, o si se apaga al terminar la canción. */
data class SleepState(
    val endsAt: Long? = null,
    val endOfTrack: Boolean = false,
) {
    val active: Boolean get() = endsAt != null || endOfTrack
}

/**
 * Temporizador para dormir. En los últimos 20 segundos baja el volumen poco a poco y luego
 * pausa, así la música no se corta de golpe. Vive en el contenedor de la app, así que sigue
 * contando aunque cierres la pantalla del reproductor.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val player: PlayerConnection,
) {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) {
        cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = SleepState(endsAt = endsAt)
        // En el hilo principal: el reproductor solo se puede tocar desde ahí.
        job =
            scope.launch(Dispatchers.Main) {
                delay((endsAt - System.currentTimeMillis() - FADE_MS).coerceAtLeast(0L))
                for (i in 1..FADE_STEPS) {
                    player.setVolume(1f - i / FADE_STEPS.toFloat())
                    delay(FADE_MS / FADE_STEPS)
                }
                player.pause()
                player.setVolume(1f)
                _state.value = SleepState()
                job = null
            }
    }

    /** Pausa cuando termine la canción que está sonando. */
    fun stopAtEndOfTrack() {
        cancel()
        _state.value = SleepState(endOfTrack = true)
        player.pauseAfterCurrent { _state.value = SleepState() }
    }

    fun cancel() {
        val wasFading = job != null
        job?.cancel()
        job = null
        if (wasFading) player.setVolume(1f)
        player.pauseAfterCurrent(null)
        _state.value = SleepState()
    }

    private companion object {
        const val FADE_MS = 20_000L
        const val FADE_STEPS = 40
    }
}
