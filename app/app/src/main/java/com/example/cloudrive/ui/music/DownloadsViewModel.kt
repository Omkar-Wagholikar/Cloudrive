package com.example.cloudrive.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A download row paired with whatever track metadata is known for it (may be missing if not synced). */
data class DownloadRow(
    val download: DownloadEntity,
    val track: TrackEntity?
)

data class DownloadsUiState(
    val rows: List<DownloadRow> = emptyList(),
    val totalDownloadedBytes: Long = 0L,
    val isLoading: Boolean = true
)

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                locator.downloadRepository.observeAll(),
                locator.trackRepository.observeAll()
            ) { downloads, tracks ->
                val tracksById = tracks.associateBy { it.fileId }
                val rows = downloads
                    .sortedByDescending { it.updatedAt }
                    .map { DownloadRow(download = it, track = tracksById[it.fileId]) }
                val totalBytes = downloads
                    .filter { it.state == DownloadEntity.State.DONE }
                    .sumOf { it.bytesTotal }
                DownloadsUiState(rows = rows, totalDownloadedBytes = totalBytes, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    fun retry(track: TrackEntity) {
        locator.downloadRepository.enqueue(track)
    }

    fun cancel(fileId: Long) {
        locator.downloadRepository.cancel(fileId)
    }

    fun remove(fileId: Long) {
        viewModelScope.launch {
            locator.downloadRepository.removeDownload(fileId)
        }
    }

    fun removeAll() {
        viewModelScope.launch {
            locator.downloadRepository.removeAll()
        }
    }
}
