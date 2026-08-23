package com.example.cloudrive.data.remote.api

import com.example.cloudrive.data.model.FileList
import com.example.cloudrive.data.model.PurgeResponse
import com.example.cloudrive.data.model.RestoreResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrashApi {
    @GET("/trash")
    suspend fun listTrash(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<FileList>

    @POST("/trash/{id}/restore")
    suspend fun restore(@Path("id") id: Long): Response<RestoreResponse>

    @DELETE("/trash/{id}")
    suspend fun deletePermanently(@Path("id") id: Long): Response<Unit>

    @DELETE("/trash")
    suspend fun purgeAll(): Response<PurgeResponse>
}
