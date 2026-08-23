package com.example.cloudrive.ui.music

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private enum class MusicSection(val label: String) {
    HOME("Home"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    PLAYLISTS("Playlists")
}

/**
 * Entry point for the Music tab. Owns its own header (title + search) rather than sharing
 * HomeScreen's TopAppBar, per design — Music is art-forward and wants full-bleed control
 * over its top area.
 */
@Composable
fun MusicTab(navController: NavController, modifier: Modifier = Modifier) {
    var section by remember { mutableStateOf(MusicSection.HOME) }

    LaunchedEffect(Unit) {
        CloudriveApp.locator.trackRepository.sync()
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Music", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { navController.navigate(Screen.MusicSearch.route) }) {
                Icon(Icons.Default.Search, contentDescription = "Search music")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            MusicSection.entries.forEach { option ->
                FilterChip(
                    selected = section == option,
                    onClick = { section = option },
                    label = { Text(option.label) }
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
            when (section) {
                MusicSection.HOME -> MusicHomeScreen(navController = navController)
                MusicSection.SONGS -> SongsScreen(navController = navController)
                MusicSection.ALBUMS -> AlbumsScreen(navController = navController)
                MusicSection.ARTISTS -> ArtistsScreen(navController = navController)
                MusicSection.GENRES -> GenresScreen(navController = navController)
                MusicSection.PLAYLISTS -> PlaylistsScreen(navController = navController)
            }
        }
    }
}
