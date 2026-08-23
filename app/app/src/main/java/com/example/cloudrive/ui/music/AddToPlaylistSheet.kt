package com.example.cloudrive.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.PlaylistWithCount
import com.example.cloudrive.data.local.music.TrackEntity
import kotlinx.coroutines.launch

/**
 * Bottom sheet for adding/removing [track] to any playlist. Invoked from wherever a track's
 * "Add to playlist" action lives (e.g. [TrackContextSheet], wired up elsewhere).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(track: TrackEntity, onDismiss: () -> Unit) {
    val locator = CloudriveApp.locator
    val scope = rememberCoroutineScope()
    val playlists by locator.playlistRepository.observePlaylists().collectAsState(initial = null)
    var newPlaylistName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Add to playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !creating) {
                    val name = newPlaylistName.trim().ifBlank { "New playlist" }
                    creating = true
                    scope.launch {
                        val id = locator.playlistRepository.createPlaylist(name)
                        locator.playlistRepository.addTracks(id, listOf(track.fileId))
                        creating = false
                        onDismiss()
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
            }
            Text("New playlist", modifier = Modifier.padding(start = 16.dp))
        }

        val current = playlists
        when {
            current == null -> {
                // still loading, nothing to show yet
            }
            current.isEmpty() -> {
                Text(
                    text = "No playlists yet — create one above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> LazyColumn {
                items(current, key = { it.id }) { playlist ->
                    PlaylistToggleRow(
                        playlist = playlist,
                        track = track,
                        onToggle = { contains ->
                            scope.launch {
                                if (contains) {
                                    locator.playlistRepository.removeTrack(playlist.id, track.fileId)
                                } else {
                                    locator.playlistRepository.addTracks(playlist.id, listOf(track.fileId))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistToggleRow(
    playlist: PlaylistWithCount,
    track: TrackEntity,
    onToggle: (currentlyContains: Boolean) -> Unit
) {
    val locator = CloudriveApp.locator
    // Reactive rather than a one-shot containsTrack() check, so the checkmark updates
    // immediately after this row's own toggle (or any other change to the playlist).
    val playlistTracks by locator.playlistRepository.observePlaylistTracks(playlist.id)
        .collectAsState(initial = emptyList())
    val contains = playlistTracks.any { it.fileId == track.fileId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(contains) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (contains) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Default.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Transparent)
        }
        Text(
            text = playlist.name,
            modifier = Modifier.padding(start = 16.dp).weight(1f)
        )
        Text(
            text = "${playlist.trackCount}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
