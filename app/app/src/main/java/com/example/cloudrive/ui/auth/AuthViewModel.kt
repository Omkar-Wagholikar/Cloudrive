package com.example.cloudrive.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    private val _navigateToHome = MutableSharedFlow<Unit>()
    val navigateToHome: SharedFlow<Unit> = _navigateToHome

    val serverUrl get() = locator.tokenStore.serverUrl

    fun login(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            if (!updateServerUrlIfChanged(serverUrl)) return@launch
            val result = locator.authRepository.login(username.trim(), password)
            result.onSuccess {
                _uiState.value = AuthUiState.Success
                _navigateToHome.emit(Unit)
            }.onFailure {
                _uiState.value = AuthUiState.Error(it.message ?: "Login failed")
            }
        }
    }

    fun register(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            if (!updateServerUrlIfChanged(serverUrl)) return@launch
            val result = locator.authRepository.register(username.trim(), password)
            result.onSuccess {
                // Auto-login after register
                val loginResult = locator.authRepository.login(username.trim(), password)
                loginResult.onSuccess {
                    _uiState.value = AuthUiState.Success
                    _navigateToHome.emit(Unit)
                }.onFailure {
                    _uiState.value = AuthUiState.Error("Registered! Please log in.")
                }
            }.onFailure {
                _uiState.value = AuthUiState.Error(it.message ?: "Registration failed")
            }
        }
    }

    fun clearError() {
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    /** Returns false (and sets an Error state) if the URL is invalid, so callers can bail out. */
    private fun updateServerUrlIfChanged(newUrl: String): Boolean {
        val trimmed = newUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            _uiState.value = AuthUiState.Error("Enter a server URL")
            return false
        }
        if (trimmed == locator.tokenStore.serverUrl) return true
        return try {
            locator.tokenStore.serverUrl = trimmed
            locator.rebuildClient()
            true
        } catch (e: IllegalArgumentException) {
            _uiState.value = AuthUiState.Error("Invalid server URL. Include http:// or https://")
            false
        }
    }
}
