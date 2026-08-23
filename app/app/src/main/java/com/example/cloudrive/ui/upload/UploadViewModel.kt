package com.example.cloudrive.ui.upload

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.UploadResponse
import com.example.cloudrive.data.model.UploadSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UploadUiState {
    object Idle : UploadUiState()
    data class Uploading(val progress: Float) : UploadUiState()
    data class Success(val file: UploadResponse) : UploadUiState()
    data class Error(val message: String) : UploadUiState()
    data class QuotaExceeded(
        val usedBytes: Long,
        val quotaBytes: Long,
        val neededBytes: Long
    ) : UploadUiState()
}

class UploadViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState

    private val _openSessions = MutableStateFlow<List<UploadSession>>(emptyList())
    val openSessions: StateFlow<List<UploadSession>> = _openSessions

    fun checkOpenSessions() {
        viewModelScope.launch {
            locator.uploadRepository.listOpenSessions().onSuccess {
                _openSessions.value = it
            }
        }
    }

    fun dismissSession(uploadId: String) {
        _openSessions.value = _openSessions.value.filterNot { it.uploadId == uploadId }
    }

    fun resumeUpload(uri: Uri, session: UploadSession) {
        viewModelScope.launch {
            val name = locator.uploadRepository.fileName(uri)
            if (session.filename != null && name != session.filename) {
                _uiState.value = UploadUiState.Error(
                    "Selected file \"$name\" doesn't match the interrupted upload \"${session.filename}\""
                )
                return@launch
            }
            _uiState.value = UploadUiState.Uploading(session.offset.toFloat() / session.totalSize.toFloat())
            val result = locator.uploadRepository.resumeSession(uri, session) { progress ->
                _uiState.value = UploadUiState.Uploading(progress)
            }
            result.onSuccess {
                dismissSession(session.uploadId)
                _uiState.value = UploadUiState.Success(it)
            }.onFailure {
                _uiState.value = UploadUiState.Error(it.message ?: "Resume failed")
            }
        }
    }

    fun uploadFile(uri: Uri, folderId: Long? = null) {
        viewModelScope.launch {
            val size = locator.uploadRepository.fileSize(uri)
            val user = locator.authRepository.getMe().getOrNull()
            if (user != null && user.quotaBytes > 0 && user.usedBytes + size > user.quotaBytes) {
                _uiState.value = UploadUiState.QuotaExceeded(
                    usedBytes = user.usedBytes,
                    quotaBytes = user.quotaBytes,
                    neededBytes = size
                )
                return@launch
            }

            _uiState.value = UploadUiState.Uploading(0f)
            val result = locator.uploadRepository.uploadFile(uri, folderId) { progress ->
                _uiState.value = UploadUiState.Uploading(progress)
            }
            result.onSuccess {
                _uiState.value = UploadUiState.Success(it)
            }.onFailure {
                _uiState.value = UploadUiState.Error(it.message ?: "Upload failed")
            }
        }
    }

    fun reset() {
        _uiState.value = UploadUiState.Idle
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
