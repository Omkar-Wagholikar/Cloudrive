package com.example.cloudrive.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.PlaylistWithCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<PlaylistWithCount> = emptyList(),
    val isLoading: Boolean = true
)

/** Backs [PlaylistsScreen] — the list of all playlists plus create/navigate actions. */
class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState

    init {
        viewModelScope.launch {
            locator.playlistRepository.observePlaylists().collect { playlists ->
                _uiState.value = PlaylistsUiState(playlists = playlists, isLoading = false)
            }
        }
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = locator.playlistRepository.createPlaylist(name)
            onCreated(id)
        }
    }
}
