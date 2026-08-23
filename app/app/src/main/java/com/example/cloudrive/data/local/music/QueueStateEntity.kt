package com.example.cloudrive.data.local.music

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Singleton row (id is always 0) persisting the playback queue across process death. */
@Entity(tableName = "queue_state")
data class QueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val fileIdsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleOn: Boolean,
    val repeatMode: Int,
    val sourceLabel: String?
)
