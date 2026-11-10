package com.bismopower.app.library

import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.provider.MediaStore
import com.bismopower.app.audio.Loudness
import com.bismopower.app.audio.LoudnessMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lo fuerte que suena cada canción, ya medido. Es una caché: se guarda en un archivo de texto
 * (`loudness.tsv`: id, sonoridad, pico) y no en Room, porque se puede volver a calcular y así
 * no hace falta una migración de la base de datos.
 *
 * [get] se llama desde el hilo de audio, por eso todo vive en memoria.
 */
class LoudnessStore(
    context: Context,
) {
    private val file = File(context.filesDir, "loudness.tsv")
    private val map = ConcurrentHashMap<Long, Loudness>()

    @Volatile private var loaded = false

    /** Lee el archivo (una sola vez). Llamar fuera del hilo principal. */
    @Synchronized
    fun load() {
        if (loaded) return
        runCatching {
            if (file.exists()) {
                file.forEachLine { line ->
                    val p = line.split('\t')
                    val id = p.getOrNull(0)?.toLongOrNull()
                    val lufs = p.getOrNull(1)?.toFloatOrNull()
                    val peak = p.getOrNull(2)?.toFloatOrNull()
                    if (id != null && lufs != null && peak != null && !lufs.isNaN()) map[id] = Loudness(lufs, peak)
                }
            }
        }
        loaded = true
    }

    operator fun get(id: Long): Loudness? = map[id]

    fun has(id: Long): Boolean = map.containsKey(id)

    /**
     * Guarda una medida. Las canciones que no se pudieron leer solo se recuerdan mientras la app
     * siga abierta, así se vuelven a intentar la próxima vez.
     */
    @Synchronized
    fun put(
        id: Long,
        loudness: Loudness,
    ) {
        map[id] = loudness
        if (loudness.valid) runCatching { file.appendText("$id\t${loudness.lufs}\t${loudness.peak}\n") }
    }
}

/**
 * Mide la sonoridad de las canciones en segundo plano, de a una y con prioridad baja para no
 * estorbar a la música: primero las que están por sonar ([prioritize]) y después el resto de
 * la biblioteca ([setLibrary]). Solo trabaja con "Volumen parejo" encendido ([setEnabled]).
 *
 * Para no decodificar canciones enteras mide tres trozos de 20 s (al 15 %, 45 % y 75 %);
 * las de menos de 75 s se miden completas.
 */
