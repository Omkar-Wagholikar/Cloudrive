package com.example.cloudrive.data.remote.api

import com.example.cloudrive.data.model.CreateFolderRequest
import com.example.cloudrive.data.model.Folder
import com.example.cloudrive.data.model.FolderContents
import com.example.cloudrive.data.model.FolderList
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FolderApi {
    @GET("/folders")
    suspend fun listRootFolders(): Response<FolderList>

    @GET("/folders/{id}")
    suspend fun getFolderContents(
        @Path("id") id: Long,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<FolderContents>

    @POST("/folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): Response<Folder>

    @DELETE("/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: Long): Response<Unit>
}
