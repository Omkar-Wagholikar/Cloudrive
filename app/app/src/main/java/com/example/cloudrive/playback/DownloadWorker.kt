package com.example.cloudrive.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile

/** Downloads one track to `filesDir/music/{fileId}.{ext}`, resuming a `.part` file via HTTP Range. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val fileId = inputData.getLong(KEY_FILE_ID, -1L)
        if (fileId == -1L) return@withContext Result.failure()

        val locator = CloudriveApp.locator
        val dao = locator.musicDatabase.downloadDao()
        val extension = inputData.getString(KEY_EXTENSION) ?: "audio"

        val dir = File(applicationContext.filesDir, "music").apply { mkdirs() }
        val partFile = File(dir, "$fileId.$extension.part")
        val finalFile = File(dir, "$fileId.$extension")
        val startOffset = if (partFile.exists()) partFile.length() else 0L

        dao.upsert(
            DownloadEntity(
                fileId = fileId,
                state = DownloadEntity.State.RUNNING,
                bytesDone = startOffset,
                bytesTotal = 0L,
                filePath = null,
                updatedAt = System.currentTimeMillis()
            )
        )

        try {
            val requestBuilder = Request.Builder().url(locator.trackRepository.streamUrl(fileId))
            if (startOffset > 0) requestBuilder.header("Range", "bytes=$startOffset-")
            val response = locator.authenticatedClient.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                dao.upsert(
                    DownloadEntity(
                        fileId = fileId,
                        state = DownloadEntity.State.FAILED,
                        bytesDone = startOffset,
                        bytesTotal = 0L,
                        filePath = null,
                        errorMsg = "HTTP ${response.code}",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                return@withContext Result.failure()
            }

            val body = response.body ?: return@withContext Result.failure()
            val total = startOffset + body.contentLength().coerceAtLeast(0)
            val raf = RandomAccessFile(partFile, "rw")
            raf.seek(startOffset)
            var done = startOffset
            var lastReport = 0L
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    done += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 200) {
                        lastReport = now
                        dao.upsert(
                            DownloadEntity(
                                fileId = fileId,
                                state = DownloadEntity.State.RUNNING,
                                bytesDone = done,
                                bytesTotal = total,
                                filePath = null,
                                updatedAt = now
                            )
                        )
                    }
                }
            }
            raf.close()

            partFile.renameTo(finalFile)
            dao.upsert(
                DownloadEntity(
                    fileId = fileId,
                    state = DownloadEntity.State.DONE,
                    bytesDone = done,
                    bytesTotal = total.takeIf { it > 0 } ?: done,
                    filePath = finalFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (e: Exception) {
            dao.upsert(
                DownloadEntity(
                    fileId = fileId,
                    state = DownloadEntity.State.FAILED,
                    bytesDone = partFile.length(),
                    bytesTotal = 0L,
                    filePath = null,
                    errorMsg = e.message,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.failure()
        }
    }

    companion object {
        private const val KEY_FILE_ID = "file_id"
        private const val KEY_EXTENSION = "extension"

        fun uniqueName(fileId: Long) = "download_$fileId"

        fun enqueue(context: Context, fileId: Long, extension: String, wifiOnly: Boolean) {
            val data: Data = workDataOf(KEY_FILE_ID to fileId, KEY_EXTENSION to extension)
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueName(fileId),
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, fileId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(fileId))
        }
    }
}
