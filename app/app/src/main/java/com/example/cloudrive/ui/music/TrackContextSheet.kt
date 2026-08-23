package com.example.cloudrive.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.navigation.Screen
import kotlinx.coroutines.launch

/** Overflow-menu sheet for a single track: play-next, add to playlist, download, favorite, go-to nav. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextSheet(
    track: TrackEntity,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var favorite by remember(track.fileId) { mutableStateOf(track.favorite) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val downloadState by CloudriveApp.locator.downloadRepository.observe(track.fileId)
        .collectAsState(initial = null)

    if (showAddToPlaylist) {
        AddToPlaylistSheet(track = track, onDismiss = { showAddToPlaylist = false })
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(track.displayTitle) },
            supportingContent = { Text(track.displayArtist) }
        )
        ListItem(
            headlineContent = { Text("Play next") },
            leadingContent = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable {
                CloudriveApp.locator.playerController.addNext(track)
                onDismiss()
            }
        )
        ListItem(
            headlineContent = { Text("Add to playlist") },
            leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().clickable {
                showAddToPlaylist = true
            }
        )
        if (downloadState?.state == DownloadEntity.State.DONE) {
            ListItem(
                headlineContent = { Text("Remove download") },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { CloudriveApp.locator.downloadRepository.removeDownload(track.fileId) }
                    onDismiss()
                }
            )
        } else {
            ListItem(
                headlineContent = { Text("Download") },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    CloudriveApp.locator.downloadRepository.enqueue(track)
                    onDismiss()
                }
            )
        }
        ListItem(
            headlineContent = { Text(if (favorite) "Remove from favorites" else "Add to favorites") },
            leadingContent = {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth().clickable {
                favorite = !favorite
                scope.launch {
                    CloudriveApp.locator.trackRepository.setFavorite(track.fileId, favorite)
                }
            }
        )
        val albumName = track.album
        if (albumName != null) {
            ListItem(
                headlineContent = { Text("Go to album") },
                leadingContent = { Icon(Icons.Default.Album, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    navController.navigate(Screen.MusicAlbum.createRoute(albumName))
                }
            )
        }
        val artistName = track.artist
        if (artistName != null) {
            ListItem(
                headlineContent = { Text("Go to artist") },
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable {
                    onDismiss()
                    navController.navigate(Screen.MusicArtist.createRoute(artistName))
                }
            )
        }
    }
}
