package com.example.cloudrive.ui.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.ShareLinkItem
import com.example.cloudrive.navigation.isReducedMotionEnabled
import com.example.cloudrive.ui.components.MovePickerSheet
import com.example.cloudrive.ui.components.rememberSaveToDeviceAction
import com.example.cloudrive.ui.components.ShareSheet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.max

/**
 * In-app image viewer (design brief Previews 6a-6c).
 *
 * Nice-to-haves skipped for this pass (noted per spec):
 *  - Filmstrip thumbnail strip along the bottom.
 *  - Swipe-down-to-dismiss with background alpha tracking drag distance.
 *  - True immersive mode (WindowInsetsController hiding system bars) — only the in-app
 *    chrome (top/bottom bars, position chip) fades; system status/nav bars are untouched.
 *  - Progressive full-resolution loading with a "Loading full resolution over LAN" pill —
 *    Coil just loads the full image directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    navController: NavController,
    images: List<FileItem>,
    initialFileId: Long
) {
    val fileRepo = CloudriveApp.locator.fileRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveToDevice = rememberSaveToDeviceAction(snackbarHostState, scope)

    var currentImages by remember { mutableStateOf(images) }
    val startIndex = max(0, currentImages.indexOfFirst { it.id == initialFileId })
    val pagerState = rememberPagerState(initialPage = startIndex) { currentImages.size }

    var chromeVisible by remember { mutableStateOf(true) }
    var moveTarget by remember { mutableStateOf<FileItem?>(null) }
    var shareTarget by remember { mutableStateOf<FileItem?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }

    // If the pager runs out of pages (e.g. last remaining image was deleted), leave the viewer.
    LaunchedEffect(currentImages) {
        if (currentImages.isEmpty()) navController.popBackStack()
    }

    // Container-transform substitute (design brief: photo tile -> viewer). A true shared-element
    // morph from the grid tile needs SharedTransitionLayout to wrap a common ancestor of
    // PhotosTab and this screen, which live under separate NavHost destinations — wiring that up
    // would mean threading SharedTransitionScope through HomeScreen's tab switcher and the
    // NavGraph for a single call site, which is a disproportionate amount of restructuring for
    // this pass. This scale/fade entrance reads as "the image opening up" without it.
    val reducedMotion = isReducedMotionEnabled(context)
    val entranceProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceProgress.animateTo(1f, animationSpec = tween(300, easing = FastOutSlowInEasing))
    }

    fun deleteCurrent(file: FileItem) {
        scope.launch {
            fileRepo.deleteFile(file.id).onSuccess {
                currentImages = currentImages.filter { it.id != file.id }
                val result = snackbarHostState.showSnackbar(
                    message = "Moved to trash",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    CloudriveApp.locator.trashRepository.restore(file.id)
                }
            }.onFailure {
                snackbarHostState.showSnackbar("Failed to delete")
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    alpha = entranceProgress.value
                    if (!reducedMotion) {
                        val scale = 0.9f + 0.1f * entranceProgress.value
                        scaleX = scale
                        scaleY = scale
                    }
                }
        ) {
            if (currentImages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val file = currentImages.getOrNull(page) ?: return@HorizontalPager
                    ZoomableImage(
                        url = fileRepo.downloadUrl(file.id),
                        contentDescription = file.filename,
                        onTap = { chromeVisible = !chromeVisible }
                    )
                }
            }

            val currentFile = currentImages.getOrNull(pagerState.currentPage.coerceIn(0, max(0, currentImages.size - 1)))

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PreviewTopBar(
                    file = currentFile,
                    onBack = { navController.popBackStack() },
                    overflowExpanded = overflowExpanded,
                    onOverflowToggle = { overflowExpanded = it },
                    onDetails = { currentFile?.let { detailsTarget = it } },
                    onShare = { currentFile?.let { shareTarget = it } },
                    onDelete = { currentFile?.let { deleteCurrent(it) } }
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PreviewBottomBar(
                    onShare = { currentFile?.let { shareTarget = it } },
                    onSave = { currentFile?.let { saveToDevice(it) } },
                    onMove = { currentFile?.let { moveTarget = it } },
                    onDetails = { currentFile?.let { detailsTarget = it } },
                    onDelete = { currentFile?.let { deleteCurrent(it) } }
                )
            }

            if (chromeVisible && currentImages.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${currentImages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    moveTarget?.let { file ->
        MovePickerSheet(
            fileName = file.filename,
            onDismiss = { moveTarget = null },
            onMove = { destinationFolderId ->
                scope.launch {
                    fileRepo.moveFile(file.id, destinationFolderId)
                    snackbarHostState.showSnackbar("Moved \"${file.filename}\"")
                }
                moveTarget = null
            }
        )
    }

    shareTarget?.let { file ->
        ShareSheet(
            fileName = file.filename,
            onDismiss = { shareTarget = null },
            onCreateLink = { expiresIn -> fileRepo.shareFile(file.id, expiresIn) },
            onShareVia = { url ->
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, url)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                shareTarget = null
            }
        )
    }

    detailsTarget?.let { file ->
        DetailsSheet(file = file, onDismiss = { detailsTarget = null })
    }
}

@Composable
private fun ZoomableImage(url: String, contentDescription: String?, onTap: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offsetX = if (newScale == 1f) 0f else offsetX + pan.x
                    offsetY = if (newScale == 1f) 0f else offsetY + pan.y
                }
            }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2f
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun PreviewTopBar(
    file: FileItem?,
    onBack: () -> Unit,
    overflowExpanded: Boolean,
    onOverflowToggle: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = file?.filename ?: "",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file != null) {
                Text(
                    text = fileSubtitle(file),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Box {
            IconButton(onClick = { onOverflowToggle(true) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { onOverflowToggle(false) }) {
                DropdownMenuItem(text = { Text("Details") }, onClick = { onOverflowToggle(false); onDetails() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { onOverflowToggle(false); onShare() })
                DropdownMenuItem(text = { Text("Move to Trash") }, onClick = { onOverflowToggle(false); onDelete() })
            }
        }
    }
}

@Composable
private fun PreviewBottomBar(
    onShare: () -> Unit,
    onSave: () -> Unit,
    onMove: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomBarAction(Icons.Default.Share, "Share", onShare)
        BottomBarAction(Icons.Default.Save, "Save", onSave)
        BottomBarAction(Icons.Default.DriveFileMove, "Move", onMove)
        BottomBarAction(Icons.Default.Info, "Details", onDetails)
        BottomBarAction(Icons.Default.Delete, "Delete", onDelete)
    }
}

@Composable
private fun BottomBarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(file: FileItem, onDismiss: () -> Unit) {
    val fileRepo = CloudriveApp.locator.fileRepository
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var activeShare by remember(file.id) { mutableStateOf<ShareLinkItem?>(null) }
    var loadedShares by remember(file.id) { mutableStateOf(false) }

    LaunchedEffect(file.id) {
        fileRepo.listShares().onSuccess { links ->
            activeShare = links.firstOrNull { it.fileId == file.id }
        }
        loadedShares = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
            Text(text = file.filename, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            DetailRow("Uploaded", formatFullDate(file.createdAt))
            DetailRow("Size", sizeAndDimensions(file))
            DetailRow("Type", file.mimeType)
            // Parent folder: FileItem.folderId only gives us an id, not a resolvable path
            // without an extra folder-tree fetch, so it's omitted here rather than guessed.
            if (!loadedShares) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
            } else if (activeShare != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Shared link active",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        val token = activeShare?.token ?: return@TextButton
                        scope.launch {
                            fileRepo.revokeShare(token)
                            activeShare = null
                        }
                    }) {
                        Text("Revoke")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun fileSubtitle(file: FileItem): String {
    val date = formatShortDate(file.createdAt)
    val size = formatSize(file.size)
    val dims = dimensionsOrNull(file)
    return if (dims != null) "$date · $size · $dims" else "$date · $size"
}

private fun sizeAndDimensions(file: FileItem): String {
    val dims = dimensionsOrNull(file)
    return if (dims != null) "$dims · ${formatSize(file.size)}" else formatSize(file.size)
}

private fun dimensionsOrNull(file: FileItem): String? {
    val w = file.width
    val h = file.height
    return if (w != null && h != null) "${w}×${h}" else null
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

private fun formatShortDate(isoTimestamp: String): String = try {
    Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).format(shortDateFormatter)
} catch (e: DateTimeParseException) {
    isoTimestamp
}

private fun formatFullDate(isoTimestamp: String): String = try {
    Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).format(fullDateFormatter)
} catch (e: DateTimeParseException) {
    isoTimestamp
}
