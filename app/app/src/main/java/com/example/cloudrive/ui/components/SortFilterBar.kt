package com.example.cloudrive.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cloudrive.data.model.FileItem
import java.time.Instant

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
    TYPE("Type")
}

enum class FileTypeFilter(val label: String) {
    ALL("All"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    OTHER("Other")
}

fun FileTypeFilter.matches(mimeType: String): Boolean = when (this) {
    FileTypeFilter.ALL -> true
    FileTypeFilter.IMAGE -> mimeType.startsWith("image/")
    FileTypeFilter.VIDEO -> mimeType.startsWith("video/")
    FileTypeFilter.AUDIO -> mimeType.startsWith("audio/")
    FileTypeFilter.DOCUMENT ->
        mimeType == "application/pdf" || mimeType.startsWith("text/") || mimeType.contains("document")
    FileTypeFilter.OTHER ->
        !mimeType.startsWith("image/") && !mimeType.startsWith("video/") &&
            !mimeType.startsWith("audio/") && mimeType != "application/pdf" &&
            !mimeType.startsWith("text/") && !mimeType.contains("document")
}

fun List<FileItem>.sortedAndFiltered(sort: SortOption, filter: FileTypeFilter): List<FileItem> =
    filter { filter.matches(it.mimeType) }
        .let { list ->
            when (sort) {
                SortOption.NAME -> list.sortedBy { it.filename.lowercase() }
                SortOption.DATE -> list.sortedByDescending {
                    runCatching { Instant.parse(it.createdAt) }.getOrDefault(Instant.EPOCH)
                }
                SortOption.SIZE -> list.sortedByDescending { it.size }
                SortOption.TYPE -> list.sortedBy { it.mimeType }
            }
        }

@Composable
fun SortFilterBar(
    sort: SortOption,
    onSortChange: (SortOption) -> Unit,
    filter: FileTypeFilter,
    onFilterChange: (FileTypeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }

    Row(modifier.padding(horizontal = 8.dp)) {
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { sortExpanded = true }) {
                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(sort.label, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                SortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { sortExpanded = false; onSortChange(option) }
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { filterExpanded = true }) {
                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(filter.label, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                FileTypeFilter.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { filterExpanded = false; onFilterChange(option) }
                    )
                }
            }
        }
    }
}
