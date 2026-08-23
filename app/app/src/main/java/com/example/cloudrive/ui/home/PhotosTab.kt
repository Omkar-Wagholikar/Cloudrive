package com.example.cloudrive.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.navigation.PreviewContext
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.ui.components.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Client-side filter for the Photos tab (design brief 4b). Distinct from
 * [com.example.cloudrive.ui.components.FileTypeFilter] (used by My Drive) since
 * "Screenshots" isn't a mime type — it's a filename heuristic.
 */
enum class PhotosFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots")
}

private fun PhotosFilter.apiType(): String? = when (this) {
    PhotosFilter.ALL -> null
    PhotosFilter.VIDEOS -> "video"
    PhotosFilter.SCREENSHOTS -> null
}

private fun isScreenshot(file: FileItem): Boolean =
    file.filename.startsWith("Screenshot", ignoreCase = true)

class PhotosViewModel(app: Application) : AndroidViewModel(app) {
    private val locator = CloudriveApp.locator
    private val _photos = MutableStateFlow<List<FileItem>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _filter = MutableStateFlow(PhotosFilter.ALL)
    val photos: StateFlow<List<FileItem>> = _photos
    val loading: StateFlow<Boolean> = _loading
    val filter: StateFlow<PhotosFilter> = _filter

    init { load() }

    fun setFilter(filter: PhotosFilter) {
        _filter.value = filter
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            fetchAndApply()
            _loading.value = false
        }
    }

    /** Silent refresh (no loading spinner) used to poll for thumbnails that were still processing. */
    fun refreshQuietly() {
        viewModelScope.launch { fetchAndApply() }
    }

    private suspend fun fetchAndApply() {
        val filter = _filter.value
        if (filter == PhotosFilter.VIDEOS) {
            // The server's /thumbnails endpoint only returns rows with a ready thumbnail,
            // which silently hides videos still awaiting (or missing) thumbnail generation.
            // Source from /files instead — same as the Files tab — so every video shows up
            // regardless of thumbReady, falling back to a placeholder tile like Files does.
            _photos.value = fetchAllVideos()
            return
        }
        locator.fileRepository.listThumbnails(type = filter.apiType()).onSuccess {
            _photos.value = if (filter == PhotosFilter.SCREENSHOTS) {
                it.items.filter(::isScreenshot)
            } else {
                it.items
            }
        }
    }

    private suspend fun fetchAllVideos(): List<FileItem> {
        val videos = mutableListOf<FileItem>()
        var page = 1
        while (true) {
            val body = locator.fileRepository.listFiles(page = page, limit = PAGE_SIZE).getOrNull() ?: break
            videos += body.items.filter { it.mimeType.startsWith("video/") }
            if (body.items.size < PAGE_SIZE || page * PAGE_SIZE >= body.total) break
            page++
        }
        return videos
    }

    companion object {
        private const val PAGE_SIZE = 100
    }
}

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val monthKeyFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

private fun monthKey(isoTimestamp: String): String = try {
    Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).format(monthKeyFormatter)
} catch (e: DateTimeParseException) {
    "unknown"
}

private fun monthLabel(isoTimestamp: String): String = try {
    Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).format(monthFormatter)
} catch (e: DateTimeParseException) {
    "Unknown"
}

@Composable
fun PhotosTab(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: PhotosViewModel = viewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val context = LocalContext.current
    val fileRepo = CloudriveApp.locator.fileRepository

    // Column density: default 4, cycled via the toolbar toggle. A true pinch-to-zoom gesture
    // would match the spec more closely but a density toggle button is a simpler, equally
    // usable fallback for this pass.
    val densities = listOf(2, 4, 6)
    var densityIndex by remember { mutableStateOf(1) }
    val columns = densities[densityIndex]

    // Poll once, a few seconds after load, if any tile's thumbnail is still processing
    // (thumbReady == 0), then refresh so pending tiles can swap to the real thumbnail.
    LaunchedEffect(photos) {
        if (photos.any { it.thumbReady == 0 }) {
            delay(5000)
            viewModel.refreshQuietly()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhotosFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
                IconButton(onClick = { densityIndex = (densityIndex + 1) % densities.size }) {
                    Icon(Icons.Default.GridView, contentDescription = "Change grid density")
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else if (photos.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PhotoLibrary,
                    title = "No photos yet",
                    body = "Photos and videos you upload will show up here.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val grouped = photos
                    .sortedByDescending { it.createdAt }
                    .groupBy { monthKey(it.createdAt) }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    grouped.forEach { (key, filesInMonth) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header_$key") {
                            Text(
                                text = monthLabel(filesInMonth.first().createdAt),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                        items(filesInMonth, key = { it.id }) { file ->
                            PhotoTile(
                                file = file,
                                thumbnailUrl = fileRepo.thumbnailUrl(file.id),
                                onClick = {
                                    PreviewContext.siblingImages = photos.filter { it.mimeType.startsWith("image/") }
                                    navController.navigate(Screen.Preview.createRoute(file.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(file: FileItem, thumbnailUrl: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val isVideo = file.mimeType.startsWith("video/")

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        if (file.thumbReady == 1) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbnailUrl).build(),
                contentDescription = file.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // thumbReady == 0 (pending) or 2 (failed): show a placeholder tile instead of
            // attempting the Coil load. Pending tiles get picked up by the tab-level poll
            // in PhotosTab's LaunchedEffect once the server finishes processing.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = if (file.thumbReady == 0) "Thumbnail processing" else null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
