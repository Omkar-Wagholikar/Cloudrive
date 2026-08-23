package com.example.cloudrive.ui.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.navigation.PreviewContext

/**
 * Dispatcher for every "open a file" action in the app (design brief: Previews 6a-6c,
 * replaces the old blanket ACTION_VIEW handoff). Resolves the tapped file — preferring the
 * sibling list the calling screen was already displaying (see [PreviewContext]) so we don't
 * re-fetch, falling back to a direct lookup for screens that don't populate it (Search,
 * deep links, etc.) — then branches on mime type.
 *
 * Only the image branch is implemented in this pass; everything else still falls back to
 * the legacy ACTION_VIEW handoff so a follow-on task can drop in real video/audio/pdf
 * viewers by adding branches here, without touching call sites.
 */
@Composable
fun PreviewScreen(navController: NavController, fileId: Long) {
    val fileRepo = CloudriveApp.locator.fileRepository
    val context = LocalContext.current

    var resolvedFile by remember { mutableStateOf<FileItem?>(null) }
    var siblingImages by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var notFound by remember { mutableStateOf(false) }

    LaunchedEffect(fileId) {
        val fromSiblings = PreviewContext.siblingImages.firstOrNull { it.id == fileId }
        if (fromSiblings != null) {
            resolvedFile = fromSiblings
            siblingImages = PreviewContext.siblingImages
        } else {
            // Not in the sibling set the caller had loaded (e.g. navigated in from Search
            // or Trash) — fetch it directly and treat it as a single-item pager.
            fileRepo.getFile(fileId).onSuccess { info ->
                resolvedFile = FileItem(
                    id = info.id,
                    filename = info.filename,
                    size = info.size,
                    mimeType = info.mimeType,
                    folderId = info.folderId,
                    thumbReady = info.thumbReady,
                    createdAt = info.createdAt,
                    deletedAt = info.deletedAt,
                    width = null,
                    height = null
                )
                siblingImages = listOf(resolvedFile!!)
            }.onFailure {
                notFound = true
            }
        }
    }

    val file = resolvedFile
    when {
        notFound -> LaunchedEffect(Unit) { navController.popBackStack() }
        file == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        file.mimeType.startsWith("image/") -> {
            val images = siblingImages.filter { it.mimeType.startsWith("image/") }
                .ifEmpty { listOf(file) }
            ImageViewerScreen(
                navController = navController,
                images = images,
                initialFileId = file.id
            )
        }
        file.mimeType.startsWith("video/") -> VideoPlayerScreen(navController = navController, file = file)
        file.mimeType.startsWith("audio/") -> AudioPlayerScreen(navController = navController, file = file)
        file.mimeType == "application/pdf" -> PdfViewerScreen(navController = navController, file = file)
        else -> {
            // Everything else: legacy ACTION_VIEW handoff for now.
            LaunchedEffect(file.id) {
                val url = fileRepo.downloadUrl(file.id)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                navController.popBackStack()
            }
        }
    }
}
