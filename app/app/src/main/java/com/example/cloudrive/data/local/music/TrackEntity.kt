package com.example.cloudrive.data.local.music

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val fileId: Long,
    val filename: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val trackNo: Int?,
    val discNo: Int?,
    val year: Int?,
    val durationMs: Long,
    val bitrate: Int?,
    val codec: String?,
    val sizeBytes: Long,
    val hasArt: Boolean,
    val tagStatus: Int,
    val createdAt: Long,
    val favorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: filename.substringBeforeLast('.')

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"

    companion object {
        const val TAG_STATUS_PENDING = 0
        const val TAG_STATUS_OK = 1
        const val TAG_STATUS_NO_TAGS = 2
        const val TAG_STATUS_FAILED = 3
        const val TAG_STATUS_UNSUPPORTED = 4
    }
}
