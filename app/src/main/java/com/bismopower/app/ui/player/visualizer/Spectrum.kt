package com.bismopower.app.ui.player.visualizer

import com.bismopower.app.audio.AudioAnalyzer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Lo que necesita un estilo para dibujarse. Todos los valores van de 0 a 1 (la onda, de −1 a 1). */
interface VizFrame {
    /** Volumen por banda de frecuencia, de graves (0) a agudos. */
    val bands: FloatArray

    /** Marca que se queda un momento arriba cuando una banda baja. */
    val peaks: FloatArray

    /** Forma de la onda (osciloscopio). */
    val wave: FloatArray

    /** Espectros anteriores, el más nuevo en [historyAt] (0). Para la "Cordillera". */
    fun historyAt(age: Int): FloatArray

    val historySize: Int

    /** Fuerza de los graves (para que la carátula "lata"). */
    val energy: Float
}

/**
 * Convierte el audio en lo que dibuja el visualizador, una vez por cuadro (60 veces por segundo):
 * toma 2048 muestras, les aplica la transformada de Fourier (FFT) para separar las frecuencias,
 * las agrupa en [BANDS] bandas repartidas como las oye el oído (más detalle en los graves) y las
 * suaviza para que suban rápido y bajen despacio.
 */
class Spectrum(
    private val analyzer: AudioAnalyzer,
) : VizFrame {
    override val bands = FloatArray(BANDS)
    override val peaks = FloatArray(BANDS)
    override val wave = FloatArray(WAVE)
    private val history = Array(HISTORY) { FloatArray(BANDS) }
    private var historyHead = 0
    private var sinceHistory = 0.0
    override val historySize = HISTORY
    override var energy = 0f
        private set

    /** Qué tanto se mueve todo; cuando llega a casi 0 con la música en pausa, se puede dejar de dibujar. */
    var level = 0f
        private set

    private val samples = FloatArray(N)
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val raw = FloatArray(BANDS)
    private var playhead = -1.0
    private var lastNanos = 0L
    private var waveGain = 4f

    override fun historyAt(age: Int): FloatArray = history[((historyHead - age) % HISTORY + HISTORY) % HISTORY]

    fun update(
        nanos: Long,
        playing: Boolean,
    ) {
        val dt = if (lastNanos == 0L) 0.016 else ((nanos - lastNanos) / 1e9).coerceIn(0.0, 0.1)
        lastNanos = nanos
        val rate = analyzer.sampleRate
        val written = analyzer.written
        val active = playing && written > N

        if (active) {
            // El audio que suena AHORA se escribió hace LATENCY_MS: el cabezal avanza a ritmo real
            // y se corrige poco a poco hacia ese punto, así se mueve suave aunque el audio llegue a saltos.
            val target = written - AudioAnalyzer.LATENCY_MS * rate / 1000.0
            if (playhead < 0 || abs(target - playhead) > rate * 0.4) {
                playhead = target
            } else {
                playhead += dt * rate
                playhead += (target - playhead) * 0.08
            }
            analyzer.copy(playhead.toLong(), samples)
            analyze(rate)
        } else {
            playhead = -1.0
            raw.fill(0f)
        }

        // Suavizado: sube rápido, baja despacio. Las marcas de pico caen solas.
        val up = min(1.0, dt * 28).toFloat()
        val down = min(1.0, dt * 7).toFloat()
        var sum = 0f
        for (i in 0 until BANDS) {
            val v = raw[i]
            bands[i] = if (v > bands[i]) bands[i] + (v - bands[i]) * up else bands[i] + (v - bands[i]) * down
            peaks[i] = if (bands[i] > peaks[i]) bands[i] else max(0f, peaks[i] - (dt * 0.45).toFloat())
            sum += bands[i]
        }
        var bass = 0f
        for (i in 0 until 8) bass += bands[i]
        energy += (bass / 8f - energy) * min(1.0, dt * 12).toFloat()

        // Onda: las últimas 1024 muestras reducidas a WAVE puntos, con volumen automático.
        var peakAbs = 0f
        for (i in 0 until WAVE) {
            val target = if (active) samples[N - 1024 + i * (1024 / WAVE)] else 0f
            peakAbs = max(peakAbs, abs(target))
            wave[i] += (target * waveGain - wave[i]) * 0.5f
        }
        if (active && peakAbs > 0.01f) waveGain += ((0.85f / peakAbs).coerceIn(1f, 12f) - waveGain) * 0.05f
        for (i in 0 until WAVE) wave[i] = wave[i].coerceIn(-1f, 1f)

        // Historial para la "Cordillera": una copia cada 55 ms.
        sinceHistory += dt
        if (sinceHistory >= 0.055) {
            sinceHistory = 0.0
            historyHead = (historyHead + 1) % HISTORY
            bands.copyInto(history[historyHead])
        }

        level = sum / BANDS + peaks.average().toFloat()
    }

    private fun analyze(rate: Int) {
        for (i in 0 until N) {
            re[i] = samples[i] * HANN[i]
            im[i] = 0f
        }
        fft(re, im)
        val nyquist = rate / 2f
        val top = min(MAX_HZ, nyquist * 0.95f)
        val binHz = rate.toFloat() / N
        for (b in 0 until BANDS) {
            val fLo = MIN_HZ * (top / MIN_HZ).pow(b / BANDS.toFloat())
            val fHi = MIN_HZ * (top / MIN_HZ).pow((b + 1) / BANDS.toFloat())
            val lo = (fLo / binHz).toInt().coerceIn(1, N / 2 - 1)
            val hi = (fHi / binHz).toInt().coerceIn(lo, N / 2 - 1)
            var m = 0f
            for (k in lo..hi) m = max(m, sqrt(re[k] * re[k] + im[k] * im[k]))
            // 4/N: una onda a todo volumen da 1 (la ventana Hann reduce a la mitad).
            val amp = m * 4f / N
            val tilt = b / BANDS.toFloat() * 9f // los agudos traen menos energía: se les da un empujón
            val db = 20f * log10(amp + 1e-9f) + tilt
            raw[b] = ((db + 56f) / 50f).coerceIn(0f, 1f).pow(1.25f)
        }
    }

    companion object {
        const val N = 2048
        const val BANDS = 64
        const val WAVE = 256
        const val HISTORY = 28
        const val MIN_HZ = 40f
        const val MAX_HZ = 16_000f

        private val HANN = FloatArray(N) { (0.5 - 0.5 * cos(2 * PI * it / (N - 1))).toFloat() }

        /** FFT de base 2 en el mismo lugar (Cooley–Tukey). */
        fun fft(
            re: FloatArray,
            im: FloatArray,
        ) {
            val n = re.size
            // Reordenar por bits invertidos.
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    var t = re[i]
                    re[i] = re[j]
                    re[j] = t
                    t = im[i]
                    im[i] = im[j]
                    im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat()
                val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr
                        im[b] = im[a] - xi
                        re[a] += xr
                        im[a] += xi
                        val ncr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = ncr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

/** Datos fijos con forma de música, para las miniaturas de la hoja de estilos. */
object PreviewFrame : VizFrame {
    override val bands = FloatArray(Spectrum.BANDS) { i -> (0.85f * (1f - i / 80f) * (0.55f + 0.45f * abs(sin(i * 0.55f + 0.7f)))).coerceIn(0.05f, 1f) }
    override val peaks = FloatArray(Spectrum.BANDS) { i -> (bands[i] + 0.06f).coerceAtMost(1f) }
    override val wave = FloatArray(Spectrum.WAVE) { i -> (sin(i * 0.21f) * 0.55f + sin(i * 0.067f) * 0.3f) }
    private val layers = Array(Spectrum.HISTORY) { a -> FloatArray(Spectrum.BANDS) { i -> (bands[i] * (0.6f + 0.4f * abs(sin(i * 0.9f + a * 0.7f)))) } }

    override fun historyAt(age: Int): FloatArray = layers[age.coerceIn(0, layers.lastIndex)]

    override val historySize = Spectrum.HISTORY
    override val energy = 0.6f
}
