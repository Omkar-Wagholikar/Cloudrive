package com.example.cloudrive.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.Folder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovePickerSheet(
    fileName: String,
    excludeFolderId: Long? = null,
    onDismiss: () -> Unit,
    onMove: (destinationFolderId: Long?) -> Unit
) {
    val folderRepository = CloudriveApp.locator.folderRepository
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentFolderId by remember { mutableStateOf<Long?>(null) }
    var breadcrumb by remember { mutableStateOf(listOf<Pair<Long?, String>>(null to "My Drive")) }
    var subfolders by remember { mutableStateOf(listOf<Folder>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentFolderId) {
        isLoading = true
        val id = currentFolderId
        subfolders = if (id == null) {
            folderRepository.getRootFolders().getOrDefault(emptyList())
        } else {
            folderRepository.getFolderContents(id).getOrNull()?.subfolders ?: emptyList()
        }
        subfolders = subfolders.filter { it.id != excludeFolderId }
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Move \"$fileName\" to…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                breadcrumb.forEachIndexed { index, (id, name) ->
                    if (index > 0) Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.height(18.dp))
                    TextButton(onClick = {
                        currentFolderId = id
                        breadcrumb = breadcrumb.take(index + 1)
                    }) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            HorizontalDivider()
            Box(Modifier.height(280.dp)) {
                when {
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    subfolders.isEmpty() -> Text(
                        "No subfolders",
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn {
                        items(subfolders, key = { it.id }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = folder.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = {
                                    currentFolderId = folder.id
                                    breadcrumb = breadcrumb + (folder.id to folder.name)
                                }) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = { onMove(currentFolderId) }) {
                    Text("Move here")
                }
            }
        }
    }
}
