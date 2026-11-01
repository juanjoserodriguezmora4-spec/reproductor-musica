package com.bismopower.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * Datos del usuario: listas, favoritas y conteo de reproducciones.
 *
 * Ojo: ninguna de estas tablas tiene llave foránea hacia `tracks`. Esa tabla se vuelve a
 * llenar en cada escaneo (y REPLACE borra y reinserta filas), así que una llave foránea con
 * borrado en cascada se llevaría por delante las listas. Si una canción ya no está en el
 * teléfono, simplemente no aparece (las consultas hacen JOIN con `tracks`); si vuelve, reaparece.
 */

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: Long,
    val addedAt: Long,
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val trackId: Long,
    val plays: Int,
    val lastPlayed: Long,
)

/** Una lista con lo necesario para mostrarla: cuántas canciones tiene y una para la carátula. */
data class PlaylistRow(
    val id: Long,
    val name: String,
    val count: Int,
    val firstTrackId: Long?,
    val firstAlbumId: Long?,
)

/** Una canción con las veces que se ha escuchado. */
data class PlayedTrack(
    @Embedded val track: TrackEntity,
    val plays: Int,
)

@Dao
interface PlaylistDao {
    // ---------- Listas ----------

    @Query(
        "SELECT p.id, p.name, COUNT(t.id) AS count, " +
            "(SELECT pt2.trackId FROM playlist_tracks pt2 JOIN tracks t2 ON t2.id = pt2.trackId " +
            " WHERE pt2.playlistId = p.id ORDER BY pt2.position LIMIT 1) AS firstTrackId, " +
            "(SELECT t3.albumId FROM playlist_tracks pt3 JOIN tracks t3 ON t3.id = pt3.trackId " +
            " WHERE pt3.playlistId = p.id ORDER BY pt3.position LIMIT 1) AS firstAlbumId " +
            "FROM playlists p " +
            "LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id " +
            "LEFT JOIN tracks t ON t.id = pt.trackId " +
            "GROUP BY p.id ORDER BY p.name COLLATE NOCASE",
    )
    fun playlists(): Flow<List<PlaylistRow>>

    @Query(
        "SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position",
    )
    fun tracksOf(playlistId: Long): Flow<List<TrackEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(
        id: Long,
        name: String,
    )

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    /** Las canciones que ya estaban en la lista se ignoran (no se repiten). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRows(rows: List<PlaylistTrackEntity>): List<Long>

    @Transaction
    suspend fun addTracks(
        playlistId: Long,
        trackIds: List<Long>,
    ): Int {
        val start = maxPosition(playlistId) + 1
        val result = insertRows(trackIds.mapIndexed { i, id -> PlaylistTrackEntity(playlistId, id, start + i) })
        return result.count { it != -1L }
    }

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(
        playlistId: Long,
        trackId: Long,
    )

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(
        playlistId: Long,
        trackId: Long,
        position: Int,
    )

    /** Guarda el orden nuevo: [trackIds] es la lista completa ya ordenada. */
    @Transaction
    suspend fun reorder(
        playlistId: Long,
        trackIds: List<Long>,
    ) {
        trackIds.forEachIndexed { i, id -> setPosition(playlistId, id, i) }
    }

    // ---------- Favoritas ----------

    @Query("SELECT trackId FROM favorites")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT t.* FROM favorites f JOIN tracks t ON t.id = f.trackId ORDER BY f.addedAt DESC")
    fun favorites(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    @Query("SELECT COUNT(*) FROM favorites WHERE trackId = :trackId")
    suspend fun isFavorite(trackId: Long): Int

    // ---------- Reproducciones ----------

    // Dos pasos en vez de "INSERT ... ON CONFLICT DO UPDATE": Android 8 trae un SQLite que no lo entiende.
    @Query("INSERT OR IGNORE INTO play_stats (trackId, plays, lastPlayed) VALUES (:trackId, 0, :now)")
    suspend fun ensureStat(
        trackId: Long,
        now: Long,
    )

    @Query("UPDATE play_stats SET plays = plays + 1, lastPlayed = :now WHERE trackId = :trackId")
    suspend fun bumpStat(
        trackId: Long,
        now: Long,
    )

    @Transaction
    suspend fun recordPlay(
        trackId: Long,
        now: Long,
    ) {
        ensureStat(trackId, now)
        bumpStat(trackId, now)
    }

    @Query(
        "SELECT t.*, s.plays AS plays FROM play_stats s JOIN tracks t ON t.id = s.trackId " +
            "WHERE s.plays > 0 ORDER BY s.plays DESC, s.lastPlayed DESC LIMIT :limit",
    )
    fun mostPlayed(limit: Int): Flow<List<PlayedTrack>>

    @Query(
        "SELECT t.*, s.plays AS plays FROM play_stats s JOIN tracks t ON t.id = s.trackId " +
            "WHERE s.plays > 0 ORDER BY s.lastPlayed DESC LIMIT :limit",
    )
    fun recentlyPlayed(limit: Int): Flow<List<PlayedTrack>>
}
