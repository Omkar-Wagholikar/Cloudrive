package com.example.cloudrive.data.repository

import android.app.Application
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.data.local.music.MusicDatabase
import com.example.cloudrive.data.local.music.MusicPrefs
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.playback.DownloadWorker
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Façade over [DownloadWorker] (WorkManager) + [com.example.cloudrive.data.local.music.DownloadDao]. */
class DownloadRepository(
    private val app: Application,
    private val db: MusicDatabase,
    private val prefs: MusicPrefs
) {
    private val dao = db.downloadDao()

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    fun observe(fileId: Long): Flow<DownloadEntity?> = dao.observe(fileId)

    fun enqueue(track: TrackEntity) {
        val extension = track.filename.substringAfterLast('.', track.codec ?: "audio")
        DownloadWorker.enqueue(app, track.fileId, extension, prefs.wifiOnlyDownloads)
    }

    fun cancel(fileId: Long) {
        DownloadWorker.cancel(app, fileId)
    }

    suspend fun removeDownload(fileId: Long) {
        val entity = dao.get(fileId)
        entity?.filePath?.let { File(it).delete() }
        dao.delete(fileId)
    }

    suspend fun removeAll() {
        val allStates = listOf(
            DownloadEntity.State.DONE,
            DownloadEntity.State.FAILED,
            DownloadEntity.State.PAUSED,
            DownloadEntity.State.PENDING,
            DownloadEntity.State.RUNNING
        )
        allStates.flatMap { dao.idsWithState(it) }.distinct().forEach { removeDownload(it) }
    }

    suspend fun localFilePath(fileId: Long): String? {
        val entity = dao.get(fileId)
        return entity?.filePath?.takeIf { entity.state == DownloadEntity.State.DONE && File(it).exists() }
    }
}
