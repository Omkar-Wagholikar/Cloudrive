package com.example.cloudrive.data.repository

import com.example.cloudrive.data.local.music.MusicDatabase
import com.example.cloudrive.data.local.music.MusicPrefs
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.data.remote.LanResolver
import com.example.cloudrive.data.remote.api.MusicApi
import com.example.cloudrive.data.remote.api.MusicStatusResponse
import com.example.cloudrive.data.remote.api.TrackDto
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Room is the app-side source of truth for the music library: screens observe [TrackDao] Flows,
 * never the network response directly. [sync] reconciles server state into Room.
 */
class TrackRepository(
    private val api: MusicApi,
    private val db: MusicDatabase,
    private val prefs: MusicPrefs,
    private val fileRepository: FileRepository,
    private val lanResolver: LanResolver
) {
    private val gson = Gson()
    private val dao = db.trackDao()

    fun observeAll(): Flow<List<TrackEntity>> = dao.observeAll()
    fun observeByAlbum(album: String): Flow<List<TrackEntity>> = dao.observeByAlbum(album)
    fun observeByArtist(artist: String): Flow<List<TrackEntity>> = dao.observeByArtist(artist)
    fun observeByGenre(genre: String): Flow<List<TrackEntity>> = dao.observeByGenre(genre)
    fun observeAlbums(): Flow<List<String>> = dao.observeAlbums()
    fun observeArtists(): Flow<List<String>> = dao.observeArtists()
    fun observeGenres(): Flow<List<String>> = dao.observeGenres()
    fun search(query: String): Flow<List<TrackEntity>> = dao.search(query.toFtsMatchQuery())
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<TrackEntity>> = dao.recentlyPlayed(limit)
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<TrackEntity>> = dao.recentlyAdded(limit)
    fun observeFavorites(): Flow<List<TrackEntity>> = dao.observeFavorites()
    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun setFavorite(fileId: Long, favorite: Boolean) = dao.setFavorite(fileId, favorite)
    suspend fun recordPlay(fileId: Long) = dao.recordPlay(fileId, System.currentTimeMillis())

    /** Delta-syncs the local mirror against `/music/tracks`, deleting rows the server reports gone. */
    suspend fun sync(): Result<Unit> = runCatching {
        // A stale cursor pointing past an empty table (e.g. after a destructive Room migration
        // reset the data but not this SharedPreferences value) would make every delta sync ask
        // "what changed since X" and correctly get nothing back, leaving the library empty
        // forever. If Room has no rows, always do a full sync regardless of the stored cursor.
        val hasLocalTracks = dao.observeCount().first() > 0
        var page = 1
        val allDeleted = mutableSetOf<Long>()
        while (true) {
            val r = api.listTracks(page = page, updatedSince = prefs.lastSyncAt.takeIf { it > 0 && hasLocalTracks })
            val body = r.body() ?: error(parseError(r.errorBody()?.string()))
            dao.upsertAll(body.tracks.map { it.toEntity() })
            body.deletedIds?.let { allDeleted += it }
            if (body.tracks.size < PAGE_SIZE || body.tracks.isEmpty()) break
            page++
        }
        if (allDeleted.isNotEmpty()) dao.deleteByIds(allDeleted.toList())
        prefs.lastSyncAt = System.currentTimeMillis()
    }

    suspend fun status(): Result<MusicStatusResponse> = runCatching {
        val r = api.status()
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    fun streamUrl(fileId: Long): String = fileRepository.downloadUrl(fileId)

    fun artworkUrl(fileId: Long, size: Int = 88): String =
        "${lanResolver.currentBaseUrl()}/music/tracks/$fileId/artwork?size=$size"

    private fun TrackDto.toEntity() = TrackEntity(
        fileId = id,
        filename = filename,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        genre = genre,
        trackNo = trackNo,
        discNo = discNo,
        year = year,
        durationMs = durationMs ?: 0L,
        bitrate = bitrate,
        codec = codec,
        sizeBytes = size,
        hasArt = artworkUrl != null,
        tagStatus = tagStatus,
        createdAt = runCatching { java.time.Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
    )

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            val map = gson.fromJson(body, Map::class.java)
            map["error"] as? String ?: body
        } catch (e: Exception) {
            body
        }
    }

    companion object {
        private const val PAGE_SIZE = 200
    }
}

/**
 * Turns free-text user input into a safe FTS4 MATCH query: each whitespace-separated token is
 * stripped of characters that have special meaning to the FTS4 query syntax (`"`, `-`, `(`, `)`,
 * `*`, `:`) and suffixed with `*` for prefix matching, so `"foo bar"` becomes `foo* bar*`
 * (implicit AND across tokens, OR across the shadowed title/artist/album/filename columns since
 * no column is specified). Falls back to a phrase that can never match if the sanitized query is
 * empty, so blank/punctuation-only input just yields no results instead of an FTS syntax error.
 */
private val FTS_SPECIAL_CHARS = Regex("[\"\\-()*:^]")

fun String.toFtsMatchQuery(): String {
    val tokens = trim().split(Regex("\\s+"))
        .map { it.replace(FTS_SPECIAL_CHARS, "").trim() }
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return "\"\""
    return tokens.joinToString(" ") { "$it*" }
}
