package com.example.cloudrive.ui.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.music.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSearchScreen(
    navController: NavController,
    viewModel: MusicSearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var contextTrack by remember { mutableStateOf<TrackEntity?>(null) }

    contextTrack?.let { track ->
        TrackContextSheet(track = track, navController = navController, onDismiss = { contextTrack = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search songs, artists, albums") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.query.isBlank() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Search your music",
                    body = "Find songs, artists, and albums in your library.",
                    modifier = Modifier.fillMaxSize()
                )
                uiState.results.isEmpty() && !uiState.isSearching -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "No results",
                    body = "No tracks matched \"${uiState.query}\".",
                    modifier = Modifier.fillMaxSize()
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(uiState.results, key = { it.fileId }) { track ->
                        TrackRow(
                            track = track,
                            onClick = {
                                val index = uiState.results.indexOf(track)
                                CloudriveApp.locator.playerController.playQueue(uiState.results, index, sourceLabel = "Search")
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
