package com.bismopower.app.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Las cuentas del motor de audio, sin nada de Android ni de ExoPlayer: reciben y devuelven
 * muestras en float (de −1 a 1), con los canales intercalados (izq, der, izq, der…).
 * Los procesadores de AudioEffects.kt solo les pasan los datos.
 */

/**
 * Ecualizador paramétrico: un filtro biquad por banda y por canal, más el volumen (el
 * preamplificador y el ajuste de "volumen parejo" de la canción que suena).
 * Por dentro todo se calcula en `Double`, así subir y bajar bandas no pierde calidad.
 * Solo lo usa el hilo de audio.
 */
class EqEngine {
    private var applied: EqSettings? = null
    private var appliedLoudness: Loudness? = null
    private var rate = 0
    private var channels = 0
    private var active = false

    // Volumen que se quiere y el que se está aplicando: el segundo persigue al primero en unos
    // 15 ms, así cambiar de canción o mover el preamp no hace "clic".
    private var gain = 1.0
    private var current = 1.0
    private var glide = 1.0
    private var coefs: Array<BiquadCoefs> = emptyArray()
    private var skip = BooleanArray(0)

    // Memoria de cada filtro (las dos últimas muestras), una por filtro y canal: [filtro * canales + canal].
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)

    /**
     * Recalcula los filtros y el volumen si cambiaron los ajustes, la canción ([loudness] es lo
     * fuerte que suena; null = sin medir), la frecuencia de muestreo o los canales.
     */
    fun prepare(
        s: EqSettings,
        loudness: Loudness?,
        sampleRate: Int,
        channelCount: Int,
    ) {
        val formatChanged = sampleRate != rate || channelCount != channels
        if (s === applied && loudness == appliedLoudness && !formatChanged) return
        val first = applied == null
        // El preamp es parte del ecualizador (apagado no se aplica); el volumen parejo va aparte.
        val db =
            (if (s.enabled) s.effectivePreampDb else 0f) +
                (if (s.normalize) normalizationGainDb(loudness, s.limiter) else 0f)
        gain = 10.0.pow(db / 20.0)
        if (first) current = gain
        glide = 1.0 - exp(-1.0 / (GLIDE_SECONDS * sampleRate))
        appliedLoudness = loudness
        if (s === applied && !formatChanged) return
        val filters = s.filters()
        val wasActive = active
        active = s.enabled
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
        if (formatChanged || z1.size != filters.size * channelCount || (active && !wasActive)) {
            z1 = DoubleArray(filters.size * channelCount)
            z2 = DoubleArray(filters.size * channelCount)
        } else {
            for (f in newSkip.indices) {
                if (newSkip[f]) {
                    for (c in 0 until channelCount) {
                        z1[f * channelCount + c] = 0.0
                        z2[f * channelCount + c] = 0.0
                    }
                }
            }
        }
        skip = newSkip
        applied = s
        rate = sampleRate
        channels = channelCount
    }

    /** Olvida el sonido anterior (al saltar a otra parte de la canción). */
    fun clearMemory() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    /**
     * Ajusta el volumen y ecualiza [count] muestras de [data] en el mismo arreglo. Con el
     * ecualizador apagado y sin ajuste de volumen no toca nada.
     */
    fun process(
        data: FloatArray,
        count: Int,
    ) {
        if (channels <= 0) return
        if (!active && gain == 1.0 && current == 1.0) return
        val chs = channels
        val on = active
        val n = coefs.size
        var g = current
        val target = gain
        var ch = 0
        for (i in 0 until count) {
            if (ch == 0 && g != target) {
                g += (target - g) * glide
                if (abs(target - g) < 1e-5) g = target
            }
            var v = data[i] * g
            if (!on) {
                data[i] = v.toFloat()
                ch++
                if (ch == chs) ch = 0
                continue
            }
            for (f in 0 until n) {
                if (skip[f]) continue
                val k = coefs[f]
                val j = f * chs + ch
                // Forma directa II transpuesta: la más estable con números en Double.
                val y = k.b0 * v + z1[j]
                z1[j] = k.b1 * v - k.a1 * y + z2[j]
                z2[j] = k.b2 * v - k.a2 * y
                v = y
            }
            data[i] = v.toFloat()
            ch++
            if (ch == chs) ch = 0
        }
        current = g
        if (!on) return
        // Tras un silencio largo la memoria se llena de números diminutos que gastan procesador.
        for (j in z1.indices) {
            if (abs(z1[j]) < TINY) z1[j] = 0.0
            if (abs(z2[j]) < TINY) z2[j] = 0.0
        }
    }

    private companion object {
        const val TINY = 1e-25
        const val GLIDE_SECONDS = 0.015
    }
}

/**
 * Limitador con anticipación: mira el audio unos milisegundos antes de que suene y, si viene un
 * pico que se pasaría del máximo, baja el volumen suavemente justo a tiempo y luego lo devuelve
 * poco a poco. A diferencia de recortar o "redondear" cada pico, no cambia la forma de la onda,
 * así que no suena a distorsión. Los canales bajan juntos para no mover la imagen estéreo.
 *
 * El audio sale con [latencyFrames] cuadros de retraso (unos 5 ms), que es lo que le da tiempo a
 * reaccionar. No añade silencio: al empezar se guarda esos primeros cuadros (devuelve menos de
 * los que recibe) y al terminar la canción los entrega con [drain]. Así entran y salen los
 * mismos cuadros y las canciones pegadas (gapless) siguen sin hueco.
 */
class LookaheadLimiter {
    private var channels = 0
    private var look = 0

    // Cuánto tiempo mantiene bajada la ganancia después de un pico (retraso + espera).
    private var window = 0
    private var delay = FloatArray(0)
    private var pos = 0
    private var env = 1f
    private var attack = 0f
    private var release = 0f
    private var wasOn = false

