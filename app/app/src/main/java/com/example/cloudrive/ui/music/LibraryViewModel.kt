package com.example.cloudrive.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val isLoading: Boolean = true
)

/** Backs [SongsScreen]: the flat alphabetical list of every synced track. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            locator.trackRepository.observeAll().collect { tracks ->
                _uiState.value = LibraryUiState(tracks = tracks, isLoading = false)
            }
        }
    }
}