class LoudnessScanner(
    private val context: Context,
    private val store: LoudnessStore,
    scope: CoroutineScope,
) {
    /** Cuántas canciones de la biblioteca ya están medidas, cuántas son y cuántas no se pudieron leer. */
    data class Progress(
        val done: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val lock = Any()
    private val urgent = ArrayDeque<Long>()
    private var library: List<Long> = emptyList()
    private var cursor = 0

    @Volatile private var enabled = false
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val worker =
        Executors
            .newSingleThreadExecutor { r ->
                Thread(r, "bismo-loudness").apply {
                    priority = Thread.MIN_PRIORITY
                    isDaemon = true
                }
            }.asCoroutineDispatcher()

    init {
        scope.launch(worker) {
            store.load()
            publish()
            while (true) {
                val job = if (enabled) next() else null
                if (job == null) {
                    wake.receive()
                    continue
                }
                val result = runCatching { measure(job.first) }.getOrNull()
                store.put(job.first, result ?: Loudness.UNREADABLE)
                publish()
                // Entre canciones de la biblioteca descansa un poco para no calentar el teléfono.
                if (!job.second) delay(120)
            }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        wake.trySend(Unit)
    }

    /** Todas las canciones de la biblioteca (para medirlas poco a poco y contar el avance). */
    fun setLibrary(ids: List<Long>) {
        synchronized(lock) {
            library = ids
            cursor = 0
        }
        publish()
        wake.trySend(Unit)
    }

    /** Las canciones que van a sonar pronto: se miden antes que las demás. */
    fun prioritize(ids: List<Long>) {
        synchronized(lock) {
            urgent.clear()
            urgent.addAll(ids)
        }
        wake.trySend(Unit)
    }

    /** La siguiente canción sin medir (id, si es urgente), o null si no queda ninguna. */
    private fun next(): Pair<Long, Boolean>? =
        synchronized(lock) {
            while (urgent.isNotEmpty()) {
                val id = urgent.removeFirst()
                if (!store.has(id)) return@synchronized id to true
            }
            while (cursor < library.size) {
                val id = library[cursor++]
                if (!store.has(id)) return@synchronized id to false
            }
            null
        }

    private fun publish() {
        val ids = synchronized(lock) { library }
        var done = 0
        var failed = 0
        for (id in ids) {
            val l = store[id] ?: continue
            done++
            if (!l.valid) failed++
        }
        _progress.value = Progress(done, ids.size, failed)
    }

    // ---------------------------------------------------------------- medir una canción

    /** Lo que se va juntando mientras se decodifica una canción. */
    private class Decoding {
        var meter: LoudnessMeter? = null
        var isFloat = false
        var failed = false
        var floats = FloatArray(0)
        var shorts = ShortArray(0)
    }

    private fun measure(id: Long): Loudness? {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    format = f
                    break
                }
            }
            val audio = format ?: return null
            val mime = audio.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (audio.containsKey(MediaFormat.KEY_DURATION)) audio.getLong(MediaFormat.KEY_DURATION) else 0L

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(audio, null, null, 0)
            decoder.start()

            val state = Decoding()
            val windows =
                if (durationUs < WHOLE_BELOW_US) {
                    listOf(0L to Long.MAX_VALUE)
                } else {
                    WINDOW_STARTS.map { (durationUs * it).toLong() }.map { it to it + WINDOW_US }
                }
            windows.forEachIndexed { n, (startUs, endUs) ->
                if (state.failed) return null
                if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                if (n > 0) {
                    // El trozo anterior terminó con "fin de flujo": hay que vaciar el decodificador para seguir.
                    decoder.flush()
                    state.meter?.split()
                }
                decodeUntil(extractor, decoder, endUs, state)
            }
            return if (state.failed) null else state.meter?.result()
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Decodifica desde donde está el extractor hasta [endUs] (o el final) y se lo pasa al medidor. */
    private fun decodeUntil(
        extractor: MediaExtractor,
        codec: MediaCodec,
        endUs: Long,
        state: Decoding,
    ) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        val deadline = SystemClock.elapsedRealtime() + WINDOW_TIMEOUT_MS
        while (true) {
            if (SystemClock.elapsedRealtime() > deadline) {
                state.failed = true
                return
            }
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(DEQUEUE_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)
                    val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    val timeUs = extractor.sampleTime
                    if (size < 0 || timeUs > endUs) {
                        codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, timeUs, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, DEQUEUE_US)
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                useFormat(codec.outputFormat, state)
            } else if (out >= 0) {
                if (info.size > 0) {
                    if (state.meter == null) useFormat(codec.outputFormat, state)
                    codec.getOutputBuffer(out)?.let { feed(it, info, state) }
                }
                codec.releaseOutputBuffer(out, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
            if (state.failed) return
        }
    }

    private fun useFormat(
        format: MediaFormat,
        state: Decoding,
    ) {
        if (state.meter != null) return
        val rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 0
        val encoding =
            if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        val known = encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT
        if (rate <= 0 || channels <= 0 || !known) {
            state.failed = true
            return
        }
        state.isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
        state.meter = LoudnessMeter(rate, channels)
    }

    private fun feed(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        state: Decoding,
    ) {
        val meter = state.meter ?: return
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())
        val count: Int
        if (state.isFloat) {
            count = info.size / 4
            if (state.floats.size < count) state.floats = FloatArray(count)
            buffer.asFloatBuffer().get(state.floats, 0, count)
        } else {
            count = info.size / 2
            if (state.floats.size < count) state.floats = FloatArray(count)
            if (state.shorts.size < count) state.shorts = ShortArray(count)
            buffer.asShortBuffer().get(state.shorts, 0, count)
            for (i in 0 until count) state.floats[i] = state.shorts[i] * (1f / 32768f)
        }
        meter.add(state.floats, 0, count)
    }

    private companion object {
        const val WINDOW_US = 20_000_000L
        const val WHOLE_BELOW_US = 75_000_000L
        val WINDOW_STARTS = doubleArrayOf(0.15, 0.45, 0.75)
        const val DEQUEUE_US = 5_000L
        const val WINDOW_TIMEOUT_MS = 12_000L
    }
}
