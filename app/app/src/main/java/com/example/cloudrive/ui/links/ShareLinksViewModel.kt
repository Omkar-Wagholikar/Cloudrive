package com.example.cloudrive.ui.links

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.ShareLinkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ShareLinksUiState(
    val links: List<ShareLinkItem> = emptyList(),
    val isLoading: Boolean = false,
    val revokingToken: String? = null,
    val error: String? = null
)

class ShareLinksViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(ShareLinksUiState())
    val uiState: StateFlow<ShareLinksUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            locator.fileRepository.listShares()
                .onSuccess { links -> _uiState.value = ShareLinksUiState(links = links) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun revoke(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(revokingToken = token, error = null)
            locator.fileRepository.revokeShare(token)
                .onSuccess { load() }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
            _uiState.value = _uiState.value.copy(revokingToken = null)
        }
    }
}
