package com.example.cloudrive.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState

    private val _navigateToAuth = MutableSharedFlow<Unit>()
    val navigateToAuth: SharedFlow<Unit> = _navigateToAuth

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = locator.authRepository.getMe()
            result.onSuccess {
                _uiState.value = ProfileUiState(user = it, isLoading = false)
            }.onFailure {
                _uiState.value = ProfileUiState(isLoading = false, error = it.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            locator.authRepository.logout()
            _navigateToAuth.emit(Unit)
        }
    }

    val serverUrl get() = locator.tokenStore.serverUrl
}
