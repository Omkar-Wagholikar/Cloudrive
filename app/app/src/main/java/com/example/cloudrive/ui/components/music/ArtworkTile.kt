package com.example.cloudrive.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.cloudrive.CloudriveApp
import kotlin.math.abs

/**
 * Artwork thumbnail for a track/album. Falls back to a deterministic gradient derived from
 * [fileId]'s hash when [hasArt] is false, so the placeholder is stable across recompositions
 * rather than random.
 */
@Composable
fun ArtworkTile(
    fileId: Long,
    hasArt: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (hasArt) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(CloudriveApp.locator.trackRepository.artworkUrl(fileId, size = size.value.toInt().coerceAtLeast(1)))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        val brush = gradientForHash(fileId.hashCode())
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun gradientForHash(hash: Int): Brush {
    val palette = listOf(
        Color(0xFFE9B44C), // brass accent
        Color(0xFF6B5B95),
        Color(0xFF2E86AB),
        Color(0xFF5C8A5C),
        Color(0xFFB4534A),
        Color(0xFF4A6FA5)
    )
    val h = abs(hash)
    val colorA = palette[h % palette.size]
    val colorB = palette[(h / palette.size) % palette.size]
    val start = if (colorA == colorB) colorA else colorA
    val end = if (colorA == colorB) lerp(colorA, MaterialTheme.colorScheme.surface, 0.5f) else colorB
    return Brush.linearGradient(listOf(start, end))
}
