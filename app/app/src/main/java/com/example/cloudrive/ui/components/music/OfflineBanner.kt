package com.example.cloudrive.ui.components.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cloudrive.CloudriveApp

/**
 * Slim banner shown across library screens when the device has no network connectivity,
 * to make clear that only downloaded tracks are available. Composes to nothing while online.
 * Self-contained: reads [com.example.cloudrive.playback.NetworkMonitor] itself, no params needed.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val isOnline by CloudriveApp.locator.networkMonitor.isOnline.collectAsState()
    if (isOnline) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "You're offline — showing downloaded tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
