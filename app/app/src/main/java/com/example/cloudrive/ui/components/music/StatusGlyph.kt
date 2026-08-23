package com.example.cloudrive.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.cloudrive.data.local.music.TrackEntity

/**
 * Small glyph reflecting a track's tag-extraction status: nothing for OK, a "no tags" icon
 * for untagged files, a warning for failed/unsupported extraction, and a pending dot while
 * indexing is in progress.
 */
@Composable
fun StatusGlyph(tagStatus: Int, modifier: Modifier = Modifier) {
    when (tagStatus) {
        TrackEntity.TAG_STATUS_OK -> Unit
        TrackEntity.TAG_STATUS_NO_TAGS -> Icon(
            imageVector = Icons.Default.LabelOff,
            contentDescription = "No tags",
            modifier = modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TrackEntity.TAG_STATUS_FAILED, TrackEntity.TAG_STATUS_UNSUPPORTED -> Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Tag extraction failed",
            modifier = modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error
        )
        TrackEntity.TAG_STATUS_PENDING -> androidx.compose.foundation.layout.Box(
            modifier = modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        else -> Unit
    }
}
