package com.bismopower.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Efectos de "Sonido 3D" (propios de BismoPower, pensados para audífonos).
 * Cada modo mezcla cuatro ingredientes en distinta cantidad:
 *  - [width]: abre el sonido a los lados (sube lo que es distinto entre izquierda y derecha).
 *  - [cross]: pasa un poco de cada lado al otro, con retraso, como al oír parlantes al frente.
 *  - [early]: primeros rebotes de una sala, que dan sensación de espacio.
 *  - [reverb]: la cola de eco suave de una sala.
 * [trim] compensa el volumen para que encender el efecto no suene más fuerte ni más bajo.
 */
enum class SpatialMode(
    val id: String,
    val label: String,
    val hint: String,
    val width: Float,
    val cross: Float,
    val early: Float,
    val reverb: Float,
    val trim: Float,
) {
    OFF("OFF", "Apagado", "El sonido tal como viene en la canción", 0f, 0f, 0f, 0f, 1f),
    WIDE("WIDE", "Amplio", "Abre el sonido hacia los lados", 1.0f, 0f, 0.35f, 0f, 0.94f),
    FRONT("FRONT", "Frontal", "Como oír dos parlantes al frente", 0.25f, 0.5f, 0.3f, 0.2f, 0.86f),
    ROOM("ROOM", "Sala", "Como estar en una sala, con algo de eco", 0.35f, 0.15f, 0.6f, 0.75f, 0.9f),
    SURROUND("SURROUND", "Envolvente", "El más grande: te rodea por todos lados", 1.1f, 0f, 0.75f, 0.5f, 0.88f),
    ;

    companion object {
        fun of(id: String): SpatialMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/**
 * El motor del Sonido 3D. Trabaja con audio estéreo en float, intercalado (izq, der, izq, der…),
 * en el mismo arreglo. Sin dependencias de Android; solo lo usa el hilo de audio.
 *
 * Los graves no se tocan (se quedan al centro, con toda su fuerza) y lo que añade a los lados
 * se cancela al sumar los dos canales, así que en un solo parlante la música suena igual.
 * Al cambiar de modo, el efecto baja a cero y sube con el modo nuevo: no hace "clic".
 */
class SpatialEngine {
    private var rate = 0

    // Modo que está sonando y el que se pidió; `mix` va de 0 (sin efecto) a 1.
    private var active = SpatialMode.OFF
    private var wanted = SpatialMode.OFF
    private var mix = 0f
    private var amount = 0f
    private var wantedAmount = 0f
    private var glide = 0f

    // Filtros de un polo (memoria).
    private var sideLow = 0f
    private var earlyLow = 0f
    private var reverbLow = 0f
    private var crossL = 0f
    private var crossR = 0f
    private var sideHp = 0f
    private var earlyHp = 0f
    private var crossLp = 0f

    // Retrasos: el centro (para los rebotes) y cada canal (para el cruce).
    private var midLine = FloatArray(0)
    private var midPos = 0
    private var tapA = 0
    private var tapB = 0
    private var tapC = 0
    private var lineL = FloatArray(0)
    private var lineR = FloatArray(0)
    private var crossPos = 0

    // Sala: 4 peines + 2 pasa-todo por canal (los de la derecha un poco más largos).
    private val combL = Array(COMBS.size) { Comb() }
    private val combR = Array(COMBS.size) { Comb() }
    private val allL = Array(ALLPASS.size) { Allpass() }
    private val allR = Array(ALLPASS.size) { Allpass() }

    /** Prepara los retrasos para una frecuencia de muestreo y borra todo lo que estaba sonando. */
    fun configure(sampleRate: Int) {
        if (sampleRate <= 0) {
            rate = 0
            return
        }
        if (sampleRate != rate) {
            rate = sampleRate
            val k = sampleRate / 44100f
            tapA = (0.0113f * sampleRate).toInt()
            tapB = (0.0179f * sampleRate).toInt()
            tapC = (0.0237f * sampleRate).toInt()
            midLine = FloatArray(tapC + 2)
            val crossDelay = max(1, (0.00028f * sampleRate).toInt())
            lineL = FloatArray(crossDelay)
            lineR = FloatArray(crossDelay)
            for (i in COMBS.indices) {
                combL[i].resize((COMBS[i] * k).toInt())
                combR[i].resize(((COMBS[i] + SPREAD) * k).toInt())
            }
            for (i in ALLPASS.indices) {
                allL[i].resize((ALLPASS[i] * k).toInt())
                allR[i].resize(((ALLPASS[i] + SPREAD) * k).toInt())
            }
            sideHp = pole(150f, sampleRate)
            earlyHp = pole(250f, sampleRate)
            crossLp = pole(700f, sampleRate)
            glide = 1f - exp(-1f / (0.05f * sampleRate))
        }
        clear()
    }

    private fun pole(
        hz: Float,
        sampleRate: Int,
    ): Float = 1f - exp(-2f * PI.toFloat() * hz / sampleRate)

    private fun clear() {
        midLine.fill(0f)
        lineL.fill(0f)
        lineR.fill(0f)
        midPos = 0
        crossPos = 0
        sideLow = 0f
        earlyLow = 0f
        reverbLow = 0f
        crossL = 0f
        crossR = 0f
        for (c in combL) c.clear()
        for (c in combR) c.clear()
        for (a in allL) a.clear()
        for (a in allR) a.clear()
    }

    /** Qué modo se quiere y con cuánta intensidad (0 a 1). El cambio se hace suave dentro de [process]. */
    fun set(
        mode: SpatialMode,
        intensity: Float,
    ) {
        wanted = mode
        wantedAmount = intensity.coerceIn(0f, 1f)
    }

    /** Aplica el efecto a [count] muestras estéreo de [data]. Apagado del todo, no toca nada. */
    fun process(
        data: FloatArray,
        count: Int,
    ) {
        if (rate <= 0) return
        if (active == SpatialMode.OFF && wanted == SpatialMode.OFF && mix == 0f) return
        if (active == SpatialMode.OFF && mix == 0f) {
            // Se acaba de encender: empieza limpio, sin restos de la última vez.
            clear()
            active = wanted
            amount = wantedAmount
        }
        var mode = active
        var i = 0
        val end = count - (count and 1)
        while (i < end) {
            // El efecto persigue a su objetivo; para cambiar de modo primero baja a cero.
            val target = if (wanted == mode) 1f else 0f
            mix += (target - mix) * glide
            if (target == 0f && mix < 0.002f) {
                mix = 0f
                mode = wanted
                active = wanted
                if (mode == SpatialMode.OFF) break
            } else if (target == 1f && mix > 0.998f) {
                mix = 1f
            }
            amount += (wantedAmount - amount) * glide
            val k = mix * amount

            val l = data[i]
            val r = data[i + 1]
            val mid = (l + r) * 0.5f
            var side = (l - r) * 0.5f

            // 1) Abrir a los lados, solo por encima de los graves.
            sideLow += (side - sideLow) * sideHp
            side += (side - sideLow) * mode.width * k

            // 2) Primeros rebotes: copias retrasadas del centro, que van solo a los lados.
            val size = midLine.size
            midLine[midPos] = mid
            val e =
                0.6f * midLine[(midPos - tapA + size) % size] -
                    0.45f * midLine[(midPos - tapB + size) % size] +
                    0.3f * midLine[(midPos - tapC + size) % size]
            midPos++
            if (midPos == size) midPos = 0
            earlyLow += (e - earlyLow) * earlyHp
            side += (e - earlyLow) * mode.early * k

            var outL = mid + side
            var outR = mid - side

            // 3) Cruce: un poco de cada lado llega al otro oído, tarde y sin agudos.
            if (mode.cross > 0f) {
                val oldL = lineL[crossPos]
                val oldR = lineR[crossPos]
                lineL[crossPos] = outL
                lineR[crossPos] = outR
                crossPos++
                if (crossPos == lineL.size) crossPos = 0
                crossL += (oldL - crossL) * crossLp
                crossR += (oldR - crossR) * crossLp
                val g = mode.cross * k
                outL += crossR * g
                outR += crossL * g
            }

            // 4) Sala: eco suave hecho con el centro (sin graves, para que no se emborrone).
            if (mode.reverb > 0f) {
                reverbLow += (mid - reverbLow) * earlyHp
                val feed = (mid - reverbLow) * REVERB_IN + ANTI_DENORMAL
                var wetL = 0f
                var wetR = 0f
                for (c in combL) wetL += c.run(feed)
                for (c in combR) wetR += c.run(feed)
                for (a in allL) wetL = a.run(wetL)
                for (a in allR) wetR = a.run(wetR)
                val g = mode.reverb * k
                outL += wetL * g
                outR += wetR * g
            }

            val trim = 1f + (mode.trim - 1f) * k
            data[i] = outL * trim
            data[i + 1] = outR * trim
            i += 2
        }
    }

    /** Peine con realimentación y filtro: cada vuelta del eco pierde agudos, como en una sala real. */
    private class Comb {
        private var buffer = FloatArray(1)
        private var pos = 0
        private var store = 0f

        fun resize(n: Int) {
            buffer = FloatArray(max(1, n))
            pos = 0
            store = 0f
        }

        fun clear() {
            buffer.fill(0f)
            store = 0f
        }

        fun run(input: Float): Float {
            val out = buffer[pos]
            store = out * (1f - DAMP) + store * DAMP
            if (abs(store) < 1e-20f) store = 0f
            buffer[pos] = input + store * FEEDBACK
            pos++
            if (pos == buffer.size) pos = 0
            return out
        }
    }

    /** Pasa-todo: desordena el eco para que suene difuso y no metálico. */
    private class Allpass {
        private var buffer = FloatArray(1)
        private var pos = 0

        fun resize(n: Int) {
            buffer = FloatArray(max(1, n))
            pos = 0
        }

        fun clear() {
            buffer.fill(0f)
        }

        fun run(input: Float): Float {
            val held = buffer[pos]
            val out = held - input
            buffer[pos] = input + held * 0.5f
            pos++
            if (pos == buffer.size) pos = 0
            return out
        }
    }

    private companion object {
        // Largos (en muestras a 44,1 kHz) de la sala clásica "Freeverb".
        val COMBS = intArrayOf(1116, 1277, 1422, 1557)
        val ALLPASS = intArrayOf(556, 341)
        const val SPREAD = 23

        // Sala pequeña: el eco se apaga en algo menos de un segundo.
        const val FEEDBACK = 0.78f
        const val DAMP = 0.3f
        const val REVERB_IN = 0.06f
        const val ANTI_DENORMAL = 1e-18f
    }
}
