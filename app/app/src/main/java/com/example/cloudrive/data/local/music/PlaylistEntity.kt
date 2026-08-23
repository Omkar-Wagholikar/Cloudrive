package com.example.cloudrive.data.local.music

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Display mode transform applied over immutable [PlaylistTrackEntity.position] ordering. */
    val sortMode: String = SortMode.CUSTOM,
    /** Non-null while a session shuffle is active; "Restore custom order" clears it. */
    val shuffledSeed: Long? = null
) {
    object SortMode {
        const val CUSTOM = "custom"
        const val TITLE = "title"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val DURATION = "duration"
        const val DATE_ADDED = "date_added"
    }
}
