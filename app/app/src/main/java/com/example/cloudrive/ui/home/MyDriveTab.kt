package com.example.cloudrive.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.ViewMode
import com.example.cloudrive.data.local.ViewModePrefs
import com.example.cloudrive.navigation.PreviewContext
import com.example.cloudrive.navigation.Screen
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.ui.components.CreateFolderDialog
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileGridItem
import com.example.cloudrive.ui.components.FileGridSkeleton
import com.example.cloudrive.ui.components.FileListItem
import com.example.cloudrive.ui.components.FileListSkeleton
import com.example.cloudrive.ui.components.FolderGridItem
import com.example.cloudrive.ui.components.FolderListItem
import com.example.cloudrive.ui.components.MovePickerSheet
import com.example.cloudrive.ui.components.QuotaBlockDialog
import com.example.cloudrive.ui.components.RenameDialog
import com.example.cloudrive.ui.components.rememberSaveToDeviceAction
import com.example.cloudrive.ui.components.FileTypeFilter
import com.example.cloudrive.ui.components.SelectionActionBar
import com.example.cloudrive.ui.components.ServerUnreachableScreen
import com.example.cloudrive.ui.components.ShareSheet
import com.example.cloudrive.ui.components.SortFilterBar
import com.example.cloudrive.ui.components.SortOption
import com.example.cloudrive.ui.components.UploadProgressDialog
import com.example.cloudrive.ui.components.ViewModeToggle
import com.example.cloudrive.ui.components.sortedAndFiltered
import com.example.cloudrive.ui.profile.ProfileViewModel
import com.example.cloudrive.ui.upload.UploadUiState
import com.example.cloudrive.ui.upload.UploadViewModel
import kotlinx.coroutines.launch

