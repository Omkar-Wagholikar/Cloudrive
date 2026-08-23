package com.example.cloudrive.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.PlaylistEntity
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileListSkeleton
import com.example.cloudrive.ui.components.music.ArtworkTile
import com.example.cloudrive.ui.components.music.TrackRow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(navController: NavController, playlistId: Long) {
    val locator = CloudriveApp.locator
    val scope = rememberCoroutineScope()

    var playlist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var tracks by remember { mutableStateOf<List<TrackEntity>?>(null) }
    var contextTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(playlistId) {
        locator.playlistRepository.observePlaylist(playlistId).collectLatest { playlist = it }
    }
    androidx.compose.runtime.LaunchedEffect(playlistId) {
        locator.playlistRepository.observePlaylistTracks(playlistId).collectLatest { tracks = it }
    }

    if (deleted) {
        androidx.compose.runtime.LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    contextTrack?.let { track ->
        TrackContextSheet(track = track, navController = navController, onDismiss = { contextTrack = null })
    }

    if (showRenameDialog) {
        RenamePlaylistDialog(
            initialName = playlist?.name.orEmpty(),
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                val current = playlist ?: return@RenamePlaylistDialog
                scope.launch { locator.playlistRepository.renamePlaylist(current, newName) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { showMenu = false; showRenameDialog = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                showMenu = false
                                val current = playlist
                                if (current != null) {
                                    scope.launch { locator.playlistRepository.duplicatePlaylist(current) }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    locator.playlistRepository.deletePlaylist(playlistId)
                                    deleted = true
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val current = tracks
            when {
                current == null -> FileListSkeleton(modifier = Modifier.fillMaxSize())
                current.isEmpty() -> EmptyState(
                    icon = Icons.Default.QueueMusic,
                    title = "No tracks yet",
                    body = "Add tracks to this playlist from the \"Add to playlist\" action on any song.",
                    modifier = Modifier.fillMaxSize()
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkTile(fileId = current.first().fileId, hasArt = current.first().hasArt, size = 72.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(playlist?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${current.size} ${if (current.size == 1) "track" else "tracks"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = {
                            locator.playerController.playQueue(current, 0, sourceLabel = "Playlist: ${playlist?.name.orEmpty()}")
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(" Play all")
                        }
                    }

                    val listState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                        val orderedIds = current.map { it.fileId }
                        val fromIndex = from.index
                        val toIndex = to.index
                        if (fromIndex in orderedIds.indices && toIndex in orderedIds.indices) {
                            val fileId = orderedIds[fromIndex]
                            scope.launch {
                                locator.playlistRepository.moveTrack(playlistId, orderedIds, fileId, toIndex)
                            }
                        }
                    }

                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(current, key = { it.fileId }) { track ->
                            ReorderableItem(reorderableState, key = track.fileId) { isDragging ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                                            else Color.Transparent
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        PlaylistTrackRow(
                                            track = track,
                                            onClick = {
                                                val index = current.indexOf(track)
                                                locator.playerController.playQueue(
                                                    current, index, sourceLabel = "Playlist: ${playlist?.name.orEmpty()}"
                                                )
                                            },
                                            onOverflowClick = { contextTrack = track },
                                            onRemove = {
                                                scope.launch {
                                                    locator.playlistRepository.removeTrack(playlistId, track.fileId)
                                                }
                                            }
                                        )
                                    }
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle()
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps [TrackRow], swapping its overflow button for a small local menu that offers
 * "Remove from playlist" alongside falling through to the shared track context sheet.
 */
@Composable
private fun PlaylistTrackRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onOverflowClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showRowMenu by remember { mutableStateOf(false) }
    Box {
        TrackRow(
            track = track,
            onClick = onClick,
            onOverflowClick = { showRowMenu = true }
        )
        DropdownMenu(expanded = showRowMenu, onDismissRequest = { showRowMenu = false }) {
            DropdownMenuItem(
                text = { Text("Remove from playlist") },
                onClick = { showRowMenu = false; onRemove() }
            )
            DropdownMenuItem(
                text = { Text("Track details") },
                onClick = { showRowMenu = false; onOverflowClick() }
            )
        }
    }
}

@Composable
private fun RenamePlaylistDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Playlist name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
