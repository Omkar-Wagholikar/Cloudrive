package com.example.cloudrive.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.data.local.music.TrackEntity
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileGridSkeleton
import com.example.cloudrive.ui.components.music.ArtworkTile

data class AlbumSummary(val name: String, val artist: String, val representativeTrack: TrackEntity)

@Composable
fun AlbumsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val albums = remember(uiState.tracks) {
        uiState.tracks
            .groupBy { it.album?.takeIf { name -> name.isNotBlank() } ?: "Unknown album" }
            .map { (name, tracks) ->
                val representative = tracks.first()
                AlbumSummary(name = name, artist = representative.displayArtist, representativeTrack = representative)
            }
            .sortedBy { it.name.lowercase() }
    }

    Box(modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> FileGridSkeleton(modifier = Modifier.fillMaxSize())
            albums.isEmpty() -> EmptyState(
                icon = Icons.Default.Album,
                title = "No albums yet",
                body = "Albums will appear here once your library finishes indexing.",
                modifier = Modifier.fillMaxSize()
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(albums, key = { it.name }) { album ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Screen.MusicAlbum.createRoute(album.name)) }
                    ) {
                        ArtworkTile(
                            fileId = album.representativeTrack.fileId,
                            hasArt = album.representativeTrack.hasArt,
                            size = 160.dp
                        )
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = album.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
