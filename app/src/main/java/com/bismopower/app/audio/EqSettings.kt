package com.bismopower.app.audio

import java.util.Locale

/** Una banda del ecualizador: frecuencia central (Hz), ganancia (dB), ancho (Q) y forma. */
data class EqBand(
    val freq: Float,
    val gainDb: Float,
    val q: Float = DEFAULT_Q,
    val type: FilterType = FilterType.PEAK,
)

/** Q de una banda de una octava de ancho: así las 10 bandas cubren todo el espectro sin huecos. */
const val DEFAULT_Q = 1.41f
const val MAX_GAIN_DB = 15f
const val MAX_TONE_DB = 10f
const val MIN_FREQ = 20f
const val MAX_FREQ = 20000f
const val CUSTOM_PRESET = "Personalizado"

/** Todo lo que el usuario ajusta en el ecualizador. Es inmutable: cada cambio crea una copia. */
data class EqSettings(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val limiter: Boolean = true,
    val bands: List<EqBand> = defaultBands(),
    val preset: String = "Plano",
) {
    /** Los filtros que se aplican al audio: las 10 bandas más las perillas de graves y agudos. */
    fun filters(): List<EqBand> =
        bands +
            listOf(
                EqBand(BASS_FREQ, bassDb, 0.7f, FilterType.LOW_SHELF),
                EqBand(TREBLE_FREQ, trebleDb, 0.7f, FilterType.HIGH_SHELF),
            )

    /** Texto de una línea para guardarlo en DataStore. */
    fun encode(): String =
        buildString {
            append("v1|")
            append(if (enabled) 1 else 0).append('|')
            append(fmt(preampDb)).append('|')
            append(fmt(bassDb)).append('|')
            append(fmt(trebleDb)).append('|')
            append(if (limiter) 1 else 0).append('|')
            append(preset).append('|')
            append(bands.joinToString(";") { "${fmt(it.freq)},${fmt(it.gainDb)},${fmt(it.q)},${it.type.name}" })
        }

    companion object {
        val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        const val BASS_FREQ = 100f
        const val TREBLE_FREQ = 10000f

        fun defaultBands(gains: FloatArray? = null): List<EqBand> = FREQS.mapIndexed { i, f -> EqBand(f, gains?.getOrNull(i) ?: 0f) }

        /** Lee lo que guardó [encode]. Si algo no cuadra devuelve null y se usan los valores por defecto. */
        fun decode(text: String): EqSettings? {
            val p = text.split('|')
            if (p.size < 8 || p[0] != "v1") return null
            return runCatching {
                val bands =
                    p[7].split(';').map { b ->
                        val v = b.split(',')
                        EqBand(
                            freq = v[0].toFloat().coerceIn(MIN_FREQ, MAX_FREQ),
                            gainDb = v[1].toFloat().coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
                            q = v[2].toFloat().coerceIn(0.3f, 8f),
                            type = FilterType.valueOf(v[3]),
                        )
                    }
                require(bands.size == FREQS.size)
                EqSettings(
                    enabled = p[1] == "1",
                    preampDb = p[2].toFloat().coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB),
                    bassDb = p[3].toFloat().coerceIn(-MAX_TONE_DB, MAX_TONE_DB),
                    trebleDb = p[4].toFloat().coerceIn(-MAX_TONE_DB, MAX_TONE_DB),
                    limiter = p[5] == "1",
                    preset = p[6],
                    bands = bands,
                )
            }.getOrNull()
        }

        private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)
    }
}

/** Ajustes listos de fábrica: ganancia en dB de cada una de las 10 bandas. */
val EQ_PRESETS: List<Pair<String, FloatArray>> =
    listOf(
        "Plano" to FloatArray(10),
        "Rock" to floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
        "Pop" to floatArrayOf(-1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f),
        "Jazz" to floatArrayOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
        "Clásica" to floatArrayOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Electrónica" to floatArrayOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f),
        "Hip-hop" to floatArrayOf(5f, 4f, 1f, 3f, -1f, -1f, 1f, -1f, 2f, 3f),
        "Reguetón" to floatArrayOf(6f, 5f, 2f, 0f, -1f, 0f, 1f, 2f, 3f, 2f),
        "Más graves" to floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f),
        "Más agudos" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
        "Voz" to floatArrayOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f),
        "Acústica" to floatArrayOf(4f, 3f, 2f, 1f, 2f, 2f, 3f, 3f, 2f, 1f),
    )
