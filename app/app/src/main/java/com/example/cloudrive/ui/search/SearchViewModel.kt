package com.example.cloudrive.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class SearchUiState(
    val results: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val query: String = ""
)

@OptIn(FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    init {
        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _uiState.value = SearchUiState(query = q)
                    } else {
                        search(q)
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun refresh() {
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            viewModelScope.launch { search(query) }
        }
    }

    private suspend fun search(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val result = locator.fileRepository.searchFiles(query)
        result.onSuccess { fileList ->
            _uiState.value = SearchUiState(
                results = fileList.items,
                isLoading = false,
                query = query
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
        }
    }
}
