package com.example.cloudrive.data.repository

import com.example.cloudrive.data.local.TokenStore
import com.example.cloudrive.data.model.BatchFilesRequest
import com.example.cloudrive.data.model.BatchFilesResponse
import com.example.cloudrive.data.model.FileInfo
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.FileList
import com.example.cloudrive.data.model.RenameFileRequest
import com.example.cloudrive.data.model.ShareLinkItem
import com.example.cloudrive.data.model.ShareRequest
import com.example.cloudrive.data.model.ShareToken
import com.example.cloudrive.data.remote.LanResolver
import com.example.cloudrive.data.remote.api.FileApi
import com.google.gson.Gson

class FileRepository(
    private val api: FileApi,
    private val tokenStore: TokenStore,
    private val lanResolver: LanResolver
) {
    private val gson = Gson()

    suspend fun listFiles(page: Int = 1, limit: Int = 50): Result<FileList> = runCatching {
        val r = api.listFiles(page, limit)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun searchFiles(query: String, page: Int = 1): Result<FileList> = runCatching {
        val r = api.searchFiles(query, page)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun getFile(id: Long): Result<FileInfo> = runCatching {
        val r = api.getFile(id)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun renameFile(id: Long, newName: String): Result<FileItem> = runCatching {
        val r = api.patchFile(id, RenameFileRequest(filename = newName, folderId = null))
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun moveFile(id: Long, folderId: Long?): Result<FileItem> = runCatching {
        val r = api.patchFile(id, RenameFileRequest(filename = null, folderId = folderId))
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun deleteFile(id: Long): Result<Unit> = runCatching {
        val r = api.deleteFile(id)
        if (!r.isSuccessful) error(parseError(r.errorBody()?.string()))
    }

    suspend fun shareFile(id: Long, expiresInSeconds: Int? = null): Result<ShareToken> = runCatching {
        val r = api.shareFile(id, ShareRequest(expiresInSeconds))
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun listShares(): Result<List<ShareLinkItem>> = runCatching {
        val r = api.listShares()
        r.body()?.links ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun revokeShare(token: String): Result<Unit> = runCatching {
        val r = api.revokeShare(token)
        if (!r.isSuccessful) error(parseError(r.errorBody()?.string()))
    }

    suspend fun batchMove(ids: List<Long>, folderId: Long?): Result<BatchFilesResponse> =
        batch("move", ids, folderId)

    suspend fun batchTrash(ids: List<Long>): Result<BatchFilesResponse> = batch("trash", ids)

    suspend fun batchRestore(ids: List<Long>): Result<BatchFilesResponse> = batch("restore", ids)

    suspend fun batchDelete(ids: List<Long>): Result<BatchFilesResponse> = batch("delete", ids)

    private suspend fun batch(op: String, ids: List<Long>, folderId: Long? = null): Result<BatchFilesResponse> =
        runCatching {
            val r = api.batchFiles(BatchFilesRequest(op, ids, folderId))
            r.body() ?: error(parseError(r.errorBody()?.string()))
        }

    suspend fun listThumbnails(page: Int = 1, type: String? = null): Result<FileList> = runCatching {
        val r = api.listThumbnails(page, type = type)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    fun downloadUrl(id: Long): String = "${lanResolver.currentBaseUrl()}/files/$id/download"

    fun thumbnailUrl(id: Long): String = "${lanResolver.currentBaseUrl()}/files/$id/thumbnail"

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
