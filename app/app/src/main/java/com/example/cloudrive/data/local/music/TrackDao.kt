package com.example.cloudrive.data.local.music

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNo, trackNo")
    fun observeByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album COLLATE NOCASE, trackNo")
    fun observeByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY title COLLATE NOCASE")
    fun observeByGenre(genre: String): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT album FROM tracks WHERE album IS NOT NULL AND album != '' ORDER BY album COLLATE NOCASE")
    fun observeAlbums(): Flow<List<String>>

    @Query("SELECT DISTINCT artist FROM tracks WHERE artist IS NOT NULL AND artist != '' ORDER BY artist COLLATE NOCASE")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<String>>

    /**
     * FTS4-backed search. [q] must already be a sanitized MATCH query (see
     * [com.example.cloudrive.data.repository.TrackRepository.search]), not a raw LIKE pattern.
     */
    @Query(
        "SELECT tracks.* FROM tracks JOIN track_fts ON tracks.fileId = track_fts.rowid " +
            "WHERE track_fts MATCH :q ORDER BY tracks.title COLLATE NOCASE"
    )
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY createdAt DESC LIMIT :limit")
    fun recentlyAdded(limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE favorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE fileId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT fileId FROM tracks")
    suspend fun allIds(): List<Long>

    @Query("UPDATE tracks SET favorite = :favorite WHERE fileId = :fileId")
    suspend fun setFavorite(fileId: Long, favorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedAt = :ts, playCount = playCount + 1 WHERE fileId = :fileId")
    suspend fun recordPlay(fileId: Long, ts: Long)
}
