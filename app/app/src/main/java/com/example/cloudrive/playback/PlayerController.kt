package com.example.cloudrive.playback

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.data.repository.DownloadRepository
import com.example.cloudrive.data.repository.TrackRepository
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUiState(
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val shuffleOn: Boolean = false,
    val repeatMode: Int = 0, // matches androidx.media3.common.Player.REPEAT_MODE_OFF/ONE/ALL
    val queue: List<TrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val playbackSpeed: Float = 1f,
    val isBuffering: Boolean = false,
    val isOffline: Boolean = false,
    val error: String? = null
)

class PlayerController(
    private val app: Application,
    private val trackRepository: TrackRepository,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor
) {
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    // MediaController must only be touched from the app's main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var controller: MediaController? = null
    private var queue: List<TrackEntity> = emptyList()
    private var positionPollingStarted = false
    private val queueManager = QueueManager()

    init {
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        future.addListener(
            {
                controller = future.get()
                attachListener()
                startPositionPolling()
            },
            MoreExecutors.directExecutor()
        )
        scope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    private suspend fun mediaItemFor(track: TrackEntity): MediaItem {
        val localPath = downloadRepository.localFilePath(track.fileId)
        val uri = if (localPath != null) Uri.fromFile(File(localPath)) else Uri.parse(trackRepository.streamUrl(track.fileId))
        return MediaItem.Builder().setUri(uri).setMediaId(track.fileId.toString()).build()
    }

    private fun attachListener() {
        val controller = controller ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying, error = if (isPlaying) null else _uiState.value.error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.value = _uiState.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val fileId = mediaItem?.mediaId?.toLongOrNull()
                val track = fileId?.let { id -> queue.firstOrNull { it.fileId == id } }
                val index = fileId?.let { id -> queue.indexOfFirst { it.fileId == id } } ?: -1
                _uiState.value = _uiState.value.copy(
                    currentTrack = track,
                    currentIndex = index,
                    durationMs = track?.durationMs ?: 0L
                )
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffleOn = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        })
    }

    private fun startPositionPolling() {
        if (positionPollingStarted) return
        positionPollingStarted = true
        scope.launch {
            while (true) {
                delay(500)
                val controller = controller
                if (controller != null) {
                    _uiState.value = _uiState.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0),
                        bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0),
                        durationMs = controller.duration.takeIf { it > 0 } ?: _uiState.value.durationMs
                    )
                }
            }
        }
    }

    fun playQueue(tracks: List<TrackEntity>, startIndex: Int, sourceLabel: String? = null) {
        val controller = controller ?: return
        queue = tracks
        _uiState.value = _uiState.value.copy(queue = tracks, error = null)
        scope.launch {
            val items = tracks.map { mediaItemFor(it) }
            controller.setMediaItems(items, startIndex, 0L)
            controller.prepare()
            controller.play()
            tracks.getOrNull(startIndex)?.let { track ->
                ioScope.launch { trackRepository.recordPlay(track.fileId) }
            }
        }
    }

    /** Inserts [track] to play right after the current item. */
    fun addNext(track: TrackEntity) {
        val controller = controller ?: return
        val currentIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
        val insertAt = (currentIndex + 1).coerceIn(0, queue.size)
        queue = queueManager.addNext(queue, currentIndex, track)
        _uiState.value = _uiState.value.copy(queue = queue)
        scope.launch {
            val item = mediaItemFor(track)
            controller.addMediaItem(insertAt.coerceIn(0, controller.mediaItemCount), item)
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = controller ?: return
        if (index !in queue.indices) return
        queue = queueManager.remove(queue, index)
        _uiState.value = _uiState.value.copy(queue = queue)
        controller.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val controller = controller ?: return
        if (from !in queue.indices || to !in queue.indices) return
        queue = queueManager.move(queue, from, to)
        _uiState.value = _uiState.value.copy(queue = queue)
        controller.moveMediaItem(from, to)
    }

    fun playPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun setShuffle(on: Boolean) {
        controller?.shuffleModeEnabled = on
    }

    fun cycleRepeatMode() {
        val controller = controller ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackParameters(PlaybackParameters(speed))
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }
}
