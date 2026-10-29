package com.bismopower.app.audio

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer

/**
 * Cadena de efectos que se mete en el AudioSink de ExoPlayer.
 *
 * Fase 1: el ecualizador existe pero está inactivo (deja pasar el audio sin tocarlo).
 * Fase 2: [ParametricEq] aplicará filtros biquad; la interfaz cambia sus bandas desde aquí.
 */
@UnstableApi
class AudioEffects {
    val equalizer = ParametricEq()

    /** Orden en que se procesa el audio, de primero a último. */
    val processors: List<AudioProcessor> = listOf(equalizer)
}

/**
 * Ecualizador paramétrico (marcador de la Fase 2).
 *
 * Para implementarlo: aceptar PCM 16 bits y float en [onConfigure], crear un biquad por banda
 * y canal con la frecuencia de muestreo recibida, y filtrar las muestras en [queueInput].
 * Los coeficientes se recalculan en [onFlush] cuando cambian las bandas.
 */
@UnstableApi
class ParametricEq : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // NOT_SET deja el procesador inactivo: ExoPlayer se lo salta y no gasta batería.
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }
}

/** Fábrica de renderizadores que añade [AudioEffects] al sonido de ExoPlayer. */
@UnstableApi
class BismoRenderersFactory(
    context: Context,
    private val effects: AudioEffects,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink
            .Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setAudioProcessors(effects.processors.toTypedArray())
            .build()
}
