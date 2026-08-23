package com.example.cloudrive.data.local.music

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val fileId: Long,
    val state: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val filePath: String?,
    val errorMsg: String? = null,
    val updatedAt: Long
) {
    object State {
        const val PENDING = "PENDING"
        const val RUNNING = "RUNNING"
        const val PAUSED = "PAUSED"
        const val FAILED = "FAILED"
        const val DONE = "DONE"
    }
}
