package com.example.cloudrive.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Contextual top bar shown while in multi-select mode. */
@Composable
fun SelectionActionBar(
    count: Int,
    onCancel: () -> Unit,
    onMove: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDeleteForever: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        onMove?.let {
            IconButton(onClick = it) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
            }
        }
        onRestore?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
            }
        }
        onTrash?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Delete, contentDescription = "Move to trash", tint = MaterialTheme.colorScheme.error)
            }
        }
        onDeleteForever?.let {
            IconButton(onClick = it) {
                Icon(Icons.Default.Delete, contentDescription = "Delete forever", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
