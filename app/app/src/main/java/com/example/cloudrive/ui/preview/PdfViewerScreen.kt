package com.example.cloudrive.ui.preview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.ui.components.rememberSaveToDeviceAction
import com.example.cloudrive.ui.components.ShareSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * In-app PDF viewer (design brief Previews 5a).
 *
 * PdfRenderer requires a local, seekable ParcelFileDescriptor — it can't stream directly off
 * an HTTP URL — so the file is first downloaded in full to a cache-dir temp file via the
 * shared authenticatedClient (same client Coil/ExoPlayer use, so auth headers carry over).
 * Pages are decoded lazily as their LazyColumn item nears visibility (not eagerly up front)
 * to avoid holding the whole document's bitmaps in memory on large PDFs; a [Mutex] serializes
 * page renders since a single PdfRenderer instance isn't safe for concurrent page access.
 *
 * Nice-to-haves skipped for this pass (noted per spec):
 *  - The page-position chip tracks firstVisibleItemIndex, which is an approximation of "most
 *    visible page" rather than a pixel-exact midpoint calculation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(navController: NavController, file: FileItem) {
    val fileRepo = CloudriveApp.locator.fileRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveToDevice = rememberSaveToDeviceAction(snackbarHostState, scope)

    var tempFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var shareTarget by remember { mutableStateOf<FileItem?>(null) }
    var overflowExpanded by remember { mutableStateOf(false) }
    val renderMutex = remember { Mutex() }

    LaunchedEffect(file.id) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = File(context.cacheDir, "preview_pdf_${file.id}.pdf")
                val request = Request.Builder().url(fileRepo.downloadUrl(file.id)).build()
                CloudriveApp.locator.authenticatedClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed: ${response.code}")
                    val body = response.body ?: error("Empty response body")
                    FileOutputStream(cacheFile).use { out -> body.byteStream().copyTo(out) }
                }
                val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                cacheFile to r
            }
        }
        result.onSuccess { (cacheFile, r) ->
            tempFile = cacheFile
            renderer = r
            pageCount = r.pageCount
        }.onFailure {
            downloadError = it.message ?: "Failed to open PDF"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
            tempFile?.delete()
        }
    }

    val listState = rememberLazyListState()
    val visiblePage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.filename, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (pageCount > 0) "${formatSize(file.size)} · $pageCount pages" else formatSize(file.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareTarget = file }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Open in another app") },
                                onClick = {
                                    overflowExpanded = false
                                    scope.launch { snackbarHostState.showSnackbar("Opening externally isn't implemented yet") }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { saveToDevice(file) },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                text = { Text("Download") }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                downloadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(downloadError ?: "Failed to load PDF", color = MaterialTheme.colorScheme.error)
                }
                renderer == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val activeRenderer = renderer!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        itemsIndexed(items = List(pageCount) { it }, key = { _, page -> page }) { _, pageIndex ->
                            PdfPageCard(
                                renderer = activeRenderer,
                                pageIndex = pageIndex,
                                mutex = renderMutex,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }

                    if (pageCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 88.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${visiblePage.coerceIn(1, pageCount)} / $pageCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }

    shareTarget?.let { target ->
        ShareSheet(
            fileName = target.filename,
            onDismiss = { shareTarget = null },
            onCreateLink = { expiresIn -> fileRepo.shareFile(target.id, expiresIn) },
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
}

@Composable
private fun PdfPageCard(
    renderer: PdfRenderer,
    pageIndex: Int,
    mutex: Mutex,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        val rendered = withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }.getOrNull()
            }
        }
        bitmap = rendered
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