@Composable
fun MyDriveTab(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = viewModel(),
    uploadViewModel: UploadViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val uploadState by uploadViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fileRepo = CloudriveApp.locator.fileRepository
    val lanResolver = CloudriveApp.locator.lanResolver
    val saveToDevice = rememberSaveToDeviceAction(snackbarHostState, scope)
    val viewModePrefs = remember { CloudriveApp.locator.viewModePrefs }
    var viewMode by remember { mutableStateOf(viewModePrefs.get(ViewModePrefs.MY_DRIVE)) }
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
        uri?.let { uploadViewModel.uploadFile(it, folderId = null) }
    }

    var resumeTarget by remember { mutableStateOf<com.example.cloudrive.data.model.UploadSession?>(null) }
    val resumeFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val session = resumeTarget
        if (uri != null && session != null) uploadViewModel.resumeUpload(uri, session)
        resumeTarget = null
    }
    val openSessions by uploadViewModel.openSessions.collectAsState()

    LaunchedEffect(Unit) {
        uploadViewModel.checkOpenSessions()
    }

    LaunchedEffect(uploadState) {
        when (uploadState) {
            is UploadUiState.Success -> {
                snackbarHostState.showSnackbar("Upload complete")
                uploadViewModel.reset()
                homeViewModel.load()
            }
            is UploadUiState.Error -> {
                snackbarHostState.showSnackbar((uploadState as UploadUiState.Error).message)
                uploadViewModel.reset()
            }
            else -> {}
        }
    }

    fun onFolderClick(folderId: Long) = navController.navigate(Screen.Folder.createRoute(folderId))
    fun onFolderDelete(folderId: Long) {
        scope.launch {
            homeViewModel.deleteFolder(folderId)
            snackbarHostState.showSnackbar("Folder deleted")
        }
    }
    fun onFileRename(file: FileItem) { renameFileId = file.id; renameInitial = file.filename }
    fun onFileDelete(file: FileItem) {
        homeViewModel.deleteFile(file.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                homeViewModel.restoreFile(file.id)
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
    fun onToggleSelect(fileId: Long) {
        selectedIds = if (fileId in selectedIds) selectedIds - fileId else selectedIds + fileId
    }
    fun onBatchTrash() {
        val ids = selectedIds.toList()
        val previous = uiState
        homeViewModel.applyOptimistic(uiState.copy(files = uiState.files.filterNot { it.id in ids }))
        selectedIds = emptySet()
        scope.launch {
            val result = fileRepo.batchTrash(ids)
            if (result.isFailure) {
                homeViewModel.applyOptimistic(previous)
            }
            snackbarHostState.showSnackbar("Moved ${ids.size} item(s) to trash")
        }
    }

    val sortedFiles = uiState.files.sortedAndFiltered(sortOption, typeFilter)

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StorageCard(
                usedBytes = profileState.user?.usedBytes ?: 0L,
                quotaBytes = profileState.user?.quotaBytes ?: 0L,
                isOnLan = lanResolver.isOnLan()
            )
            openSessions.forEach { session ->
                ResumeUploadBanner(
                    session = session,
                    onResume = { resumeTarget = session; resumeFilePicker.launch("*/*") },
                    onDismiss = { uploadViewModel.dismissSession(session.uploadId) }
                )
            }
            if (selectionMode) {
                SelectionActionBar(
                    count = selectedIds.size,
                    onCancel = { selectedIds = emptySet() },
                    onMove = { showBatchMove = true },
                    onTrash = { onBatchTrash() }
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortFilterBar(
                        sort = sortOption,
                        onSortChange = { sortOption = it },
                        filter = typeFilter,
                        onFilterChange = { typeFilter = it }
                    )
                    ViewModeToggle(viewMode) {
                        viewMode = it
                        viewModePrefs.set(ViewModePrefs.MY_DRIVE, it)
                    }
                }
            }
            when {
                uiState.isLoading -> if (viewMode == ViewMode.GRID) {
                    FileGridSkeleton(modifier = Modifier.fillMaxSize())
                } else {
                    FileListSkeleton(modifier = Modifier.fillMaxSize())
                }
                uiState.isConnectionError && uiState.folders.isEmpty() && uiState.files.isEmpty() ->
                    ServerUnreachableScreen(
                        host = CloudriveApp.locator.tokenStore.serverUrl,
                        onRetry = { homeViewModel.load() },
                        onCheckServerAddress = {
                            navController.navigate(Screen.Auth.route)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                uiState.folders.isEmpty() && uiState.files.isEmpty() -> EmptyState(
                    icon = Icons.Default.UploadFile,
                    title = "Your drive is empty",
                    actionLabel = "Upload your first file",
                    onAction = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxSize()
                )
                uiState.folders.isEmpty() && sortedFiles.isEmpty() -> EmptyState(
                    icon = Icons.Default.FilterAltOff,
                    title = "No files match this filter",
                    modifier = Modifier.fillMaxSize()
                )
                viewMode == ViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.folders, key = { "folder_${it.id}" }) { folder ->
                        FolderGridItem(
                            folder = folder,
                            onClick = { onFolderClick(folder.id) },
                            onDelete = { onFolderDelete(folder.id) }
                        )
                    }
                    items(sortedFiles, key = { "file_${it.id}" }) { file ->
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
                    if (uiState.folders.isNotEmpty()) {
                        item { SectionHeader("Folders") }
                        items(uiState.folders, key = { "folder_${it.id}" }) { folder ->
                            FolderListItem(
                                folder = folder,
                                onClick = { onFolderClick(folder.id) },
                                onDelete = { onFolderDelete(folder.id) }
                            )
                        }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                    }
                    if (sortedFiles.isNotEmpty()) {
                        item { SectionHeader("Files") }
                        items(sortedFiles, key = { "file_${it.id}" }) { file ->
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

        // FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
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

    if (showCreateFolder) {
        CreateFolderDialog(
            onConfirm = { name ->
                scope.launch {
                    CloudriveApp.locator.folderRepository.createFolder(name, null)
                    homeViewModel.load()
                }
                showCreateFolder = false
            },
            onDismiss = { showCreateFolder = false }
        )
    }

    renameFileId?.let { id ->
        RenameDialog(
            initialName = renameInitial,
            onConfirm = { newName ->
                homeViewModel.renameFile(id, newName)
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

    (uploadState as? UploadUiState.QuotaExceeded)?.let { quota ->
        QuotaBlockDialog(
            usedBytes = quota.usedBytes,
            quotaBytes = quota.quotaBytes,
            neededBytes = quota.neededBytes,
            onOpenTrash = {
                uploadViewModel.reset()
                navController.navigate(Screen.Trash.route)
            },
            onDismiss = { uploadViewModel.reset() }
        )
    }

    moveTarget?.let { file ->
        MovePickerSheet(
            fileName = file.filename,
            onDismiss = { moveTarget = null },
            onMove = { destinationFolderId ->
                homeViewModel.moveFile(file.id, destinationFolderId)
                moveTarget = null
                scope.launch { snackbarHostState.showSnackbar("Moved \"${file.filename}\"") }
            }
        )
    }

    if (showBatchMove) {
        val ids = selectedIds.toList()
        MovePickerSheet(
            fileName = "${ids.size} file(s)",
            onDismiss = { showBatchMove = false },
            onMove = { destinationFolderId ->
                val previous = uiState
                if (destinationFolderId != null) {
                    homeViewModel.applyOptimistic(uiState.copy(files = uiState.files.filterNot { it.id in ids }))
                }
                selectedIds = emptySet()
                showBatchMove = false
                scope.launch {
                    val result = fileRepo.batchMove(ids, destinationFolderId)
                    if (result.isFailure) {
                        homeViewModel.applyOptimistic(previous)
                    }
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

@Composable
private fun StorageCard(usedBytes: Long, quotaBytes: Long, isOnLan: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Storage", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (quotaBytes > 0) {
                    val fraction = usedBytes.toFloat() / quotaBytes.toFloat()
                    Column(Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${formatStorageSize(usedBytes)} of ${formatStorageSize(quotaBytes)} used",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                } else {
                    Text("${formatStorageSize(usedBytes)} used", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isOnLan) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Connected over LAN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ResumeUploadBanner(
    session: com.example.cloudrive.data.model.UploadSession,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val percent = if (session.totalSize > 0) (session.offset * 100 / session.totalSize).toInt() else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Interrupted upload: ${session.filename ?: "file"}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$percent% done — pick the file again to resume",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss") }
        androidx.compose.material3.TextButton(onClick = onResume) { Text("Resume") }
    }
}
