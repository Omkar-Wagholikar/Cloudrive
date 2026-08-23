package com.example.cloudrive.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileListSkeleton

@Composable
fun GenresScreen(navController: NavController, modifier: Modifier = Modifier) {
    var genres by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        CloudriveApp.locator.trackRepository.observeGenres().collect { genres = it.sortedBy { name -> name.lowercase() } }
    }

    Box(modifier.fillMaxSize()) {
        val current = genres
        when {
            current == null -> FileListSkeleton(modifier = Modifier.fillMaxSize())
            current.isEmpty() -> EmptyState(
                icon = Icons.Default.LibraryMusic,
                title = "No genres yet",
                body = "Genres will appear here once your library finishes indexing.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(current, key = { it }) { genre ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.MusicGenre.createRoute(genre)) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
