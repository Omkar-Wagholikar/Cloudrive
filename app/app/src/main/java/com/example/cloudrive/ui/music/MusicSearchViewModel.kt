package com.example.cloudrive.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class MusicSearchUiState(
    val query: String = "",
    val results: List<TrackEntity> = emptyList(),
    val isSearching: Boolean = false
)

/**
 * Search is local-only (Room query, no network) — mirrors [com.example.cloudrive.ui.search.SearchViewModel]'s
 * 300ms debounce pattern but backs it with [com.example.cloudrive.data.repository.TrackRepository.search].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MusicSearchViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(MusicSearchUiState())
    val uiState: StateFlow<MusicSearchUiState> = _uiState

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList()) else locator.trackRepository.search(q)
                }
                .collect { results ->
                    _uiState.value = _uiState.value.copy(results = results, isSearching = false)
                }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _uiState.value = _uiState.value.copy(query = query, isSearching = query.isNotBlank())
    }
}
