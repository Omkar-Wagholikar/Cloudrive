package com.example.cloudrive.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.ui.components.EmptyState
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val musicPrefs = remember { CloudriveApp.locator.musicPrefs }
    var wifiOnly by remember { mutableStateOf(musicPrefs.wifiOnlyDownloads) }
    var showRemoveAllConfirm by remember { mutableStateOf(false) }

    if (showRemoveAllConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveAllConfirm = false },
            title = { Text("Remove all downloads?") },
            text = { Text("This deletes all downloaded files from this device. You can download them again anytime.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveAllConfirm = false
                    viewModel.removeAll()
                }) {
                    Text("Remove all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveAllConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.rows.isEmpty() && !uiState.isLoading) {
                Column(Modifier.fillMaxSize()) {
                    WifiOnlyRow(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            musicPrefs.wifiOnlyDownloads = it
                        }
                    )
                    HorizontalDivider()
                    EmptyState(
                        icon = Icons.Default.DownloadDone,
                        title = "No downloads yet",
                        body = "Tracks you download for offline listening will show up here.",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Downloaded",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatBytes(uiState.totalDownloadedBytes),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    item {
                        WifiOnlyRow(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                musicPrefs.wifiOnlyDownloads = it
                            }
                        )
                    }
                    if (uiState.rows.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showRemoveAllConfirm = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(" Remove all", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }
                    items(uiState.rows, key = { it.download.fileId }) { row ->
                        DownloadRowItem(
                            row = row,
                            onCancel = { viewModel.cancel(row.download.fileId) },
                            onRemove = { viewModel.remove(row.download.fileId) },
                            onRetry = { row.track?.let { viewModel.retry(it) } }
                        )
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiOnlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Wi-Fi only", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Only download tracks when connected to Wi-Fi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DownloadRowItem(
    row: DownloadRow,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    val download = row.download
    val name = row.track?.displayTitle ?: download.filePath?.substringAfterLast('/') ?: "File ${download.fileId}"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusLabel(download),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (download.state == DownloadEntity.State.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            when (download.state) {
                DownloadEntity.State.RUNNING, DownloadEntity.State.PENDING, DownloadEntity.State.PAUSED -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel download")
                    }
                }
                DownloadEntity.State.FAILED -> {
                    IconButton(onClick = onRetry, enabled = row.track != null) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry download")
                    }
                }
                else -> {}
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove download")
            }
        }
        if (download.state == DownloadEntity.State.RUNNING) {
            val fraction = if (download.bytesTotal > 0) {
                (download.bytesDone.toFloat() / download.bytesTotal.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}

private fun statusLabel(download: DownloadEntity): String = when (download.state) {
    DownloadEntity.State.DONE -> "Downloaded · ${formatBytes(download.bytesTotal)}"
    DownloadEntity.State.RUNNING -> "Downloading · ${formatBytes(download.bytesDone)} / ${formatBytes(download.bytesTotal)}"
    DownloadEntity.State.PENDING -> "Queued"
    DownloadEntity.State.PAUSED -> "Paused · ${formatBytes(download.bytesDone)} / ${formatBytes(download.bytesTotal)}"
    DownloadEntity.State.FAILED -> download.errorMsg?.let { "Failed · $it" } ?: "Failed"
    else -> download.state
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}
