package com.example.cloudrive.ui.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileListSkeleton
import com.example.cloudrive.ui.components.music.TrackRow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(navController: NavController, genreName: String) {
    var tracks by remember { mutableStateOf<List<TrackEntity>?>(null) }
    var contextTrack by remember { mutableStateOf<TrackEntity?>(null) }

    LaunchedEffect(genreName) {
        CloudriveApp.locator.trackRepository.observeByGenre(genreName).collectLatest { tracks = it }
    }

    contextTrack?.let { track ->
        TrackContextSheet(track = track, navController = navController, onDismiss = { contextTrack = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(genreName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    icon = Icons.Default.LibraryMusic,
                    title = "No tracks",
                    body = "This genre has no tracks.",
                    modifier = Modifier.fillMaxSize()
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${current.size} song${if (current.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            CloudriveApp.locator.playerController.playQueue(current, 0, sourceLabel = "Genre: $genreName")
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(" Play all")
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(current, key = { it.fileId }) { track ->
                            TrackRow(
                                track = track,
                                onClick = {
                                    val index = current.indexOf(track)
                                    CloudriveApp.locator.playerController.playQueue(current, index, sourceLabel = "Genre: $genreName")
                                },
                                onOverflowClick = { contextTrack = track }
                            )
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
