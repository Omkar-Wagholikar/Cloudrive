package com.example.cloudrive.ui.trash

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.ViewMode
import com.example.cloudrive.data.local.ViewModePrefs
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileGridSkeleton
import com.example.cloudrive.ui.components.FileListSkeleton
import com.example.cloudrive.ui.components.SelectionActionBar
import com.example.cloudrive.ui.components.ViewModeToggle
import kotlinx.coroutines.launch

@Composable
fun TrashTab(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showPurgeConfirm by remember { mutableStateOf(false) }
    val viewModePrefs = remember { CloudriveApp.locator.viewModePrefs }
    var viewMode by remember { mutableStateOf(viewModePrefs.get(ViewModePrefs.TRASH)) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val fileRepo = CloudriveApp.locator.fileRepository

    fun onToggleSelect(fileId: Long) {
        selectedIds = if (fileId in selectedIds) selectedIds - fileId else selectedIds + fileId
    }
    fun onBatchRestore() {
        val ids = selectedIds.toList()
        val previous = uiState
        viewModel.applyOptimistic(uiState.copy(items = uiState.items.filterNot { it.id in ids }))
        selectedIds = emptySet()
        scope.launch {
            val result = fileRepo.batchRestore(ids)
            if (result.isFailure) {
                viewModel.applyOptimistic(previous)
            }
            snackbarHostState.showSnackbar("Restored ${ids.size} item(s)")
        }
    }
    fun onBatchDeleteForever() {
        val ids = selectedIds.toList()
        val previous = uiState
        viewModel.applyOptimistic(uiState.copy(items = uiState.items.filterNot { it.id in ids }))
        selectedIds = emptySet()
        scope.launch {
            val result = fileRepo.batchDelete(ids)
            if (result.isFailure) {
                viewModel.applyOptimistic(previous)
            }
            snackbarHostState.showSnackbar("Permanently deleted ${ids.size} item(s)")
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("Empty trash?") },
            text = { Text("This permanently deletes all ${uiState.items.size} item(s) in trash. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showPurgeConfirm = false
                    viewModel.purgeAll { result ->
                        scope.launch {
                            result.onSuccess { purged ->
                                snackbarHostState.showSnackbar("Permanently deleted $purged item(s)")
                            }.onFailure {
                                snackbarHostState.showSnackbar("Failed to empty trash: ${it.message}")
                            }
                        }
                    }
                }) {
                    Text("Empty trash", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Box(modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> if (viewMode == ViewMode.GRID) {
                FileGridSkeleton(modifier = Modifier.fillMaxSize())
            } else {
                FileListSkeleton(modifier = Modifier.fillMaxSize())
            }
            uiState.error != null -> Text(
                "Error: ${uiState.error}",
                Modifier.align(Alignment.Center)
            )
            uiState.items.isEmpty() -> EmptyState(
                icon = Icons.Default.DeleteSweep,
                title = "Trash is empty",
                body = "Files you delete will show up here for a while before they're gone for good.",
                modifier = Modifier.fillMaxSize()
            )
            else -> Column(Modifier.fillMaxSize()) {
                if (selectionMode) {
                    SelectionActionBar(
                        count = selectedIds.size,
                        onCancel = { selectedIds = emptySet() },
                        onRestore = { onBatchRestore() },
                        onDeleteForever = { onBatchDeleteForever() }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showPurgeConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                " Empty trash",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        ViewModeToggle(viewMode) {
                            viewMode = it
                            viewModePrefs.set(ViewModePrefs.TRASH, it)
                        }
                    }
                }
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                        items(uiState.items, key = { it.id }) { file ->
                            TrashGridItem(
                                file = file,
                                selectionMode = selectionMode,
                                selected = file.id in selectedIds,
                                onToggleSelect = { onToggleSelect(file.id) },
                                onEnterSelectionMode = { onToggleSelect(file.id) },
                                onRestore = {
                                    viewModel.restore(file.id)
                                    scope.launch { snackbarHostState.showSnackbar("Restored") }
                                },
                                onDeleteForever = {
                                    viewModel.deletePermanently(file.id)
                                    scope.launch { snackbarHostState.showSnackbar("Permanently deleted") }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn {
                        items(uiState.items, key = { it.id }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { if (selectionMode) onToggleSelect(file.id) },
                                        onLongClick = { if (!selectionMode) onToggleSelect(file.id) }
                                    )
                                    .then(
                                        if (selectionMode) {
                                            Modifier.semantics {
                                                role = Role.Checkbox
                                                this.selected = file.id in selectedIds
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = file.id in selectedIds,
                                        onCheckedChange = { onToggleSelect(file.id) }
                                    )
                                }
                                Text(
                                    text = file.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!selectionMode) {
                                    IconButton(onClick = {
                                        viewModel.restore(file.id)
                                        scope.launch { snackbarHostState.showSnackbar("Restored") }
                                    }) {
                                        Icon(Icons.Default.Restore, "Restore", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        viewModel.deletePermanently(file.id)
                                        scope.launch { snackbarHostState.showSnackbar("Permanently deleted") }
                                    }) {
                                        Icon(Icons.Default.Delete, "Delete forever", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashGridItem(
    file: FileItem,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onEnterSelectionMode: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() },
                onLongClick = { if (!selectionMode) onEnterSelectionMode() }
            )
            .then(
                if (selectionMode) {
                    Modifier.semantics { role = Role.Checkbox; this.selected = selected }
                } else {
                    Modifier
                }
            )
            .padding(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        Text(
            text = file.filename,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
        )
        if (!selectionMode) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore, "Restore", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(Icons.Default.Delete, "Delete forever", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
