package com.example.cloudrive.ui.music

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileListSkeleton
import com.example.cloudrive.ui.components.music.AlphabetRail
import com.example.cloudrive.ui.components.music.TrackRow
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Date added"),
    DURATION("Duration")
}

@Composable
fun SongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val musicPrefs = remember { CloudriveApp.locator.musicPrefs }
    var sortMode by remember {
        mutableStateOf(
            musicPrefs.sortMode?.let { saved ->
                runCatching { SortMode.valueOf(saved) }.getOrNull()
            } ?: SortMode.TITLE
        )
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var contextTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Multi-select
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    // Downloaded-only filter / offline dimming
    var downloadedOnly by remember { mutableStateOf(false) }
    val downloads by CloudriveApp.locator.downloadRepository.observeAll().collectAsState(initial = emptyList())
    val isOnline by CloudriveApp.locator.networkMonitor.isOnline.collectAsState()
    val downloadsByFileId = remember(downloads) { downloads.associateBy { it.fileId } }
    val effectiveFilterActive = downloadedOnly

    val sortedTracks = remember(uiState.tracks, sortMode) {
        when (sortMode) {
            SortMode.TITLE -> uiState.tracks.sortedBy { it.displayTitle.lowercase() }
            SortMode.ARTIST -> uiState.tracks.sortedBy { it.displayArtist.lowercase() }
            SortMode.ALBUM -> uiState.tracks.sortedBy { (it.album ?: "").lowercase() }
            SortMode.DATE_ADDED -> uiState.tracks.sortedByDescending { it.createdAt }
            SortMode.DURATION -> uiState.tracks.sortedBy { it.durationMs }
        }
    }

    val visibleTracks = remember(sortedTracks, effectiveFilterActive, downloadsByFileId) {
        if (effectiveFilterActive) {
            sortedTracks.filter { downloadsByFileId[it.fileId]?.state == DownloadEntity.State.DONE }
        } else {
            sortedTracks
        }
    }

    val letters = remember(visibleTracks, sortMode) {
        if (sortMode == SortMode.TITLE) {
            visibleTracks.map { it.displayTitle.firstOrNull()?.uppercaseChar() ?: '#' }.distinct()
        } else {
            emptyList()
        }
    }

    fun onToggleSelect(fileId: Long) {
        selectedIds = if (fileId in selectedIds) selectedIds - fileId else selectedIds + fileId
    }

    if (showSortSheet) {
        SortFilterSheet(
            selected = sortMode,
            onSelect = {
                sortMode = it
                musicPrefs.sortMode = it.name
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    contextTrack?.let { track ->
        TrackContextSheet(
            track = track,
            navController = navController,
            onDismiss = { contextTrack = null }
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            fileIds = selectedIds.toList(),
            onDismiss = { showPlaylistPicker = false },
            onAdded = {
                showPlaylistPicker = false
                selectedIds = emptySet()
            }
        )
    }

    Box(modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> FileListSkeleton(modifier = Modifier.fillMaxSize())
            uiState.tracks.isEmpty() -> EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "No songs yet",
                body = "Tracks from your library will appear here once indexing finishes.",
                modifier = Modifier.fillMaxSize()
            )
            else -> Row(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    if (selectionMode) {
                        SongsSelectionActionBar(
                            count = selectedIds.size,
                            onCancel = { selectedIds = emptySet() },
                            onPlay = {
                                val tracksToPlay = visibleTracks.filter { it.fileId in selectedIds }
                                CloudriveApp.locator.playerController.playQueue(tracksToPlay, 0, sourceLabel = "Songs")
                                selectedIds = emptySet()
                            },
                            onAddToPlaylist = { showPlaylistPicker = true }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = downloadedOnly,
                                onClick = { downloadedOnly = !downloadedOnly },
                                label = { Text("Downloaded only") },
                                leadingIcon = { Icon(Icons.Default.CloudDone, contentDescription = null) }
                            )
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                        }
                    }
                    if (!isOnline && !downloadedOnly) {
                        Text(
                            text = "You're offline — tracks that aren't downloaded are unavailable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    if (visibleTracks.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.CloudDone,
                            title = "No downloaded songs",
                            body = "Tracks you download will show up here.",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(visibleTracks, key = { it.fileId }) { track ->
                                val download = downloadsByFileId[track.fileId]
                                val unavailable = !isOnline && download?.state != DownloadEntity.State.DONE
                                val selected = track.fileId in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(track.fileId, selectionMode) {
                                            detectTapGestures(onLongPress = { onToggleSelect(track.fileId) })
                                        }
                                        .then(
                                            if (selectionMode) {
                                                Modifier.semantics {
                                                    role = Role.Checkbox
                                                    this.selected = selected
                                                }
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { onToggleSelect(track.fileId) },
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                    TrackRow(
                                        track = track,
                                        onClick = {
                                            if (selectionMode) {
                                                onToggleSelect(track.fileId)
                                            } else if (!unavailable) {
                                                val index = visibleTracks.indexOf(track)
                                                CloudriveApp.locator.playerController
                                                    .playQueue(visibleTracks, index, sourceLabel = "Songs")
                                            }
                                        },
                                        onOverflowClick = { contextTrack = track },
                                        downloadState = download,
                                        dimmed = unavailable,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
                if (letters.isNotEmpty()) {
                    AlphabetRail(
                        letters = letters,
                        onLetterSelected = { letter ->
                            val index = visibleTracks.indexOfFirst {
                                it.displayTitle.firstOrNull()?.uppercaseChar() == letter
                            }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/** Multi-select action bar for [SongsScreen]: batch play + add to playlist. */
@Composable
private fun SongsSelectionActionBar(
    count: Int,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
        }
        IconButton(onClick = onAddToPlaylist) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist")
        }
    }
}

/**
 * Simple playlist picker for batch "Add to playlist" — [AddToPlaylistSheet] (built in parallel)
 * is single-track only, so multi-select goes straight through [com.example.cloudrive.data.repository.PlaylistRepository].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(
    fileIds: List<Long>,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val playlists by CloudriveApp.locator.playlistRepository.observePlaylists()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Add ${fileIds.size} song(s) to playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (playlists.isEmpty()) {
            Text(
                "No playlists yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        playlists.forEach { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                CloudriveApp.locator.playlistRepository.addTracks(playlist.id, fileIds)
                                onAdded()
                            }
                        },
                        onLongClick = {}
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Text("Cancel")
        }
    }
}
