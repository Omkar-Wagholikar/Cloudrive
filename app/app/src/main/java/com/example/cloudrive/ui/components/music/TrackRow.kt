package com.example.cloudrive.ui.components.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.local.music.DownloadEntity
import com.example.cloudrive.data.local.music.TrackEntity
import java.util.concurrent.TimeUnit

/**
 * Reusable track row: artwork thumbnail, title/artist, duration, and an overflow menu.
 * Shows [EqBars] instead of duration text when this track is the one currently playing.
 */
@Composable
fun TrackRow(
    track: TrackEntity,
    onClick: () -> Unit,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadState: DownloadEntity? = null,
    dimmed: Boolean = false
) {
    val playbackState by CloudriveApp.locator.playerController.uiState.collectAsState()
    val isCurrent = playbackState.currentTrack?.fileId == track.fileId

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.4f else 1f)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkTile(fileId = track.fileId, hasArt = track.hasArt, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusGlyph(tagStatus = track.tagStatus, modifier = Modifier.padding(start = 4.dp))
            }
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isCurrent) {
            EqBars(isPlaying = playbackState.isPlaying)
            Spacer(Modifier.width(8.dp))
        } else {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (downloadState != null) {
            DownloadStateGlyph(downloadState, modifier = Modifier.padding(end = 4.dp))
        }
        IconButton(onClick = onOverflowClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
    }
}

/** Small glyph reflecting a track's [DownloadEntity.State]: cloud icon, progress ring, check, or error. */
@Composable
private fun DownloadStateGlyph(download: DownloadEntity, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (download.state) {
            DownloadEntity.State.DONE -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            DownloadEntity.State.RUNNING -> {
                val fraction = if (download.bytesTotal > 0) {
                    (download.bytesDone.toFloat() / download.bytesTotal.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
            DownloadEntity.State.PENDING, DownloadEntity.State.PAUSED -> Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Download pending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            DownloadEntity.State.FAILED -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Download failed",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            else -> Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Not downloaded",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
