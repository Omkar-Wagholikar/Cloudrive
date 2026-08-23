package com.example.cloudrive.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.data.remote.api.MusicStatusResponse
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.music.ArtworkTile
import kotlinx.coroutines.delay

@Composable
fun MusicHomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: MusicHomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var indexingStatus by remember { mutableStateOf<MusicStatusResponse?>(null) }

    // Poll /music/status every 5s while there are pending tracks, so the banner reflects
    // indexing progress; stop as soon as pending reaches 0.
    LaunchedEffect(Unit) {
        while (true) {
            val status = CloudriveApp.locator.trackRepository.status().getOrNull()
            indexingStatus = status
            if (status == null || status.pending <= 0) break
            delay(5000)
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Unit
            uiState.totalCount == 0 -> EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "No music yet",
                body = "Once your library finishes indexing, your tracks, albums, and artists will show up here.",
                modifier = Modifier.fillMaxSize()
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                indexingStatus?.takeIf { it.pending > 0 }?.let { status ->
                    IndexingBanner(status = status, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (uiState.recentlyAdded.isNotEmpty()) {
                    TrackRail(title = "Recently added", tracks = uiState.recentlyAdded)
                }
                if (uiState.recentlyPlayed.isNotEmpty()) {
                    TrackRail(title = "Recently played", tracks = uiState.recentlyPlayed)
                }
                if (uiState.favorites.isNotEmpty()) {
                    TrackRail(title = "Favorites", tracks = uiState.favorites)
                }
            }
        }
    }
}

@Composable
private fun IndexingBanner(status: MusicStatusResponse, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "Indexing ${status.indexed} of ${status.totalAudio} tracks…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            LinearProgressIndicator(
                progress = {
                    if (status.totalAudio > 0) {
                        (status.indexed.toFloat() / status.totalAudio.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun TrackRail(title: String, tracks: List<TrackEntity>) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks, key = { it.fileId }) { track ->
                Column(
                    modifier = Modifier
                        .width(112.dp)
                        .clickable {
                            CloudriveApp.locator.playerController.playQueue(listOf(track), 0)
                        },
                    horizontalAlignment = Alignment.Start
                ) {
                    ArtworkTile(fileId = track.fileId, hasArt = track.hasArt, size = 112.dp)
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth()
                    )
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
