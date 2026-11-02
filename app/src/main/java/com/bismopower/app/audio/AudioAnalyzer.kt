package com.bismopower.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Guarda los últimos segundos del audio que va a sonar (en mono) para el visualizador.
 *
 * No usa el micrófono ni pide permisos: el audio se copia dentro de la cadena de ExoPlayer,
 * justo después del ecualizador (así el visualizador también muestra lo que cambiaste).
 * El audio sale por el parlante un poco después de pasar por aquí (lo que tarda el búfer de
 * Android); quien lee compensa esa espera con [LATENCY_MS].
 */
class AudioAnalyzer {
    private val ring = FloatArray(RING_SIZE)

    /** Cuántas muestras se han escrito en total (no se reinicia; la posición en el anillo es esto % tamaño). */
    @Volatile var written = 0L
        private set

    @Volatile var sampleRate = 44_100
        private set

    @Synchronized
    fun push(
        mono: FloatArray,
        count: Int,
        rate: Int,
    ) {
        sampleRate = rate
        var w = written
        for (i in 0 until count) {
            ring[(w % RING_SIZE).toInt()] = mono[i]
            w++
        }
        written = w
    }

    /** Copia en [out] las muestras que terminan en la posición [end] (las más viejas primero). */
    @Synchronized
    fun copy(
        end: Long,
        out: FloatArray,
    ) {
        val oldest = (written - RING_SIZE + 1).coerceAtLeast(0L)
        for (i in out.indices) {
            val pos = end - out.size + i
            out[i] = if (pos < oldest || pos >= written || pos < 0) 0f else ring[(pos % RING_SIZE).toInt()]
        }
    }

    companion object {
        const val RING_SIZE = 1 shl 16

        /** Lo que tarda el audio en salir por el parlante después de procesarse (búfer de ~250 ms + equipo). */
        const val LATENCY_MS = 280L
    }
}

/**
 * Procesador que deja pasar el audio sin cambiarlo y de paso le entrega una copia en mono
 * a [AudioAnalyzer]. Acepta PCM de 16 bits y float, igual que el ecualizador.
 */
@UnstableApi
class AnalyzerTap(
    private val analyzer: AudioAnalyzer,
) : BaseAudioProcessor() {
    private var mono = FloatArray(4096)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val format = inputAudioFormat
        val channels = format.channelCount.coerceAtLeast(1)
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerFrame = channels * (if (isFloat) 4 else 2)
        val frames = size / bytesPerFrame
        if (mono.size < frames) mono = FloatArray(frames)

        // Mezcla los canales en uno leyendo sin mover el búfer de entrada.
        val start = inputBuffer.position()
        for (f in 0 until frames) {
            var sum = 0f
            val base = start + f * bytesPerFrame
            for (ch in 0 until channels) {
                sum +=
                    if (isFloat) {
                        inputBuffer.getFloat(base + ch * 4)
                    } else {
                        inputBuffer.getShort(base + ch * 2) / 32768f
                    }
            }
            mono[f] = sum / channels
        }
        analyzer.push(mono, frames, format.sampleRate)

        val out = replaceOutputBuffer(size)
        out.put(inputBuffer)
        out.flip()
    }
}
