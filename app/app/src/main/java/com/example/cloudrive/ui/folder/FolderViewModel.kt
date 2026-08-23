package com.example.cloudrive.ui.folder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.BreadcrumbItem
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.Folder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FolderUiState(
    val folder: Folder? = null,
    val breadcrumb: List<BreadcrumbItem> = emptyList(),
    val subfolders: List<Folder> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FolderViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator
    val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState

    // Files removed optimistically (e.g. via deleteFile) are cached briefly so that an
    // immediate "Undo" can restore them locally without waiting on a network round-trip.
    private val recentlyDeletedFiles = mutableMapOf<Long, FileItem>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = locator.folderRepository.getFolderContents(folderId)
            result.onSuccess { contents ->
                _uiState.value = FolderUiState(
                    folder = contents.folder,
                    breadcrumb = contents.breadcrumb,
                    subfolders = contents.subfolders,
                    files = contents.files.items,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun createFolder(name: String) {
        // New folders don't exist locally until the server assigns an id, so a silent
        // background refresh is the simplest correct approach here.
        viewModelScope.launch {
            locator.folderRepository.createFolder(name, folderId)
            load()
        }
    }

    fun deleteFolder(id: Long) {
        val previous = _uiState.value
        _uiState.value = previous.copy(subfolders = previous.subfolders.filterNot { it.id == id })
        viewModelScope.launch {
            val result = locator.folderRepository.deleteFolder(id)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
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
        // A file moved to any other folder (including back to this same folder id is a
        // no-op removal-then-reappear edge case we accept) leaves this folder's view.
        if (folderId != this.folderId) {
            _uiState.value = previous.copy(files = previous.files.filterNot { it.id == id })
        }
        viewModelScope.launch {
            val result = locator.fileRepository.moveFile(id, folderId)
            if (result.isFailure) {
                _uiState.value = previous
            }
        }
    }
}
