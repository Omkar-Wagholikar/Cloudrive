package com.example.cloudrive.ui.folder

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.ViewMode
import com.example.cloudrive.data.local.ViewModePrefs
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.navigation.PreviewContext
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.ui.components.CreateFolderDialog
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileGridItem
import com.example.cloudrive.ui.components.FileListItem
import com.example.cloudrive.ui.components.FileTypeFilter
import com.example.cloudrive.ui.components.FolderGridItem
import com.example.cloudrive.ui.components.FolderListItem
import com.example.cloudrive.ui.components.MovePickerSheet
import com.example.cloudrive.ui.components.RenameDialog
import com.example.cloudrive.ui.components.rememberSaveToDeviceAction
import com.example.cloudrive.ui.components.SelectionActionBar
import com.example.cloudrive.ui.components.ShareSheet
import com.example.cloudrive.ui.components.SortFilterBar
import com.example.cloudrive.ui.components.SortOption
import com.example.cloudrive.ui.components.UploadProgressDialog
import com.example.cloudrive.ui.components.ViewModeToggle
import com.example.cloudrive.ui.components.sortedAndFiltered
import com.example.cloudrive.ui.upload.UploadUiState
import com.example.cloudrive.ui.upload.UploadViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    navController: NavController,
    viewModel: FolderViewModel = viewModel(),
    uploadViewModel: UploadViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by uploadViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fileRepo = CloudriveApp.locator.fileRepository
    val saveToDevice = rememberSaveToDeviceAction(snackbarHostState, scope)
    val viewModePrefs = remember { CloudriveApp.locator.viewModePrefs }
    var viewMode by remember { mutableStateOf(viewModePrefs.get(ViewModePrefs.FOLDER)) }
    var sortOption by remember { mutableStateOf(SortOption.NAME) }
    var typeFilter by remember { mutableStateOf(FileTypeFilter.ALL) }

    var fabExpanded by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameFileId by remember { mutableStateOf<Long?>(null) }
    var renameInitial by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<FileItem?>(null) }
    var shareTarget by remember { mutableStateOf<FileItem?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBatchMove by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadViewModel.uploadFile(it, folderId = viewModel.folderId) }
    }

    LaunchedEffect(uploadState) {
        when (uploadState) {
            is UploadUiState.Success -> {
                snackbarHostState.showSnackbar("Upload complete")
                uploadViewModel.reset()
                viewModel.load()
            }
            is UploadUiState.Error -> {
                snackbarHostState.showSnackbar((uploadState as UploadUiState.Error).message)
                uploadViewModel.reset()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Breadcrumb row
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) } }) {
                            Text("My Drive")
                        }
                        uiState.breadcrumb.forEach { crumb ->
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, null)
                            TextButton(onClick = {
                                if (crumb.id != viewModel.folderId) {
                                    navController.navigate(Screen.Folder.createRoute(crumb.id))
                                }
                            }) {
                                Text(crumb.name)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    ViewModeToggle(viewMode) {
                        viewMode = it
                        viewModePrefs.set(ViewModePrefs.FOLDER, it)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                DropdownMenu(
                    expanded = fabExpanded,
                    onDismissRequest = { fabExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Upload File") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = { fabExpanded = false; filePicker.launch("*/*") }
                    )
                    DropdownMenuItem(
                        text = { Text("New Folder") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = { fabExpanded = false; showCreateFolder = true }
                    )
                }
                FloatingActionButton(onClick = { fabExpanded = true }) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            fun onFileRename(file: FileItem) { renameFileId = file.id; renameInitial = file.filename }
            fun onFileDelete(file: FileItem) {
                viewModel.deleteFile(file.id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Moved to trash",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreFile(file.id)
                    }
                }
            }
            fun onFileDownload(file: FileItem) {
                saveToDevice(file)
            }
            fun onFileOpen(file: FileItem) {
                PreviewContext.siblingImages = uiState.files.filter { it.mimeType.startsWith("image/") }
                navController.navigate(Screen.Preview.createRoute(file.id))
            }
            fun onFolderDelete(folderId: Long) {
                viewModel.deleteFolder(folderId)
                scope.launch { snackbarHostState.showSnackbar("Folder deleted") }
            }
            fun onToggleSelect(fileId: Long) {
                selectedIds = if (fileId in selectedIds) selectedIds - fileId else selectedIds + fileId
            }
            fun onBatchTrash() {
                val ids = selectedIds.toList()
                scope.launch {
                    fileRepo.batchTrash(ids)
                    selectedIds = emptySet()
                    viewModel.load()
                    snackbarHostState.showSnackbar("Moved ${ids.size} item(s) to trash")
                }
            }

            val sortedFiles = uiState.files.sortedAndFiltered(sortOption, typeFilter)

            if (selectionMode) {
                SelectionActionBar(
                    count = selectedIds.size,
                    onCancel = { selectedIds = emptySet() },
                    onMove = { showBatchMove = true },
                    onTrash = { onBatchTrash() }
                )
            } else if (uiState.files.isNotEmpty()) {
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
                    uiState.error != null -> Text("Error: ${uiState.error}", Modifier.align(Alignment.Center))
                    uiState.subfolders.isEmpty() && uiState.files.isEmpty() ->
                        EmptyState(
                            icon = Icons.Default.UploadFile,
                            title = "Empty folder",
                            body = "Upload files or create a subfolder to get started.",
                            modifier = Modifier.fillMaxSize()
                        )
                    uiState.subfolders.isEmpty() && sortedFiles.isEmpty() ->
                        EmptyState(
                            icon = Icons.Default.FilterAltOff,
                            title = "No files match this filter",
                            modifier = Modifier.fillMaxSize()
                        )
                    viewMode == ViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.subfolders, key = { "sf_${it.id}" }) { folder ->
                            FolderGridItem(
                                folder = folder,
                                onClick = { navController.navigate(Screen.Folder.createRoute(folder.id)) },
                                onDelete = { onFolderDelete(folder.id) }
                            )
                        }
                        items(sortedFiles, key = { "f_${it.id}" }) { file ->
                            FileGridItem(
                                file = file,
                                thumbnailUrl = if (file.thumbReady == 1) fileRepo.thumbnailUrl(file.id) else null,
                                onRename = { onFileRename(file) },
                                onDelete = { onFileDelete(file) },
                                onShare = { shareTarget = file },
                                onMove = { moveTarget = file },
                                onDownload = { onFileDownload(file) },
                                onOpen = { onFileOpen(file) },
                                selectionMode = selectionMode,
                                selected = file.id in selectedIds,
                                onToggleSelect = { onToggleSelect(file.id) },
                                onEnterSelectionMode = { onToggleSelect(file.id) }
                            )
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (uiState.subfolders.isNotEmpty()) {
                            item {
                                Text(
                                    "Folders",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(uiState.subfolders, key = { "sf_${it.id}" }) { folder ->
                                FolderListItem(
                                    folder = folder,
                                    onClick = { navController.navigate(Screen.Folder.createRoute(folder.id)) },
                                    onDelete = { onFolderDelete(folder.id) }
                                )
                            }
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        }
                        if (sortedFiles.isNotEmpty()) {
                            item {
                                Text(
                                    "Files",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(sortedFiles, key = { "f_${it.id}" }) { file ->
                                FileListItem(
                                    file = file,
                                    thumbnailUrl = if (file.thumbReady == 1) fileRepo.thumbnailUrl(file.id) else null,
                                    onRename = { onFileRename(file) },
                                    onDelete = { onFileDelete(file) },
                                    onShare = { shareTarget = file },
                                    onMove = { moveTarget = file },
                                    onDownload = { onFileDownload(file) },
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
        }
    }

    if (showCreateFolder) {
        CreateFolderDialog(
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateFolder = false
            },
            onDismiss = { showCreateFolder = false }
        )
    }

    renameFileId?.let { id ->
        RenameDialog(
            initialName = renameInitial,
            onConfirm = { newName ->
                viewModel.renameFile(id, newName)
                renameFileId = null
            },
            onDismiss = { renameFileId = null }
        )
    }

    if (uploadState is UploadUiState.Uploading) {
        UploadProgressDialog(
            progress = (uploadState as UploadUiState.Uploading).progress,
            onCancel = { uploadViewModel.reset() }
        )
    }

    moveTarget?.let { file ->
        MovePickerSheet(
            fileName = file.filename,
            onDismiss = { moveTarget = null },
            onMove = { destinationFolderId ->
                viewModel.moveFile(file.id, destinationFolderId)
                moveTarget = null
                scope.launch { snackbarHostState.showSnackbar("Moved \"${file.filename}\"") }
            }
        )
    }

    if (showBatchMove) {
        val ids = selectedIds.toList()
        MovePickerSheet(
            fileName = "${ids.size} file(s)",
            excludeFolderId = viewModel.folderId,
            onDismiss = { showBatchMove = false },
            onMove = { destinationFolderId ->
                scope.launch {
                    fileRepo.batchMove(ids, destinationFolderId)
                    selectedIds = emptySet()
                    showBatchMove = false
                    viewModel.load()
                    snackbarHostState.showSnackbar("Moved ${ids.size} item(s)")
                }
            }
        )
    }

    shareTarget?.let { file ->
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
