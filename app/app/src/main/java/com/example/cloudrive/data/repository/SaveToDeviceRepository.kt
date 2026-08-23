package com.example.cloudrive.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.cloudrive.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads a file's bytes and saves it to the device's shared storage via MediaStore, so
 * images/videos land in Pictures/Cloudrive & Movies/Cloudrive and show up in the Gallery app.
 * Everything else goes to Downloads/Cloudrive.
 */
class SaveToDeviceRepository(
    private val client: OkHttpClient,
    private val fileRepository: FileRepository
) {
    suspend fun saveToDevice(context: Context, file: FileItem): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(fileRepository.downloadUrl(file.id)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed (${response.code})")
                val body = response.body ?: error("Empty response")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    body.byteStream().use { saveViaMediaStore(context, file, it) }
                } else {
                    body.byteStream().use { saveLegacy(context, file, it) }
                }
            }
        }
    }

    private fun saveViaMediaStore(context: Context, file: FileItem, input: java.io.InputStream): Uri {
        val resolver = context.contentResolver
        val (collection, relativePath) = mediaCollectionFor(file.mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.filename)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = insertUnique(resolver, collection, values, file.filename)
        resolver.openOutputStream(uri)?.use { out -> input.copyTo(out) }
            ?: error("Could not open output stream")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Duplicate (relativePath, displayName) pairs make MediaStore.insert throw on API 30+; retry with a unique name. */
    private fun insertUnique(
        resolver: android.content.ContentResolver,
        collection: Uri,
        values: ContentValues,
        originalName: String
    ): Uri {
        return try {
            resolver.insert(collection, values) ?: error("Could not create media entry")
        } catch (e: IllegalStateException) {
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName(originalName))
            resolver.insert(collection, values) ?: error("Could not create media entry")
        }
    }

    private fun saveLegacy(context: Context, file: FileItem, input: java.io.InputStream): Uri {
        val publicDir = legacyPublicDirFor(file.mimeType)
        val dir = File(Environment.getExternalStoragePublicDirectory(publicDir), "Cloudrive").apply { mkdirs() }
        var destFile = File(dir, file.filename)
        if (destFile.exists()) destFile = File(dir, uniqueName(file.filename))
        FileOutputStream(destFile).use { out -> input.copyTo(out) }
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(file.mimeType), null)
        return Uri.fromFile(destFile)
    }

    private fun mediaCollectionFor(mimeType: String): Pair<Uri, String> = when {
        mimeType.startsWith("image/") ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/Cloudrive"
        mimeType.startsWith("video/") ->
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/Cloudrive"
        mimeType.startsWith("audio/") ->
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/Cloudrive"
        else ->
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_DOWNLOADS}/Cloudrive"
    }

    private fun legacyPublicDirFor(mimeType: String): String = when {
        mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
        mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
        mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
        else -> Environment.DIRECTORY_DOWNLOADS
    }

    private fun uniqueName(filename: String): String {
        val dot = filename.lastIndexOf('.')
        val stamp = System.currentTimeMillis()
        return if (dot > 0) "${filename.substring(0, dot)}_$stamp${filename.substring(dot)}" else "${filename}_$stamp"
    }
}
