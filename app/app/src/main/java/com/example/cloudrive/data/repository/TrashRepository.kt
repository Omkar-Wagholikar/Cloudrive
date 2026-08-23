package com.example.cloudrive.data.repository

import com.example.cloudrive.data.model.FileList
import com.example.cloudrive.data.model.PurgeResponse
import com.example.cloudrive.data.model.RestoreResponse
import com.example.cloudrive.data.remote.api.TrashApi
import com.google.gson.Gson

class TrashRepository(private val api: TrashApi) {
    private val gson = Gson()

    suspend fun listTrash(page: Int = 1): Result<FileList> = runCatching {
        val r = api.listTrash(page)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun restore(id: Long): Result<RestoreResponse> = runCatching {
        val r = api.restore(id)
        r.body() ?: error(parseError(r.errorBody()?.string()))
    }

    suspend fun deletePermanently(id: Long): Result<Unit> = runCatching {
        val r = api.deletePermanently(id)
        if (!r.isSuccessful) error(parseError(r.errorBody()?.string()))
    }

    suspend fun purgeAll(): Result<PurgeResponse> = runCatching {
        val r = api.purgeAll()
        r.body() ?: error(parseError(r.errorBody()?.string()))
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
