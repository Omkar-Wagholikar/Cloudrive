package com.example.cloudrive.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrashUiState(
    val items: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class TrashViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = locator.trashRepository.listTrash()
            result.onSuccess {
                _uiState.value = TrashUiState(items = it.items, isLoading = false)
            }.onFailure {
                _uiState.value = TrashUiState(isLoading = false, error = it.message)
            }
        }
    }

    /**
     * Lets a Composable apply an optimistic local state update (e.g. for batch/multi-select
     * actions performed directly against the repository) without waiting on a network
     * round-trip, and roll it back on failure. Does not trigger a network call itself.
     */
    fun applyOptimistic(newState: TrashUiState) {
        _uiState.value = newState
    }

    fun restore(id: Long) {
        val previous = _uiState.value
        _uiState.value = previous.copy(items = previous.items.filterNot { it.id == id })
        viewModelScope.launch {
            val result = locator.trashRepository.restore(id)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }

    fun deletePermanently(id: Long) {
        val previous = _uiState.value
        _uiState.value = previous.copy(items = previous.items.filterNot { it.id == id })
        viewModelScope.launch {
            val result = locator.trashRepository.deletePermanently(id)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }

    fun purgeAll(onResult: (Result<Int>) -> Unit) {
        val previous = _uiState.value
        _uiState.value = previous.copy(items = emptyList())
        viewModelScope.launch {
            locator.trashRepository.purgeAll()
                .onSuccess {
                    // Server-reported count may differ (e.g. concurrent trash additions); trust it.
                    onResult(Result.success(it.purged))
                }
                .onFailure {
                    _uiState.value = previous
                    onResult(Result.failure(it))
                }
        }
    }
}
