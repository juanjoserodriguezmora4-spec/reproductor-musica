package com.bismopower.app.audio

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.log10

/**
 * Motor de audio: la cadena de efectos que se mete en el AudioSink de ExoPlayer.
 *
 * El audio decodificado pasa, en este orden, por:
 *  1. [ParametricEq]: lo convierte a float (32 bits) y aplica preamplificador y ecualizador.
 *  2. [PeakLimiter]: evita que los picos se pasen del máximo sin distorsionar.
 *  3. [AnalyzerTap]: saca una copia para el visualizador (solo el reproductor principal).
 *  4. [OutputStage]: con "alta precisión" no hace nada y el audio llega a Android en float;
 *     sin ella lo pasa a 16 bits con dither.
 *
 * Cada reproductor necesita su propia cadena (un AudioProcessor no se puede compartir): el
 * principal y el que suena unos segundos durante el fundido entre canciones. Todas leen los
 * mismos ajustes de [settings], que es inmutable y se cambia entero con [update].
 */
@UnstableApi
class AudioEffects {
    @Volatile var settings = EqSettings()
        private set

    /** Copia del audio para el visualizador (solo la llena el reproductor principal). */
    val analyzer = AudioAnalyzer()

    /** Cuántos dB está bajando el limitador ahora mismo (0 = no hace nada). */
    @Volatile var limiterReductionDb = 0f
        private set

    /** Cuándo se midió [limiterReductionDb] (reloj `elapsedRealtime`): en pausa el dato se queda viejo. */
    @Volatile var limiterAt = 0L
        private set

    /**
     * Cadena nueva para un reproductor.
     * [analyze] = false para el reproductor del fundido, así el visualizador no mezcla dos canciones.
     */
    fun newChain(analyze: Boolean = true): AudioProcessorChain {
        val read = { settings }
        val processors = ArrayList<AudioProcessor>()
        processors += ParametricEq(read)
        processors += PeakLimiter(read, if (analyze) ::reportLimiter else null)
        if (analyze) processors += AnalyzerTap(analyzer)
        processors += OutputStage(read)
        return EffectChain(processors.toTypedArray())
    }

    fun update(newSettings: EqSettings) {
        settings = newSettings
    }

    private fun reportLimiter(gain: Float) {
        limiterReductionDb = if (gain >= 0.999f) 0f else -20f * log10(gain.coerceAtLeast(0.001f))
        limiterAt = SystemClock.elapsedRealtime()
    }
}

/**
 * La lista de procesadores para el AudioSink. No usa los de velocidad ni de saltar silencios de
 * ExoPlayer (la app no cambia la velocidad y esos solo aceptan 16 bits).
 */
@UnstableApi
private class EffectChain(
    private val processors: Array<AudioProcessor>,
) : AudioProcessorChain {
    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

    override fun getSkippedOutputFrameCount(): Long = 0L
}

/** Lee todas las muestras float que quedan en [buffer] hacia [into] (crece si hace falta) y devuelve el arreglo. */
private fun readFloats(
    buffer: ByteBuffer,
    into: FloatArray,
    count: Int,
): FloatArray {
    val target = if (into.size < count) FloatArray(count) else into
    buffer.asFloatBuffer().get(target, 0, count)
    buffer.position(buffer.limit())
    return target
}

/**
 * Preamplificador + ecualizador paramétrico ([EqEngine]).
 *
 * Recibe PCM de 16 bits (o float) y siempre entrega float: así lo que calcula el ecualizador
 * no se vuelve a recortar a 16 bits y queda espacio por encima del máximo para que el
 * limitador trabaje bien.
 */
