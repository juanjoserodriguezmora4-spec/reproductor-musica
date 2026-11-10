package com.bismopower.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Qué tan fuerte suena una canción.
 * [lufs] es la sonoridad (norma ITU-R BS.1770, la misma que usan las plataformas de música):
 * números más cercanos a 0 = más fuerte. [peak] es la muestra más alta medida (1 = tope digital).
 * Una canción que no se pudo leer se guarda con [lufs] = NaN para no intentarlo cada vez.
 */
data class Loudness(
    val lufs: Float,
    val peak: Float,
) {
    val valid: Boolean
        get() = !lufs.isNaN()

    companion object {
        val UNREADABLE = Loudness(Float.NaN, 0f)
    }
}

/** A qué sonoridad se llevan todas las canciones. */
const val TARGET_LUFS = -10f

/**
 * Cuántos dB hay que subir o bajar una canción para que suene igual de fuerte que las demás.
 * Bajar siempre se puede. Subir solo hasta donde lo permiten sus picos: como mucho 3 dB por
 * encima del tope si el limitador está encendido (él se encarga de esos picos), y nada por
 * encima si está apagado.
 */
fun normalizationGainDb(
    loudness: Loudness?,
    limiter: Boolean,
): Float {
    if (loudness == null || !loudness.valid) return 0f
    val wanted = (TARGET_LUFS - loudness.lufs).coerceIn(-14f, 9f)
    if (wanted <= 0f) return wanted
    val room = -20f * log10(loudness.peak.coerceIn(0.05f, 1f)) + (if (limiter) 3f else 0f)
    return min(wanted, max(0f, room))
}

/**
 * Mide la sonoridad de un audio según ITU-R BS.1770: filtra como oye el oído (resta graves
 * profundos, realza agudos), mide la energía en bloques de 400 ms que se pisan cada 100 ms y
 * promedia solo los bloques que de verdad suenan (descarta silencios y partes muy bajitas).
 *
 * Recibe muestras float con los canales intercalados. Sin dependencias de Android.
 */
class LoudnessMeter(
    sampleRate: Int,
    private val channels: Int,
) {
    // Filtro 1: estante de agudos (+4 dB desde ~1,7 kHz). Filtro 2: pasa altos (~38 Hz).
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private val c1: Double
    private val c2: Double

    // Memoria de los dos filtros por canal.
    private val s1 = DoubleArray(channels * 2)
    private val s2 = DoubleArray(channels * 2)

    private val hop = max(1, sampleRate / 10)
    private var inHop = 0
    private var hopEnergy = 0.0

    // Energía de los últimos 4 tramos de 100 ms (= un bloque de 400 ms).
    private val last = DoubleArray(4)
    private var hops = 0
    private val blocks = ArrayList<Double>()

    var peak = 0f
        private set

    init {
        var k = tan(PI * 1681.974450955533 / sampleRate)
        val vh = Math.pow(10.0, 3.999843853973347 / 20.0)
        val vb = Math.pow(vh, 0.4996667741545416)
        val q = 0.7071752369554196
        val a0 = 1.0 + k / q + k * k
        b0 = (vh + vb * k / q + k * k) / a0
        b1 = 2.0 * (k * k - vh) / a0
        b2 = (vh - vb * k / q + k * k) / a0
        a1 = 2.0 * (k * k - 1.0) / a0
        a2 = (1.0 - k / q + k * k) / a0
        k = tan(PI * 38.13547087602444 / sampleRate)
        val q2 = 0.5003270373238773
        val d = 1.0 + k / q2 + k * k
        c1 = 2.0 * (k * k - 1.0) / d
        c2 = (1.0 - k / q2 + k * k) / d
    }

    /** Añade [count] muestras de [data] (desde [offset]) a la medida. */
    fun add(
        data: FloatArray,
        offset: Int,
        count: Int,
    ) {
        val chs = channels
        var i = offset
        val end = offset + count - (count % chs)
        while (i < end) {
            var frame = 0.0
            for (c in 0 until chs) {
                val x = data[i + c]
                val ab = abs(x)
                if (ab > peak) peak = ab
                // Estante de agudos (forma directa II transpuesta).
                val j = c * 2
                val y = b0 * x + s1[j]
                s1[j] = b1 * x - a1 * y + s1[j + 1]
                s1[j + 1] = b2 * x - a2 * y
                // Pasa altos: numerador 1, −2, 1.
                val z = y + s2[j]
                s2[j] = -2.0 * y - c1 * z + s2[j + 1]
                s2[j + 1] = y - c2 * z
                frame += z * z
            }
            // Un archivo mono suena por los dos parlantes: cuenta doble, como si fuera estéreo.
            hopEnergy += if (chs == 1) frame * 2.0 else frame
            inHop++
            if (inHop == hop) {
                last[hops % 4] = hopEnergy / hop
                hops++
                if (hops >= 4) blocks += (last[0] + last[1] + last[2] + last[3]) / 4.0
                hopEnergy = 0.0
                inHop = 0
            }
            i += chs
        }
    }

    /**
     * Corta la medida entre dos trozos que no van seguidos (por ejemplo al saltar a otra parte
     * de la canción), para que no se mezclen en un mismo bloque.
     */
    fun split() {
        hops = 0
        inHop = 0
        hopEnergy = 0.0
        s1.fill(0.0)
        s2.fill(0.0)
    }

    /** La sonoridad de todo lo añadido, o null si fue muy poco o puro silencio. */
    fun result(): Loudness? {
        if (blocks.isEmpty()) return null
        // Primera puerta: fuera lo que está por debajo de −70 LUFS (silencio).
        val absolute = Math.pow(10.0, (-70.0 + 0.691) / 10.0)
        var sum = 0.0
        var n = 0
        for (e in blocks) {
            if (e > absolute) {
                sum += e
                n++
            }
        }
        if (n == 0) return null
        // Segunda puerta: fuera lo que está 10 unidades por debajo del promedio (partes muy bajitas).
        val relative = sum / n * 0.1
        var sum2 = 0.0
        var n2 = 0
        for (e in blocks) {
            if (e > absolute && e > relative) {
                sum2 += e
                n2++
            }
        }
        if (n2 == 0) return null
        val lufs = -0.691 + 10.0 * log10(sum2 / n2)
        return Loudness(lufs.toFloat(), peak)
    }
}
