package com.example.cloudrive.playback

import android.content.Intent
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.cloudrive.CloudriveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Requires an AndroidManifest.xml <service> entry for this class with
// android:foregroundServiceType="mediaPlayback", an intent-filter for
// action "androidx.media3.session.MediaSessionService", and the
// POST_NOTIFICATIONS / FOREGROUND_SERVICE_MEDIA_PLAYBACK permissions.
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var retryJob: Job? = null
    private var retryAttempt = 0

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(CloudriveApp.locator.authenticatedClient)
        )
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                60_000,
                1_000,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    retryAttempt = 0
                    retryJob?.cancel()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                scheduleRetry()
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun scheduleRetry() {
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            retryAttempt = 0
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
            return
        }
        val delayMs = 1000L shl retryAttempt
        retryAttempt++
        val resumePositionMs = player.currentPosition
        retryJob?.cancel()
        retryJob = serviceScope.launch {
            delay(delayMs)
            player.prepare()
            player.seekTo(resumePositionMs)
            player.play()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        retryJob?.cancel()
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
