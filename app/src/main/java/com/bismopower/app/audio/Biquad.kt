package com.bismopower.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Forma de cada filtro del ecualizador. */
enum class FilterType {
    /** Campana: sube o baja una zona alrededor de la frecuencia. */
    PEAK,

    /** Estante de graves: sube o baja todo lo que está por debajo de la frecuencia. */
    LOW_SHELF,

    /** Estante de agudos: sube o baja todo lo que está por encima de la frecuencia. */
    HIGH_SHELF,
}

/**
 * Coeficientes de un filtro biquad, ya divididos por a0.
 *
 * Las fórmulas son las del "Audio EQ Cookbook" de Robert Bristow-Johnson, las mismas que usan
 * la mayoría de ecualizadores de software. Cada muestra se calcula así:
 *
 *     y[n] = b0·x[n] + b1·x[n-1] + b2·x[n-2] − a1·y[n-1] − a2·y[n-2]
 */
class BiquadCoefs(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /** Cuántos dB sube o baja este filtro la frecuencia [freq] (para dibujar la curva). */
    fun magnitudeDb(
        freq: Double,
        sampleRate: Double,
    ): Double {
        val w = 2.0 * PI * freq / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2.0 * w)
        val s2 = sin(2.0 * w)
        val nr = b0 + b1 * c1 + b2 * c2
        val ni = -(b1 * s1 + b2 * s2)
        val dr = 1.0 + a1 * c1 + a2 * c2
        val di = -(a1 * s1 + a2 * s2)
        val mag2 = (nr * nr + ni * ni) / (dr * dr + di * di)
        return 10.0 * log10(mag2.coerceAtLeast(1e-12))
    }

    companion object {
        /** Filtro que deja pasar el audio sin cambios. */
        val IDENTITY = BiquadCoefs(1.0, 0.0, 0.0, 0.0, 0.0)

        fun design(
            type: FilterType,
            freq: Double,
            gainDb: Double,
            q: Double,
            sampleRate: Double,
        ): BiquadCoefs {
            // Por encima de la mitad de la frecuencia de muestreo el filtro deja de ser estable.
            val f = freq.coerceIn(10.0, sampleRate * 0.45)
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f / sampleRate
            val cosW = cos(w0)
            val alpha = sin(w0) / (2.0 * q.coerceAtLeast(0.1))
            val sqrtA2Alpha = 2.0 * sqrt(a) * alpha

            val k: DoubleArray =
                when (type) {
                    FilterType.PEAK ->
                        doubleArrayOf(
                            1.0 + alpha * a,
                            -2.0 * cosW,
                            1.0 - alpha * a,
                            1.0 + alpha / a,
                            -2.0 * cosW,
                            1.0 - alpha / a,
                        )
                    FilterType.LOW_SHELF ->
                        doubleArrayOf(
                            a * ((a + 1.0) - (a - 1.0) * cosW + sqrtA2Alpha),
                            2.0 * a * ((a - 1.0) - (a + 1.0) * cosW),
                            a * ((a + 1.0) - (a - 1.0) * cosW - sqrtA2Alpha),
                            (a + 1.0) + (a - 1.0) * cosW + sqrtA2Alpha,
                            -2.0 * ((a - 1.0) + (a + 1.0) * cosW),
                            (a + 1.0) + (a - 1.0) * cosW - sqrtA2Alpha,
                        )
                    FilterType.HIGH_SHELF ->
                        doubleArrayOf(
                            a * ((a + 1.0) + (a - 1.0) * cosW + sqrtA2Alpha),
                            -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW),
                            a * ((a + 1.0) + (a - 1.0) * cosW - sqrtA2Alpha),
                            (a + 1.0) - (a - 1.0) * cosW + sqrtA2Alpha,
                            2.0 * ((a - 1.0) - (a + 1.0) * cosW),
                            (a + 1.0) - (a - 1.0) * cosW - sqrtA2Alpha,
                        )
                }
            val a0 = k[3]
            return BiquadCoefs(k[0] / a0, k[1] / a0, k[2] / a0, k[4] / a0, k[5] / a0)
        }
    }
}
