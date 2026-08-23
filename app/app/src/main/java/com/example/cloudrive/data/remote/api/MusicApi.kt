package com.example.cloudrive.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class TrackDto(
    @SerializedName("id") val id: Long,
    @SerializedName("filename") val filename: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
    @SerializedName("album") val album: String?,
    @SerializedName("album_artist") val albumArtist: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("track_no") val trackNo: Int?,
    @SerializedName("disc_no") val discNo: Int?,
    @SerializedName("year") val year: Int?,
    @SerializedName("duration_ms") val durationMs: Long?,
    @SerializedName("bitrate") val bitrate: Int?,
    @SerializedName("codec") val codec: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("tag_status") val tagStatus: Int,
    @SerializedName("created_at") val createdAt: String
)

data class MusicTracksResponse(
    @SerializedName("page") val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total") val total: Int,
    @SerializedName("tracks") val tracks: List<TrackDto>,
    /** File ids that existed in a previous sync but are no longer present (trashed/deleted). */
    @SerializedName("deleted_ids") val deletedIds: List<Long>? = null
)

data class MusicStatusResponse(
    @SerializedName("total_audio") val totalAudio: Int,
    @SerializedName("indexed") val indexed: Int,
    @SerializedName("pending") val pending: Int,
    @SerializedName("failed") val failed: Int
)

interface MusicApi {
    @GET("/music/tracks")
    suspend fun listTracks(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 200,
        @Query("updated_since") updatedSince: Long? = null
    ): Response<MusicTracksResponse>

    @GET("/music/status")
    suspend fun status(): Response<MusicStatusResponse>
}
