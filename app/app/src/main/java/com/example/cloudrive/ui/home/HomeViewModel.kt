package com.example.cloudrive.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.Folder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val folders: List<Folder> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConnectionError: Boolean = false
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Files removed optimistically (e.g. via deleteFile) are cached briefly so that an
    // immediate "Undo" can restore them locally without waiting on a network round-trip.
    private val recentlyDeletedFiles = mutableMapOf<Long, FileItem>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val foldersResult = locator.folderRepository.getRootFolders()
            val filesResult = locator.fileRepository.listFiles()
            val failure = foldersResult.exceptionOrNull() ?: filesResult.exceptionOrNull()
            _uiState.value = HomeUiState(
                folders = foldersResult.getOrDefault(emptyList()),
                files = filesResult.getOrNull()?.items ?: emptyList(),
                isLoading = false,
                error = failure?.message,
                // Best-effort heuristic: java.io.IOException (and its ConnectException /
                // UnknownHostException subtypes) means we couldn't reach the server at
                // all, as opposed to e.g. an HTTP error response from a reachable server.
                isConnectionError = failure is java.io.IOException
            )
        }
    }

    /**
     * Lets a Composable apply an optimistic local state update (e.g. for batch/multi-select
     * actions performed directly against the repository) without waiting on a network
     * round-trip, and roll it back on failure. Does not trigger a network call itself.
     */
    fun applyOptimistic(newState: HomeUiState) {
        _uiState.value = newState
    }

    fun deleteFile(id: Long) {
        val previous = _uiState.value
        previous.files.find { it.id == id }?.let { recentlyDeletedFiles[id] = it }
        _uiState.value = previous.copy(files = previous.files.filterNot { it.id == id })
        viewModelScope.launch {
            val result = locator.fileRepository.deleteFile(id)
            if (result.isFailure) {
                recentlyDeletedFiles.remove(id)
                _uiState.value = previous
            }
        }
    }

    fun restoreFile(id: Long) {
        val previous = _uiState.value
        val cached = recentlyDeletedFiles.remove(id)
        if (cached != null) {
            _uiState.value = previous.copy(files = previous.files + cached)
        }
        viewModelScope.launch {
            val result = locator.trashRepository.restore(id)
            if (result.isFailure && cached != null) {
                _uiState.value = _uiState.value.copy(files = _uiState.value.files.filterNot { it.id == id })
            }
        }
    }

    fun moveFile(id: Long, folderId: Long?) {
        val previous = _uiState.value
        // Home only shows root-level files, so moving into any folder removes it from view.
        if (folderId != null) {
            _uiState.value = previous.copy(files = previous.files.filterNot { it.id == id })
        }
        viewModelScope.launch {
            val result = locator.fileRepository.moveFile(id, folderId)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }

    fun renameFile(id: Long, newName: String) {
        val previous = _uiState.value
        _uiState.value = previous.copy(
            files = previous.files.map { if (it.id == id) it.copy(filename = newName) else it }
        )
        viewModelScope.launch {
            val result = locator.fileRepository.renameFile(id, newName)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }

    fun deleteFolder(id: Long) {
        val previous = _uiState.value
        _uiState.value = previous.copy(folders = previous.folders.filterNot { it.id == id })
        viewModelScope.launch {
            val result = locator.folderRepository.deleteFolder(id)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }
}
