package com.example.cloudrive.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Returns a function that saves a [FileItem] to shared device storage (Gallery-visible for
 * images/videos) and reports progress/result via [snackbarHostState]. On API 28 and below this
 * requests WRITE_EXTERNAL_STORAGE first, since MediaStore writes need it pre-Android 10.
 */
@Composable
fun rememberSaveToDeviceAction(
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
): (FileItem) -> Unit {
    val context = LocalContext.current
    val repo = CloudriveApp.locator.saveToDeviceRepository
    var pendingFile by remember { mutableStateOf<FileItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val file = pendingFile
        pendingFile = null
        if (file == null) return@rememberLauncherForActivityResult
        if (granted) {
            scope.launch { performSave(context, repo, file, snackbarHostState) }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Storage permission is needed to save files") }
        }
    }

    return remember(context) {
        { file: FileItem ->
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingFile = file
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                scope.launch { performSave(context, repo, file, snackbarHostState) }
            }
        }
    }
}

private suspend fun performSave(
    context: android.content.Context,
    repo: com.example.cloudrive.data.repository.SaveToDeviceRepository,
    file: FileItem,
    snackbarHostState: SnackbarHostState
) {
    repo.saveToDevice(context, file)
        .onSuccess { snackbarHostState.showSnackbar("Saved \"${file.filename}\" to device") }
        .onFailure { snackbarHostState.showSnackbar("Couldn't save \"${file.filename}\": ${it.message ?: "unknown error"}") }
}
