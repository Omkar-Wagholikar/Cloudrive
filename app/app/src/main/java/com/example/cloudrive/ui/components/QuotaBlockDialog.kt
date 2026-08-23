package com.example.cloudrive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Pre-upload quota-block dialog (design brief 3f): warning row, dual-color quota bar
 * (used vs. would-be overflow), byte counts, and recovery actions.
 *
 * The M3 [androidx.compose.material3.LinearProgressIndicator] doesn't support a second
 * overflow segment, so the bar is a simple weighted [Row] of colored boxes instead of a
 * custom Canvas — simplest correct approach for a three-way split (used / overflow / free).
 *
 * "Free up space" just calls [onDismiss] — the real quota-freeing flow (e.g. picking
 * large files to delete) is out of scope for this pass. "Open Trash" navigates to the
 * one concrete space-freeing surface that already exists.
 */
@Composable
fun QuotaBlockDialog(
    usedBytes: Long,
    quotaBytes: Long,
    neededBytes: Long,
    onOpenTrash: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Not enough storage") },
        text = {
            Column {
                Text(
                    "This upload needs ${formatSize(neededBytes)}, but you only have " +
                        "${formatSize((quotaBytes - usedBytes).coerceAtLeast(0))} free.",
                    style = MaterialTheme.typography.bodyMedium
                )
                QuotaBar(
                    usedBytes = usedBytes,
                    quotaBytes = quotaBytes,
                    neededBytes = neededBytes,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    "${formatSize(usedBytes)} used · ${formatSize(quotaBytes)} quota · " +
                        "${formatSize(neededBytes)} needed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenTrash) { Text("Open Trash") }
        },
        dismissButton = {
            // Real quota-freeing flow (e.g. picking large files to delete) is out of
            // scope for this pass — just dismiss and let the user manage manually.
            TextButton(onClick = onDismiss) { Text("Free up space") }
        }
    )
}

@Composable
private fun QuotaBar(
    usedBytes: Long,
    quotaBytes: Long,
    neededBytes: Long,
    modifier: Modifier = Modifier
) {
    val total = (quotaBytes.coerceAtLeast(0) + neededBytes.coerceAtLeast(0)).coerceAtLeast(1)
    val usedWeight = (usedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val overflowWeight = (neededBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val freeWeight = (1f - usedWeight - overflowWeight).coerceAtLeast(0f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
    ) {
        if (usedWeight > 0f) {
            Box(
                Modifier
                    .weight(usedWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        if (overflowWeight > 0f) {
            Box(
                Modifier
                    .weight(overflowWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            )
        }
        if (freeWeight > 0f) {
            Box(
                Modifier
                    .weight(freeWeight)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