    // Mínimo de la ganancia pedida en los últimos `window` cuadros (cola monótona en anillo).
    private var qVal = FloatArray(0)
    private var qAt = LongArray(0)
    private var qHead = 0
    private var qCount = 0
    private var clock = 0L

    /** Cuadros de audio real que siguen dentro del retraso y hay que sacar al terminar la canción. */
    var pendingFrames = 0
        private set

    /** Cuadros de retraso (0 si está sin configurar). */
    val latencyFrames: Int
        get() = look

    /** La ganancia más baja que aplicó en el último bloque (1 = no hizo nada). */
    var lastLowest = 1f
        private set

    /** Prepara el limitador para un formato y lo deja vacío. Con valores no válidos queda apagado. */
    fun configure(
        sampleRate: Int,
        channelCount: Int,
    ) {
        if (sampleRate <= 0 || channelCount <= 0) {
            channels = 0
            look = 0
            pendingFrames = 0
            return
        }
        val frames = max(8, (sampleRate * LOOKAHEAD_MS / 1000f).toInt())
        if (frames != look || channelCount != channels) {
            channels = channelCount
            look = frames
            // La espera evita que la ganancia suba y baje con cada ciclo de un bajo sostenido.
            window = frames + (sampleRate * HOLD_MS / 1000f).toInt()
            delay = FloatArray(frames * channelCount)
            qVal = FloatArray(window + 2)
            qAt = LongArray(window + 2)
            // Al llegar el pico al final del retraso la ganancia ya recorrió el 99,9 % del camino.
            attack = 1f - 0.001f.pow(1f / frames)
            release = 1f - exp(-1f / (RELEASE_MS / 1000f * sampleRate))
        }
        reset()
    }

    fun reset() {
        delay.fill(0f)
        pos = 0
        env = 1f
        qHead = 0
        qCount = 0
        clock = 0L
        wasOn = false
        pendingFrames = 0
        lastLowest = 1f
    }

    /**
     * Procesa [count] muestras de [data] y deja el resultado al principio del mismo arreglo (es el
     * audio de hace [latencyFrames] cuadros). Devuelve cuántas muestras dejó: las mismas que
     * recibió, salvo al empezar, cuando el retraso todavía se está llenando.
     * Con [on] = false solo retrasa, sin tocar el volumen.
     */
    fun process(
        data: FloatArray,
        count: Int,
        on: Boolean,
    ): Int {
        val chs = channels
        if (chs <= 0 || look <= 0) return count
        if (on != wasOn) {
            qHead = 0
            qCount = 0
            wasOn = on
        }
        val frames = count / chs
        val cap = qVal.size
        var lowest = 1f
        var i = 0
        var w = 0
        for (f in 0 until frames) {
            if (on) {
                var peak = 0f
                for (c in 0 until chs) {
                    val a = abs(data[i + c])
                    if (a > peak) peak = a
                }
                val wanted = if (peak > CEILING) CEILING / peak else 1f
                // La ganancia pedida entra a la cola; salen las que ya no hacen falta.
                while (qCount > 0 && qVal[(qHead + qCount - 1) % cap] >= wanted) qCount--
                val tail = (qHead + qCount) % cap
                qVal[tail] = wanted
                qAt[tail] = clock
                qCount++
                while (qAt[qHead] < clock - window) {
                    qHead = (qHead + 1) % cap
                    qCount--
                }
                val target = qVal[qHead]
                env += (target - env) * (if (target < env) attack else release)
                if (env < lowest) lowest = env
            } else {
                env = 1f
            }
            // Hasta que el retraso se llena, lo que saldría de él es vacío: no se entrega.
            val ready = clock >= look
            clock++
            val base = pos * chs
            for (c in 0 until chs) {
                val old = delay[base + c]
                delay[base + c] = data[i + c]
                // El tope final casi nunca actúa: solo atrapa lo poco que se le escape a la curva.
                if (ready) data[w + c] = if (on) (old * env).coerceIn(-1f, 1f) else old
            }
            i += chs
            if (ready) w += chs
            pos++
            if (pos == look) pos = 0
        }
        pendingFrames = min(look.toLong(), clock).toInt()
        lastLowest = lowest
        return w
    }

    /**
     * Saca a [out] lo que quedó dentro del retraso (al terminar la canción). [out] debe tener al
     * menos [latencyFrames] × canales de tamaño. Devuelve cuántas muestras escribió.
     */
    fun drain(
        out: FloatArray,
        on: Boolean,
    ): Int {
        val count = look * channels
        if (pendingFrames <= 0 || count <= 0 || out.size < count) {
            pendingFrames = 0
            return 0
        }
        out.fill(0f, 0, count)
        val written = process(out, count, on)
        pendingFrames = 0
        return written
    }

    companion object {
        /** Lo máximo que deja pasar: justo por debajo del tope digital. */
        const val CEILING = 0.985f
        const val LOOKAHEAD_MS = 5f
        const val HOLD_MS = 30f
        const val RELEASE_MS = 160f
    }
}

/** Ruido triangular de ±1 escalón para pasar a 16 bits sin la distorsión de redondear en seco. */
class Dither {
    private var seed = -0x61c88647

    private fun next(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return (x ushr 8) * (1f / 16_777_216f)
    }

    /** Convierte [count] muestras float a 16 bits. */
    fun toInt16(
        input: FloatArray,
        output: ShortArray,
        count: Int,
    ) {
        for (i in 0 until count) {
            val v = input[i] * 32767f + (next() - next())
            val r = if (v >= 0f) (v + 0.5f).toInt() else (v - 0.5f).toInt()
            output[i] = r.coerceIn(-32768, 32767).toShort()
        }
    }
}
