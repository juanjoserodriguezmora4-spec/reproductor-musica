package com.bismopower.app.library

import java.nio.charset.Charset

/** Una línea de letra. En las letras sin tiempos, [timeMs] es -1. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/** Letra de una canción. [synced] = cada línea tiene su momento (formato LRC). */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** De dónde salió: "archivo .lrc" o "incluida en la canción". */
    val source: String,
)

/**
 * Lee letras en formato LRC:
 *
 *     [ar:Artista]
 *     [offset:+200]
 *     [00:12.50]Primera línea
 *     [00:17.20][01:40.00]Línea que se repite (dos momentos)
 *
 * Si el texto no trae tiempos, se devuelve como letra normal (sin sincronizar).
 */
object LrcParser {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val META = Regex("""^\[(ar|ti|al|au|by|re|ve|length|offset|#)\s*:.*]$""", RegexOption.IGNORE_CASE)

    fun parse(
        raw: String,
        source: String,
    ): Lyrics? {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return null
        // En LRC, un offset positivo hace que la letra aparezca antes.
        val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()
        for (line in text.split('\n')) {
            val trimmed = line.trim()
            var rest = trimmed
            val times = ArrayList<Long>()
            while (true) {
                val m = TIME.find(rest) ?: break
                if (m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms =
                    when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                times += (min * 60_000 + sec * 1_000 + ms - offset).coerceAtLeast(0L)
                rest = rest.substring(m.range.last + 1)
            }
            if (times.isNotEmpty()) {
                val words = rest.trim()
                times.forEach { timed += LyricLine(it, words) }
            } else if (!META.matches(trimmed)) {
                plain += LyricLine(-1, trimmed)
            }
        }

        if (timed.isNotEmpty()) {
            timed.sortBy { it.timeMs }
            return Lyrics(timed, synced = true, source = source)
        }
        // Sin tiempos: se quitan las líneas en blanco del principio y del final.
        val lines = plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
        return if (lines.isEmpty()) null else Lyrics(lines, synced = false, source = source)
    }
}

/**
 * Busca la letra guardada DENTRO del archivo de audio, sin librerías:
 * - MP3: etiqueta ID3v2, cuadro USLT.
 * - FLAC, OGG y Opus: comentario LYRICS o UNSYNCEDLYRICS.
 * - M4A: átomo ©lyr.
 *
 * Recibe el inicio y el final del archivo (las etiquetas casi siempre están ahí).
 */
object TagLyrics {
    fun find(
        head: ByteArray,
        tail: ByteArray,
    ): String? = id3(head) ?: vorbis(head) ?: mp4(head) ?: mp4(tail)

    // ---------- ID3v2 (MP3) ----------

    private fun id3(b: ByteArray): String? {
        if (b.size < 10 || b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return null
        val version = b[3].toInt()
        if (version < 3) return null
        val flags = b[5].toInt()
        val end = minOf(10 + synchsafe(b, 6), b.size)
        var pos = 10
        if (flags and 0x40 != 0 && b.size >= 14) {
            pos += if (version >= 4) synchsafe(b, 10) else int32(b, 10) + 4
        }
        while (pos + 10 <= end) {
            if (b[pos] == 0.toByte()) break
            val id = String(b, pos, 4, Charsets.ISO_8859_1)
            val size = if (version >= 4) synchsafe(b, pos + 4) else int32(b, pos + 4)
            if (size <= 0 || pos + 10 + size > end) break
            if (id == "USLT") return uslt(b, pos + 10, size)
            pos += 10 + size
        }
        return null
    }

    /** USLT: codificación (1 byte), idioma (3), descripción terminada en cero y el texto. */
    private fun uslt(
        b: ByteArray,
        start: Int,
        size: Int,
    ): String? {
        if (size < 5) return null
        val enc = b[start].toInt()
        val end = start + size
        var p = start + 4
        val wide = enc == 1 || enc == 2
        // Saltar la descripción.
        if (wide) {
            while (p + 1 < end && !(b[p] == 0.toByte() && b[p + 1] == 0.toByte())) p += 2
            p += 2
        } else {
            while (p < end && b[p] != 0.toByte()) p++
            p += 1
        }
        if (p >= end) return null
        val charset: Charset =
            when (enc) {
                0 -> Charsets.ISO_8859_1
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
        return String(b, p, end - p, charset).trimEnd('\u0000').takeIf { it.isNotBlank() }
    }

    // ---------- Comentarios Vorbis (FLAC, OGG, Opus) ----------

    private val VORBIS_KEYS = listOf("LYRICS=", "UNSYNCEDLYRICS=")

    /**
     * Cada comentario Vorbis es: largo (4 bytes, al revés) + "CLAVE=valor". Se busca la clave
     * sin importar mayúsculas y se lee el largo que tiene justo antes.
     */
    private fun vorbis(b: ByteArray): String? {
        for (key in VORBIS_KEYS) {
            val k = key.toByteArray(Charsets.US_ASCII)
            var i = 4
            while (i + k.size <= b.size) {
                if (matchesIgnoreCase(b, i, k)) {
                    val len = int32le(b, i - 4)
                    if (len >= k.size && i + len <= b.size) {
                        return String(b, i + k.size, len - k.size, Charsets.UTF_8).takeIf { it.isNotBlank() }
                    }
                }
                i++
            }
        }
        return null
    }

    // ---------- MP4 / M4A ----------

    /** ©lyr → [tamaño]["data"][tipo 4][idioma 4][texto]. */
    private fun mp4(b: ByteArray): String? {
        val tag = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var i = 0
        while (i + 4 <= b.size) {
            if (b[i] == tag[0] && b[i + 1] == tag[1] && b[i + 2] == tag[2] && b[i + 3] == tag[3]) {
                val d = i + 4
                if (d + 16 <= b.size && String(b, d + 4, 4, Charsets.ISO_8859_1) == "data") {
                    val size = int32(b, d)
                    val textLen = size - 16
                    if (textLen > 0 && d + 16 + textLen <= b.size) {
                        return String(b, d + 16, textLen, Charsets.UTF_8).takeIf { it.isNotBlank() }
                    }
                }
            }
            i++
        }
        return null
    }

    // ---------- Números ----------

    private fun synchsafe(
        b: ByteArray,
        o: Int,
    ): Int = ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(
        b: ByteArray,
        o: Int,
    ): Int = ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun int32le(
        b: ByteArray,
        o: Int,
    ): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun matchesIgnoreCase(
        b: ByteArray,
        o: Int,
        k: ByteArray,
    ): Boolean {
        for (j in k.indices) {
            val x = (b[o + j].toInt() and 0xFF).toChar().uppercaseChar()
            if (x != k[j].toInt().toChar()) return false
        }
        return true
    }
}
