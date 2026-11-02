package com.bismopower.app.library

import com.bismopower.app.data.db.TrackEntity
import java.text.Normalizer
import kotlin.math.abs

/** Un grupo de copias de la misma canción: [keep] se queda en la biblioteca, [extras] son las demás. */
data class DuplicateGroup(
    val keep: TrackEntity,
    val extras: List<TrackEntity>,
)

/** Quita tildes, signos y mayúsculas: "Canción!" y "cancion" quedan iguales. */
fun fold(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase()
        .replace(NOT_WORD, " ")
        .trim()
        .replace(SPACES, " ")

private val MARKS = Regex("\\p{Mn}+")
private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")
private val SPACES = Regex("\\s+")

/** Lo que agregan los teléfonos al copiar un archivo: "Canción (1)", "Canción - copia", "Canción copy 2". */
private val COPY_SUFFIX = Regex("""\s*(?:[(\[]\d{1,2}[)\]]|-\s*copia(?:\s*\d+)?|\bcopy(?:\s*\d+)?)\s*$""", RegexOption.IGNORE_CASE)

private fun titleKey(t: TrackEntity): String = fold(t.title.replace(COPY_SUFFIX, "")) + "|" + fold(t.artist)

/** Dos canciones son la misma si tienen el mismo título y artista y duran casi lo mismo (±2,5 s). */
private const val SAME_LENGTH_MS = 2_500L

/**
 * Busca canciones repetidas. De cada grupo se queda el archivo más pesado (normalmente el de
 * mejor calidad) y, si pesan igual, el que llegó primero al teléfono.
 */
fun findDuplicates(
    tracks: List<TrackEntity>,
    sizes: Map<Long, Long>,
): List<DuplicateGroup> {
    val groups = ArrayList<DuplicateGroup>()
    for ((_, same) in tracks.groupBy(::titleKey)) {
        if (same.size < 2) continue
        // Dentro del mismo título, se juntan las que duran casi lo mismo.
        val byLength = same.sortedBy { it.durationMs }
        var cluster = ArrayList<TrackEntity>()
        for (t in byLength) {
            if (cluster.isNotEmpty() && abs(t.durationMs - cluster.last().durationMs) > SAME_LENGTH_MS) {
                addGroup(cluster, sizes, groups)
                cluster = ArrayList()
            }
            cluster += t
        }
        addGroup(cluster, sizes, groups)
    }
    return groups.sortedBy { fold(it.keep.title) }
}

private fun addGroup(
    cluster: List<TrackEntity>,
    sizes: Map<Long, Long>,
    out: MutableList<DuplicateGroup>,
) {
    if (cluster.size < 2) return
    val keep = cluster.sortedWith(compareByDescending<TrackEntity> { sizes[it.id] ?: 0L }.thenBy { it.dateAdded }.thenBy { it.id }).first()
    out += DuplicateGroup(keep, cluster.filter { it.id != keep.id })
}

/** La biblioteca sin las copias (solo queda una de cada canción). */
fun withoutDuplicates(
    tracks: List<TrackEntity>,
    sizes: Map<Long, Long>,
): List<TrackEntity> {
    val extras = findDuplicates(tracks, sizes).flatMap { g -> g.extras.map { it.id } }.toHashSet()
    return if (extras.isEmpty()) tracks else tracks.filter { it.id !in extras }
}
