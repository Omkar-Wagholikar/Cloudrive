package com.example.cloudrive.data.repository

import com.example.cloudrive.data.local.music.MusicDatabase
import com.example.cloudrive.data.local.music.PlaylistEntity
import com.example.cloudrive.data.local.music.PlaylistTrackEntity
import com.example.cloudrive.data.local.music.PlaylistWithCount
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Playlists are client-local by design (single-client product, no server round-trip).
 * Custom order lives in [PlaylistTrackEntity.position] using gapped longs (1024, 2048, ...);
 * a drag writes one row's position as the midpoint of its new neighbors, and the whole list is
 * only renumbered when a gap collapses below 1 — one UPDATE per drag for large playlists.
 */
class PlaylistRepository(private val db: MusicDatabase) {
    private val dao = db.playlistDao()

    fun observePlaylists(): Flow<List<PlaylistWithCount>> = dao.observePlaylists()
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?> = dao.observePlaylist(playlistId)
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> = dao.observePlaylistTracks(playlistId)

    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertPlaylist(PlaylistEntity(name = name, createdAt = now, updatedAt = now))
    }

    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        dao.upsertPlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun duplicatePlaylist(playlist: PlaylistEntity): Long {
        val newId = createPlaylist("${playlist.name} copy")
        val ordered = dao.observePlaylistTracks(playlist.id).first()
        ordered.forEachIndexed { index, track ->
            dao.insertTracks(listOf(PlaylistTrackEntity(newId, track.fileId, (index + 1) * GAP)))
        }
        return newId
    }

    suspend fun addTracks(playlistId: Long, fileIds: List<Long>) {
        if (fileIds.isEmpty()) return
        val base = dao.maxPosition(playlistId) ?: 0L
        val rows = fileIds.mapIndexed { index, fileId ->
            PlaylistTrackEntity(playlistId, fileId, base + (index + 1) * GAP)
        }
        dao.insertTracks(rows)
        touch(playlistId)
    }

    suspend fun removeTrack(playlistId: Long, fileId: Long) {
        dao.removeTrack(playlistId, fileId)
        touch(playlistId)
    }

    suspend fun containsTrack(playlistId: Long, fileId: Long): Boolean = dao.contains(playlistId, fileId)

    /** Reorders [fileId] within [orderedIds] (the playlist's current track order) to sit at [toIndex]. */
    suspend fun moveTrack(playlistId: Long, orderedIds: List<Long>, fileId: Long, toIndex: Int) {
        val positions = dao.positions(playlistId)
        if (positions.size != orderedIds.size) return
        val fromIndex = orderedIds.indexOf(fileId)
        if (fromIndex == -1) return
        val target = orderedIds.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        val newIndex = target.indexOf(fileId)
        val before = positions.getOrNull(newIndex - 1)
        val after = positions.getOrNull(newIndex)
        val newPosition = when {
            before == null && after != null -> after - GAP
            before != null && after == null -> before + GAP
            before != null && after != null && after - before > 1 -> (before + after) / 2
            else -> {
                renumber(playlistId, target)
                touch(playlistId)
                return
            }
        }
        dao.updatePosition(playlistId, fileId, newPosition)
        touch(playlistId)
    }

    private suspend fun renumber(playlistId: Long, orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, fileId ->
            dao.updatePosition(playlistId, fileId, (index + 1) * GAP)
        }
    }

    private suspend fun touch(playlistId: Long) {
        dao.observePlaylist(playlistId).first()?.let {
            dao.upsertPlaylist(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    companion object {
        private const val GAP = 1024L
    }
}
