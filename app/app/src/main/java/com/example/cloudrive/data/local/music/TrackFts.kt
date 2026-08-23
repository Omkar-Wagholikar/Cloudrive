package com.example.cloudrive.data.local.music

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 shadow table over [TrackEntity], used for fast substring/prefix search across
 * title/artist/album/filename. Room keeps this in sync with `tracks` automatically because it's
 * declared as an external-content FTS table (`contentEntity = TrackEntity::class`): writes to
 * `tracks` via [TrackDao.upsertAll]/[TrackDao.deleteByIds] are mirrored into `track_fts` by
 * SQLite triggers Room generates, no manual sync needed.
 */
@Entity(tableName = "track_fts")
@Fts4(contentEntity = TrackEntity::class)
data class TrackFts(
    val title: String?,
    val artist: String?,
    val album: String?,
    val filename: String
)
