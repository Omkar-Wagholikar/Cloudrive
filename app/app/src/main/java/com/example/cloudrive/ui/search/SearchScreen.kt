package com.example.cloudrive.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.ViewMode
import com.example.cloudrive.data.local.ViewModePrefs
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.navigation.PreviewContext
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileGridItem
import com.example.cloudrive.ui.components.FileListItem
import com.example.cloudrive.ui.components.FileTypeFilter
import com.example.cloudrive.ui.components.MovePickerSheet
import com.example.cloudrive.ui.components.RenameDialog
import com.example.cloudrive.ui.components.SelectionActionBar
import com.example.cloudrive.ui.components.ShareSheet
import com.example.cloudrive.ui.components.SortFilterBar
import com.example.cloudrive.ui.components.SortOption
import com.example.cloudrive.ui.components.ViewModeToggle
import com.example.cloudrive.ui.components.sortedAndFiltered
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModePrefs = remember { CloudriveApp.locator.viewModePrefs }
    var viewMode by remember { mutableStateOf(viewModePrefs.get(ViewModePrefs.SEARCH)) }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    var typeFilter by remember { mutableStateOf(FileTypeFilter.ALL) }
    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var renameInitial by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<FileItem?>(null) }
    var shareTarget by remember { mutableStateOf<FileItem?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMove by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()
    val fileRepo = CloudriveApp.locator.fileRepository

    fun onToggleSelect(fileId: Long) {
        selectedIds = if (fileId in selectedIds) selectedIds - fileId else selectedIds + fileId
    }
    fun onFileOpen(file: FileItem) {
        PreviewContext.siblingImages = uiState.results.filter { it.mimeType.startsWith("image/") }
        navController.navigate(Screen.Preview.createRoute(file.id))
    }
    fun onBatchTrash() {
        val ids = selectedIds.toList()
        scope.launch {
            fileRepo.batchTrash(ids)
            selectedIds = emptySet()
            viewModel.refresh()
            snackbarHostState.showSnackbar("Moved ${ids.size} item(s) to trash")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Search files…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    ViewModeToggle(viewMode) {
                        viewMode = it
                        viewModePrefs.set(ViewModePrefs.SEARCH, it)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val sortedResults = uiState.results.sortedAndFiltered(sortOption, typeFilter)

        Column(Modifier.fillMaxSize().padding(padding)) {
            if (selectionMode) {
                SelectionActionBar(
                    count = selectedIds.size,
                    onCancel = { selectedIds = emptySet() },
                    onMove = { showBatchMove = true },
                    onTrash = { onBatchTrash() }
                )
            } else if (uiState.results.isNotEmpty()) {
                SortFilterBar(
                    sort = sortOption,
                    onSortChange = { sortOption = it },
                    filter = typeFilter,
                    onFilterChange = { typeFilter = it }
                )
            }
            Box(Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.results.isEmpty() && uiState.query.isNotBlank() ->
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No results",
                        body = "Try a different search term.",
                        modifier = Modifier.fillMaxSize()
                    )
                sortedResults.isEmpty() && uiState.results.isNotEmpty() ->
                    EmptyState(
                        icon = Icons.Default.FilterAltOff,
                        title = "No files match this filter",
                        modifier = Modifier.fillMaxSize()
                    )
                viewMode == ViewMode.GRID -> LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    items(sortedResults, key = { it.id }) { file ->
                        val fileRepo = CloudriveApp.locator.fileRepository
                        FileGridItem(
                            file = file,
                            thumbnailUrl = if (file.thumbReady == 1) fileRepo.thumbnailUrl(file.id) else null,
                            onRename = { renameTarget = file.id; renameInitial = file.filename },
                            onDelete = {
                                scope.launch {
                                    fileRepo.deleteFile(file.id)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Moved to trash",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        CloudriveApp.locator.trashRepository.restore(file.id)
                                    }
                                }
                            },
                            onShare = { shareTarget = file },
                            onMove = { moveTarget = file },
                            onDownload = {
                                val url = fileRepo.downloadUrl(file.id)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onOpen = { onFileOpen(file) },
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            onToggleSelect = { onToggleSelect(file.id) },
                            onEnterSelectionMode = { onToggleSelect(file.id) }
                        )
                    }
                }
                else -> LazyColumn {
                    items(sortedResults, key = { it.id }) { file ->
                        val fileRepo = CloudriveApp.locator.fileRepository
                        FileListItem(
                            file = file,
                            thumbnailUrl = if (file.thumbReady == 1) fileRepo.thumbnailUrl(file.id) else null,
                            onRename = { renameTarget = file.id; renameInitial = file.filename },
                            onDelete = {
                                scope.launch {
                                    fileRepo.deleteFile(file.id)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Moved to trash",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        CloudriveApp.locator.trashRepository.restore(file.id)
                                    }
                                }
                            },
                            onShare = { shareTarget = file },
                            onMove = { moveTarget = file },
                            onDownload = {
                                val url = fileRepo.downloadUrl(file.id)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            onOpen = { onFileOpen(file) },
                            selectionMode = selectionMode,
                            selected = file.id in selectedIds,
                            onToggleSelect = { onToggleSelect(file.id) },
                            onEnterSelectionMode = { onToggleSelect(file.id) }
                        )
                    }
                }
            }
            }
        }

        renameTarget?.let { id ->
            RenameDialog(
                initialName = renameInitial,
                onConfirm = { newName ->
                    scope.launch { CloudriveApp.locator.fileRepository.renameFile(id, newName) }
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }

        if (showBatchMove) {
            val ids = selectedIds.toList()
            MovePickerSheet(
                fileName = "${ids.size} file(s)",
                onDismiss = { showBatchMove = false },
                onMove = { destinationFolderId ->
                    scope.launch {
                        fileRepo.batchMove(ids, destinationFolderId)
                        selectedIds = emptySet()
                        showBatchMove = false
                        viewModel.refresh()
                        snackbarHostState.showSnackbar("Moved ${ids.size} item(s)")
                    }
                }
            )
        }

        moveTarget?.let { file ->
            val fileRepo = CloudriveApp.locator.fileRepository
            MovePickerSheet(
                fileName = file.filename,
                onDismiss = { moveTarget = null },
                onMove = { destinationFolderId ->
                    scope.launch {
                        fileRepo.moveFile(file.id, destinationFolderId)
                        snackbarHostState.showSnackbar("Moved \"${file.filename}\"")
                    }
                    moveTarget = null
                }
            )
        }

        shareTarget?.let { file ->
            val fileRepo = CloudriveApp.locator.fileRepository
            ShareSheet(
                fileName = file.filename,
                onDismiss = { shareTarget = null },
                onCreateLink = { expiresIn -> fileRepo.shareFile(file.id, expiresIn) },
                onShareVia = { url ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                    shareTarget = null
                }
            )
        }
    }
}
