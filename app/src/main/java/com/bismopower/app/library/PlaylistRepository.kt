package com.bismopower.app.library

import com.bismopower.app.data.db.BismoDatabase
import com.bismopower.app.data.db.FavoriteEntity
import com.bismopower.app.data.db.PlayedTrack
import com.bismopower.app.data.db.PlaylistEntity
import com.bismopower.app.data.db.PlaylistRow
import com.bismopower.app.data.db.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Listas de reproducción, favoritas y lo más escuchado. */
class PlaylistRepository(
    db: BismoDatabase,
) {
    private val dao = db.playlists()

    val playlists: Flow<List<PlaylistRow>> = dao.playlists()

    fun tracks(playlistId: Long): Flow<List<TrackEntity>> = dao.tracksOf(playlistId)

    /** Crea una lista y devuelve su id. */
    suspend fun create(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(
        id: Long,
        name: String,
    ) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    /** Añade canciones al final. Devuelve cuántas eran nuevas (las repetidas no se duplican). */
    suspend fun add(
        playlistId: Long,
        trackIds: List<Long>,
    ): Int = dao.addTracks(playlistId, trackIds.distinct())

    suspend fun remove(
        playlistId: Long,
        trackId: Long,
    ) = dao.removeTrack(playlistId, trackId)

    suspend fun reorder(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    ) = dao.reorder(playlistId, orderedTrackIds)

    // ---------- Favoritas ----------

    val favoriteIds: Flow<Set<Long>> = dao.favoriteIds().map { it.toSet() }
    val favorites: Flow<List<TrackEntity>> = dao.favorites()

    /** Marca o desmarca la canción. Devuelve true si quedó como favorita. */
    suspend fun toggleFavorite(trackId: Long): Boolean =
        if (dao.isFavorite(trackId) > 0) {
            dao.removeFavorite(trackId)
            false
        } else {
            dao.addFavorite(FavoriteEntity(trackId, System.currentTimeMillis()))
            true
        }

    // ---------- Reproducciones ----------

    val mostPlayed: Flow<List<PlayedTrack>> = dao.mostPlayed(100)
    val recentlyPlayed: Flow<List<PlayedTrack>> = dao.recentlyPlayed(100)

    suspend fun recordPlay(trackId: Long) = dao.recordPlay(trackId, System.currentTimeMillis())
}
