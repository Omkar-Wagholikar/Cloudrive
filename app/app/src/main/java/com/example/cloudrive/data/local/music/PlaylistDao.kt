package com.example.cloudrive.data.local.music

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortMode: String,
    val shuffledSeed: Long?,
    val trackCount: Int
)

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount " +
            "FROM playlists p ORDER BY p.updatedAt DESC"
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.fileId = t.fileId " +
            "WHERE pt.playlistId = :playlistId ORDER BY pt.position"
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT MAX(position) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Long?

    @Query("SELECT position FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun positions(playlistId: Long): List<Long>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND fileId = :fileId")
    suspend fun removeTrack(playlistId: Long, fileId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND fileId = :fileId")
    suspend fun updatePosition(playlistId: Long, fileId: Long, position: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND fileId = :fileId)")
    suspend fun contains(playlistId: Long, fileId: Long): Boolean
}
