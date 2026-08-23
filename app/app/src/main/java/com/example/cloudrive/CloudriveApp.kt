package com.example.cloudrive

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.example.cloudrive.di.ServiceLocator
import com.example.cloudrive.playback.MusicSyncWorker

class CloudriveApp : Application(), SingletonImageLoader.Factory {
    companion object {
        lateinit var locator: ServiceLocator
            private set
    }

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)
        MusicSyncWorker.schedulePeriodic(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = locator.imageLoader
}
