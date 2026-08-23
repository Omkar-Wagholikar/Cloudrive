package com.example.cloudrive.data.local.music

import androidx.room.Entity

/** Custom order lives in [position] (gapped: 1024, 2048, ...) and is never overwritten by shuffle/sort. */
@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "fileId"])
data class PlaylistTrackEntity(
    val playlistId: Long,
    val fileId: Long,
    val position: Long
)
