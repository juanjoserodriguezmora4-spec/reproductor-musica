package com.bismopower.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Una canción encontrada en MediaStore. El id es el mismo de MediaStore. */
@Entity(
    tableName = "tracks",
    indices = [Index("albumId"), Index("artist"), Index("folder")],
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val folder: String,
    val dateAdded: Long,
)

data class AlbumRow(
    val albumId: Long,
    /** Una canción del álbum, para mostrar su carátula. */
    val firstTrackId: Long,
    val album: String,
    val artist: String,
    val count: Int,
    val year: Int,
)

data class ArtistRow(
    val artist: String,
    val firstTrackId: Long,
    val albums: Int,
    val count: Int,
    val albumId: Long,
)

data class FolderRow(
    val folder: String,
    val firstTrackId: Long,
    val count: Int,
    val albumId: Long,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun all(): Flow<List<TrackEntity>>

    @Query(
        "SELECT albumId, MIN(id) AS firstTrackId, album, MAX(artist) AS artist, COUNT(*) AS count, MAX(year) AS year " +
            "FROM tracks GROUP BY albumId ORDER BY album COLLATE NOCASE",
    )
    fun albums(): Flow<List<AlbumRow>>

    @Query(
        "SELECT artist, MIN(id) AS firstTrackId, COUNT(DISTINCT albumId) AS albums, COUNT(*) AS count, MIN(albumId) AS albumId " +
            "FROM tracks GROUP BY artist ORDER BY artist COLLATE NOCASE",
    )
    fun artists(): Flow<List<ArtistRow>>

    @Query(
        "SELECT folder, MIN(id) AS firstTrackId, COUNT(*) AS count, MIN(albumId) AS albumId " +
            "FROM tracks GROUP BY folder ORDER BY folder COLLATE NOCASE",
    )
    fun folders(): Flow<List<FolderRow>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY trackNumber, title COLLATE NOCASE")
    fun byAlbum(albumId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album COLLATE NOCASE, trackNumber, title COLLATE NOCASE")
    fun byArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE")
    fun byFolder(folder: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun clear()

    /** Deja la tabla igual a lo que hay en el teléfono ahora mismo. */
    @Transaction
    suspend fun replaceAll(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) {
            clear()
            return
        }
        // SQLite limita la cantidad de parámetros, por eso se borra en dos pasos.
        val ids = tracks.map { it.id }.toSet()
        val existing = allIds()
        val gone = existing.filter { it !in ids }
        gone.chunked(500).forEach { deleteIds(it) }
        tracks.chunked(500).forEach { upsert(it) }
    }

    @Query("SELECT id FROM tracks")
    suspend fun allIds(): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class BismoDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao

    companion object {
        fun build(context: Context): BismoDatabase =
            Room
                .databaseBuilder(context, BismoDatabase::class.java, "bismo.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
