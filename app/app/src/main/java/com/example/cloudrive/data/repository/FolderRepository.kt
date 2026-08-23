package com.example.cloudrive.data.repository

import com.example.cloudrive.data.model.CreateFolderRequest
import com.example.cloudrive.data.model.Folder
import com.example.cloudrive.data.model.FolderContents
import com.example.cloudrive.data.remote.api.FolderApi
import com.google.gson.Gson

class FolderRepository(private val api: FolderApi) {
    private val gson = Gson()

    suspend fun getRootFolders(): Result<List<Folder>> = runCatching {
        val r = api.listRootFolders()
        r.body()?.items ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun getFolderContents(id: Long, page: Int = 1): Result<FolderContents> = runCatching {
        val r = api.getFolderContents(id, page)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun createFolder(name: String, parentId: Long?): Result<Folder> = runCatching {
        val r = api.createFolder(CreateFolderRequest(name, parentId))
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun deleteFolder(id: Long): Result<Unit> = runCatching {
        val r = api.deleteFolder(id)
        if (!r.isSuccessful) error(parseError(r.errorBody()?.string()))
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
