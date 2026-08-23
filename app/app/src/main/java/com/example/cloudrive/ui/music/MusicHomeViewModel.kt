package com.example.cloudrive.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MusicHomeUiState(
    val recentlyAdded: List<TrackEntity> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val favorites: List<TrackEntity> = emptyList(),
    val totalCount: Int = 0,
    val isLoading: Boolean = true
)

class MusicHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(MusicHomeUiState())
    val uiState: StateFlow<MusicHomeUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                locator.trackRepository.observeRecentlyAdded(),
                locator.trackRepository.observeRecentlyPlayed(),
                locator.trackRepository.observeFavorites(),
                locator.trackRepository.observeCount()
            ) { added, played, favorites, count ->
                MusicHomeUiState(
                    recentlyAdded = added,
                    recentlyPlayed = played,
                    favorites = favorites,
                    totalCount = count,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }
}
