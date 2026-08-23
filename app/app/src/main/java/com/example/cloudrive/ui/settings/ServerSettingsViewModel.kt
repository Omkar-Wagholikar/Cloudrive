package com.example.cloudrive.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ServerSettingsUiState(
    val serverUrl: String = "",
    val isOnLan: Boolean = false,
    val currentBaseUrl: String = "",
    val isChecking: Boolean = false,
    val isSaving: Boolean = false,
    val connected: Boolean? = null,
    val error: String? = null
)

class ServerSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(ServerSettingsUiState(serverUrl = locator.tokenStore.serverUrl))
    val uiState: StateFlow<ServerSettingsUiState> = _uiState

    init {
        checkConnection()
    }

    fun updateUrlDraft(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun checkConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, error = null)
            locator.lanResolver.refresh()
            val result = locator.authRepository.getMe()
            _uiState.value = _uiState.value.copy(
                isChecking = false,
                isOnLan = locator.lanResolver.isOnLan(),
                currentBaseUrl = locator.lanResolver.currentBaseUrl(),
                connected = result.isSuccess,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val trimmed = _uiState.value.serverUrl.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Server URL can't be empty")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            locator.tokenStore.serverUrl = trimmed
            locator.rebuildClient()
            locator.lanResolver.invalidate()
            locator.lanResolver.refresh()
            val result = locator.authRepository.getMe()
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                serverUrl = locator.tokenStore.serverUrl,
                isOnLan = locator.lanResolver.isOnLan(),
                currentBaseUrl = locator.lanResolver.currentBaseUrl(),
                connected = result.isSuccess,
                error = if (result.isFailure) {
                    "Saved, but couldn't reach the new server: ${result.exceptionOrNull()?.message}"
                } else {
                    null
                }
            )
            if (result.isSuccess) onSaved()
        }
    }
}
