package com.bismopower.app.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Cadena de efectos que se mete en el AudioSink de ExoPlayer.
 *
 * Cada reproductor necesita su propia copia del ecualizador (un AudioProcessor no se puede
 * compartir): el principal y el que suena unos segundos durante el fundido entre canciones.
 * [update] le pasa los mismos ajustes a todas las copias.
 */
@UnstableApi
class AudioEffects {
    @Volatile private var settings = EqSettings()
    private val equalizers = CopyOnWriteArrayList<WeakReference<ParametricEq>>()

    /** Efectos nuevos para un reproductor, en el orden en que se procesa el audio. */
    fun newProcessors(): Array<AudioProcessor> {
        val eq = ParametricEq()
        eq.update(settings)
        equalizers.removeIf { it.get() == null }
        equalizers += WeakReference(eq)
        return arrayOf(eq)
    }

    fun update(newSettings: EqSettings) {
        settings = newSettings
        equalizers.forEach { it.get()?.update(newSettings) }
    }
}

/**
 * Ecualizador paramétrico propio: un filtro biquad por banda y por canal.
 *
 * Recibe el audio en PCM de 16 bits o en float y lo devuelve en el mismo formato.
 * Por dentro todo se calcula en `Double`, así subir y bajar bandas no pierde calidad;
 * solo al final se vuelve a 16 bits.
 *
 * La interfaz cambia los ajustes con [update] desde el hilo principal; el hilo de audio
 * los lee en [queueInput]. Por eso los ajustes son inmutables y se pasan con @Volatile.
 */
@UnstableApi
class ParametricEq : BaseAudioProcessor() {
    @Volatile private var settings = EqSettings()

    @Volatile private var version = 0

    // Lo que sigue solo lo toca el hilo de audio.
    private var appliedVersion = -1
    private var appliedRate = 0
    private var appliedChannels = 0
    private var active = false
    private var limit = true
    private var preamp = 1.0
    private var coefs: Array<BiquadCoefs> = emptyArray()
    private var skip = BooleanArray(0)

    // Memoria de cada filtro (las dos últimas muestras), una por filtro y canal: [filtro * canales + canal].
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)

    /** Cambia los ajustes. Se aplican en el siguiente bloque de audio (unos milisegundos). */
    fun update(newSettings: EqSettings) {
        settings = newSettings
        version++
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            // Mismo formato de entrada y de salida: solo cambian los valores de las muestras.
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            // Otros formatos (por ejemplo audio comprimido que va directo al DAC) pasan sin tocar.
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val format = inputAudioFormat
        val channels = format.channelCount
        if (channels > 0) prepare(format.sampleRate, channels)

        if (!active || channels <= 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        var ch = 0
        if (format.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) {
                val y = processSample(inputBuffer.getFloat().toDouble(), ch)
                out.putFloat(y.toFloat())
                ch++
                if (ch == channels) ch = 0
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                val y = processSample(inputBuffer.getShort() / 32768.0, ch)
                out.putShort((y * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort())
                ch++
                if (ch == channels) ch = 0
            }
        }
        // Si quedara algún byte suelto (no debería), sale tal cual para no descuadrar el audio.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    /** Pasa una muestra de un canal por el preamplificador, todos los filtros y el limitador. */
    private fun processSample(
        x: Double,
        ch: Int,
    ): Double {
        var v = x * preamp
        val channels = appliedChannels
        for (f in coefs.indices) {
            if (skip[f]) continue
            val k = coefs[f]
            val i = f * channels + ch
            // Forma directa II transpuesta: la más estable con números en Double.
            val y = k.b0 * v + z1[i]
            z1[i] = k.b1 * v - k.a1 * y + z2[i]
            z2[i] = k.b2 * v - k.a2 * y
            v = y
        }
        return if (limit) softClip(v) else v
    }

    /** Recalcula los filtros si cambiaron los ajustes, la frecuencia de muestreo o los canales. */
    private fun prepare(
        sampleRate: Int,
        channels: Int,
    ) {
        val v = version
        val formatChanged = sampleRate != appliedRate || channels != appliedChannels
        if (v == appliedVersion && !formatChanged) return
        val s = settings
        val filters = s.filters()

        val wasActive = active
        active = s.enabled
        limit = s.limiter
        preamp = 10.0.pow(s.preampDb / 20.0)
        coefs =
            Array(filters.size) { i ->
                val b = filters[i]
                if (abs(b.gainDb) < 0.05f) {
                    BiquadCoefs.IDENTITY
                } else {
                    BiquadCoefs.design(b.type, b.freq.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), sampleRate.toDouble())
                }
            }
        val newSkip = BooleanArray(filters.size) { abs(filters[it].gainDb) < 0.05f }

        // La memoria de los filtros se borra si cambia el formato, si el ecualizador se
        // acaba de encender o si una banda se apaga (para que no quede un resto de sonido viejo).
        if (formatChanged || z1.size != filters.size * channels || (active && !wasActive)) {
            z1 = DoubleArray(filters.size * channels)
            z2 = DoubleArray(filters.size * channels)
        } else {
            for (f in newSkip.indices) {
                if (newSkip[f]) {
                    for (c in 0 until channels) {
                        z1[f * channels + c] = 0.0
                        z2[f * channels + c] = 0.0
                    }
                }
            }
        }
        skip = newSkip
        appliedVersion = v
        appliedRate = sampleRate
        appliedChannels = channels
    }

    /** Al reiniciar el reproductor se fuerza a recalcular todo con el siguiente bloque de audio. */
    override fun onReset() {
        appliedVersion = -1
        appliedRate = 0
        appliedChannels = 0
    }

    private companion object {
        /** A partir de este nivel el limitador empieza a redondear los picos. */
        const val KNEE = 0.9

        /**
         * Limitador suave: por debajo del 90 % no toca nada; por encima redondea el pico
         * en lugar de cortarlo de golpe, que es lo que suena a distorsión.
         */
        fun softClip(x: Double): Double {
            val a = abs(x)
            if (a <= KNEE) return x
            val y = KNEE + (1.0 - KNEE) * tanh((a - KNEE) / (1.0 - KNEE))
            return if (x < 0) -y else y
        }
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
            .setAudioProcessors(effects.newProcessors())
            .build()
}
