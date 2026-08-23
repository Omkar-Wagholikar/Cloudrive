package com.example.cloudrive.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.StartResumableRequest
import com.example.cloudrive.data.model.UploadResponse
import com.example.cloudrive.data.model.UploadSession
import com.example.cloudrive.data.remote.api.UploadApi
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream

private const val MULTIPART_THRESHOLD = 5 * 1024 * 1024L // 5 MB
private const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB

class UploadRepository(
    private val api: UploadApi,
    private val resolver: ContentResolver
) {
    private val gson = Gson()

    fun fileSize(uri: Uri): Long = queryFileInfo(uri).size

    fun fileName(uri: Uri): String = queryFileInfo(uri).name

    suspend fun listOpenSessions(): Result<List<UploadSession>> = runCatching {
        val r = api.listResumable()
        r.body()?.sessions ?: error(parseError(r.errorBody()?.string()))
    }

    /**
     * Continues an existing resumable session (e.g. rediscovered via [listOpenSessions]
     * after a crash/reinstall) — the caller must re-pick a file, since Android doesn't let
     * us re-read an arbitrary SAF Uri across process death without it.
     */
    suspend fun resumeSession(
        uri: Uri,
        session: UploadSession,
        onProgress: (Float) -> Unit
    ): Result<UploadResponse> = runCatching {
        val info = queryFileInfo(uri)
        if (info.size != session.totalSize) {
            error("Selected file doesn't match the interrupted upload (size differs)")
        }
        sendChunks(session.uploadId, session.totalSize, session.offset, onProgress) {
            resolver.openInputStream(uri)!!
        }
    }

    suspend fun uploadFile(
        uri: Uri,
        folderId: Long?,
        onProgress: (Float) -> Unit
    ): Result<UploadResponse> = runCatching {
        val (fileName, fileSize, mimeType) = queryFileInfo(uri)
        if (fileSize < MULTIPART_THRESHOLD) {
            uploadMultipart(uri, fileName, mimeType, folderId, fileSize, onProgress)
        } else {
            uploadResumable(uri, fileName, mimeType, fileSize, folderId, onProgress)
        }
    }

    private suspend fun uploadMultipart(
        uri: Uri,
        fileName: String,
        mimeType: String,
        folderId: Long?,
        fileSize: Long,
        onProgress: (Float) -> Unit
    ): UploadResponse {
        val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
        onProgress(0.5f)
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", fileName, requestBody)
        val folderPart = folderId?.let {
            it.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        }
        val response = api.uploadMultipart(filePart, folderPart)
        onProgress(1f)
        return response.body() ?: error(parseError(response.errorBody()?.string()))
    }

    private suspend fun uploadResumable(
        uri: Uri,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        folderId: Long?,
        onProgress: (Float) -> Unit
    ): UploadResponse {
        val startResponse = api.startResumable(
            StartResumableRequest(fileName, fileSize, mimeType, folderId)
        )
        if (!startResponse.isSuccessful) error(parseError(startResponse.errorBody()?.string()))
        val uploadId = startResponse.body()!!.uploadId
        val offset = startResponse.body()!!.offset

        return sendChunks(uploadId, fileSize, offset, onProgress) { resolver.openInputStream(uri)!! }
    }

    private suspend fun sendChunks(
        uploadId: String,
        fileSize: Long,
        startOffset: Long,
        onProgress: (Float) -> Unit,
        openStream: () -> InputStream
    ): UploadResponse {
        var offset = startOffset
        openStream().use { stream ->
            stream.skip(offset)
            val buffer = ByteArray(CHUNK_SIZE)
            while (offset < fileSize) {
                val read = stream.read(buffer)
                if (read == -1) break
                val chunk = buffer.copyOf(read)
                val end = offset + read - 1
                val contentRange = "bytes $offset-$end/$fileSize"
                val body = chunk.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val patchResponse = api.patchResumable(uploadId, contentRange, body)
                if (patchResponse.code() == 201) {
                    onProgress(1f)
                    val responseText = patchResponse.body()?.string() ?: error("Empty response")
                    return gson.fromJson(responseText, UploadResponse::class.java)
                }
                if (!patchResponse.isSuccessful) error(parseError(patchResponse.errorBody()?.string()))
                offset += read
                onProgress(offset.toFloat() / fileSize.toFloat())
            }
        }
        error("Upload ended without completion")
    }

    private data class FileInfo(val name: String, val size: Long, val mimeType: String)

    private fun queryFileInfo(uri: Uri): FileInfo {
        var name = "file"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        return FileInfo(name, size, mimeType)
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            val map = gson.fromJson(body, Map::class.java)
            map["error"] as? String ?: body
        } catch (e: Exception) {
            body
        }
    }
}
