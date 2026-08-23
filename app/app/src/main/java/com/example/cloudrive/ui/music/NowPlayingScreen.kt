package com.example.cloudrive.ui.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.ui.components.music.ArtworkTile
import com.example.cloudrive.ui.components.music.formatDuration

private val speedOptions = listOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f)

/**
 * Full-screen player. Reads [com.example.cloudrive.playback.PlayerController.uiState] directly
 * (no dedicated ViewModel), matching the codebase convention for thin screens over a shared
 * singleton controller.
 */
@Composable
fun NowPlayingScreen(navController: NavController) {
    val uiState by CloudriveApp.locator.playerController.uiState.collectAsState()
    var showQueueSheet by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    val track = uiState.currentTrack

    // Lightweight entrance choreography for the mini-player -> full-player transition: artwork
    // leads, then title/artist, then the transport controls, each fading + scaling in with a
    // slight stagger so the screen feels considered rather than just appearing. A true
    // shared-element morph from the MiniPlayer would need SharedTransitionScope threaded through
    // NavGraph.kt, which is out of scope here (see report) — this is a self-contained polish of
    // this screen's own first composition instead.
    val artworkAnim = remember { Animatable(0f) }
    val textAnim = remember { Animatable(0f) }
    val controlsAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        artworkAnim.animateTo(1f, tween(durationMillis = 420))
    }
    LaunchedEffect(Unit) {
        delay(90)
        textAnim.animateTo(1f, tween(durationMillis = 360))
    }
    LaunchedEffect(Unit) {
        delay(160)
        controlsAnim.animateTo(1f, tween(durationMillis = 360))
    }

    if (showQueueSheet) {
        QueueSheet(onDismiss = { showQueueSheet = false })
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
            }
            Text("Now Playing", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showQueueSheet = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Queue")
            }
        }

        Spacer(Modifier.height(32.dp))

        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing is playing", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = artworkAnim.value
                    val scale = 0.92f + 0.08f * artworkAnim.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            ArtworkTile(fileId = track.fileId, hasArt = track.hasArt, size = 300.dp)
        }

        Spacer(Modifier.height(32.dp))

        Column(
            Modifier
                .graphicsLayer {
                    alpha = textAnim.value
                    translationY = (1f - textAnim.value) * 16f
                }
        ) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        val durationMs = uiState.durationMs.coerceAtLeast(1)
        val sliderValue = scrubPosition ?: (uiState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
            value = sliderValue,
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                val target = scrubPosition
                if (target != null) {
                    CloudriveApp.locator.playerController.seekTo((target * durationMs).toLong())
                }
                scrubPosition = null
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration((sliderValue * durationMs).toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatDuration(uiState.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = controlsAnim.value
                    translationY = (1f - controlsAnim.value) * 16f
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { CloudriveApp.locator.playerController.setShuffle(!uiState.shuffleOn) }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { CloudriveApp.locator.playerController.skipPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.height(36.dp))
            }
            IconButton(
                onClick = { CloudriveApp.locator.playerController.playPause() },
                modifier = Modifier.height(64.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.height(56.dp)
                )
            }
            IconButton(onClick = { CloudriveApp.locator.playerController.skipNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.height(36.dp))
            }
            IconButton(onClick = { CloudriveApp.locator.playerController.cycleRepeatMode() }) {
                Icon(
                    imageVector = if (uiState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speedOptions.forEach { speed ->
                FilterChip(
                    selected = uiState.playbackSpeed == speed,
                    onClick = { CloudriveApp.locator.playerController.setSpeed(speed) },
                    label = { Text("${speed}x") }
                )
            }
        }
    }
}