@UnstableApi
class ParametricEq(
    private val settings: () -> EqSettings,
) : BaseAudioProcessor() {
    private val engine = EqEngine()
    private var floats = FloatArray(0)
    private var shorts = ShortArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT ->
                AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
            // Otros formatos (por ejemplo audio comprimido que va directo al DAC) pasan sin tocar.
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val count = inputBuffer.remaining() / (if (isFloat) 4 else 2)
        if (count == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        if (isFloat) {
            floats = readFloats(inputBuffer, floats, count)
        } else {
            if (floats.size < count) floats = FloatArray(count)
            if (shorts.size < count) shorts = ShortArray(count)
            inputBuffer.asShortBuffer().get(shorts, 0, count)
            inputBuffer.position(inputBuffer.limit())
            for (i in 0 until count) floats[i] = shorts[i] * (1f / 32768f)
        }
        if (format.channelCount > 0) {
            engine.prepare(settings(), format.sampleRate, format.channelCount)
            engine.process(floats, count)
        }
        val out = replaceOutputBuffer(count * 4)
        out.asFloatBuffer().put(floats, 0, count)
        out.limit(count * 4)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        engine.clearMemory()
    }

    override fun onReset() {
        engine.clearMemory()
    }
}

/**
 * Limitador con anticipación ([LookaheadLimiter]). Trabaja en float y retrasa el audio unos
 * 5 ms; lo que queda dentro del retraso sale al terminar la canción (por eso sobrescribe
 * [getOutput] e [isEnded], igual que el procesador de recorte de ExoPlayer).
 *
 * [report] recibe la ganancia más baja de cada bloque para mostrarla en el ecualizador.
 */
@UnstableApi
class PeakLimiter(
    private val settings: () -> EqSettings,
    private val report: ((Float) -> Unit)?,
) : BaseAudioProcessor() {
    private val core = LookaheadLimiter()
    private var floats = FloatArray(0)

    private fun isOn(): Boolean = settings().let { it.enabled && it.limiter }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining() / 4
        if (count == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        floats = readFloats(inputBuffer, floats, count)
        val written = core.process(floats, count, isOn())
        report?.invoke(core.lastLowest)
        val out = replaceOutputBuffer(written * 4)
        out.asFloatBuffer().put(floats, 0, written)
        out.limit(written * 4)
    }

    override fun getOutput(): ByteBuffer {
        if (super.isEnded() && core.pendingFrames > 0) {
            val size = core.latencyFrames * inputAudioFormat.channelCount.coerceAtLeast(1)
            if (floats.size < size) floats = FloatArray(size)
            val written = core.drain(floats, isOn())
            if (written > 0) {
                val out = replaceOutputBuffer(written * 4)
                out.asFloatBuffer().put(floats, 0, written)
                out.limit(written * 4)
            }
        }
        return super.getOutput()
    }

    override fun isEnded(): Boolean = super.isEnded() && core.pendingFrames == 0

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        core.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    }

    override fun onReset() {
        core.reset()
    }
}

/**
 * Último paso. Con "alta precisión" encendida se queda inactivo y el audio llega a Android en
 * float de 32 bits. Apagada, lo convierte a 16 bits con dither ([Dither]).
 * El modo se decide al configurar la cadena (cuando arranca la reproducción).
 */
@UnstableApi
class OutputStage(
    private val settings: () -> EqSettings,
) : BaseAudioProcessor() {
    private val dither = Dither()
    private var floats = FloatArray(0)
    private var shorts = ShortArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT || settings().hiRes) {
            AudioProcessor.AudioFormat.NOT_SET
        } else {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_16BIT)
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining() / 4
        if (count == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }
        floats = readFloats(inputBuffer, floats, count)
        if (shorts.size < count) shorts = ShortArray(count)
        dither.toInt16(floats, shorts, count)
        val out = replaceOutputBuffer(count * 2)
        out.asShortBuffer().put(shorts, 0, count)
        out.limit(count * 2)
    }
}

/** Fábrica de renderizadores que añade [AudioEffects] al sonido de ExoPlayer. */
@UnstableApi
class BismoRenderersFactory(
    context: Context,
    private val effects: AudioEffects,
    private val analyze: Boolean = true,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink
            .Builder(context)
            // La salida float la decide nuestra cadena (OutputStage). La opción de ExoPlayer se deja
            // apagada a propósito: encendida, se salta los procesadores (y el ecualizador) con
            // archivos de alta resolución.
            .setEnableFloatOutput(false)
            .setAudioProcessorChain(effects.newChain(analyze))
            .build()
}
