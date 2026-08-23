package com.example.cloudrive.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cloudrive.data.model.ShareLinkItem
import com.example.cloudrive.ui.components.EmptyState
import com.example.cloudrive.ui.components.FileListSkeleton
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinksScreen(
    navController: NavController,
    asTab: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ShareLinksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar("Revoke failed: $it") }
    }

    if (asTab) {
        ShareLinksContent(
            uiState = uiState,
            clipboardManager = clipboardManager,
            snackbarHostState = snackbarHostState,
            scope = scope,
            viewModel = viewModel,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Shared Links") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        ShareLinksContent(
            uiState = uiState,
            clipboardManager = clipboardManager,
            snackbarHostState = snackbarHostState,
            scope = scope,
            viewModel = viewModel,
            modifier = modifier.fillMaxSize().padding(padding)
        )
    }
}

@Composable
private fun ShareLinksContent(
    uiState: ShareLinksUiState,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    viewModel: ShareLinksViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (uiState.isLoading) {
            FileListSkeleton(modifier = Modifier.fillMaxSize())
        } else if (uiState.links.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Link,
                title = "No shared links yet",
                body = "Links you create from the Share menu will show up here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn {
                items(uiState.links, key = { it.token }) { link ->
                    ShareLinkRow(
                        link = link,
                        isRevoking = uiState.revokingToken == link.token,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(link.url))
                            scope.launch { snackbarHostState.showSnackbar("Link copied") }
                        },
                        onRevoke = { viewModel.revoke(link.token) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ShareLinkRow(
    link: ShareLinkItem,
    isRevoking: Boolean,
    onCopy: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = link.filename,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = link.expiresAt?.let { "Expires ${formatDate(it)}" } ?: "Never expires",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
        }
        if (isRevoking) {
            Box(Modifier.padding(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRevoke) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = "Revoke link",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun formatDate(isoTimestamp: String): String = try {
    Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).format(dateFormatter)
} catch (e: DateTimeParseException) {
    isoTimestamp
}
