package com.example.cloudrive.playback

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cloudrive.CloudriveApp
import java.util.concurrent.TimeUnit

/** Background delta-sync of the music library, run on app open, pull-to-refresh, and every 6h. */
class MusicSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Skip quietly if there's no session yet (e.g. this periodic worker's first run landing
        // before login completes) rather than firing an unauthenticated request and retrying.
        if (!CloudriveApp.locator.tokenStore.isLoggedIn()) return Result.success()
        val outcome = CloudriveApp.locator.trackRepository.sync()
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "music_sync_periodic"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MusicSyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
