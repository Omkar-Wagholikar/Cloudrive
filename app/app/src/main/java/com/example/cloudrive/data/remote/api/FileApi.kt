package com.example.cloudrive.data.remote.api

import com.example.cloudrive.data.model.BatchFilesRequest
import com.example.cloudrive.data.model.BatchFilesResponse
import com.example.cloudrive.data.model.DeleteFileResponse
import com.example.cloudrive.data.model.FileInfo
import com.example.cloudrive.data.model.FileItem
import com.example.cloudrive.data.model.FileList
import com.example.cloudrive.data.model.RenameFileRequest
import com.example.cloudrive.data.model.ShareLinkListResponse
import com.example.cloudrive.data.model.ShareRequest
import com.example.cloudrive.data.model.ShareToken
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FileApi {
    @GET("/files")
    suspend fun listFiles(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<FileList>

    @GET("/files/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<FileList>

    @GET("/files/{id}")
    suspend fun getFile(@Path("id") id: Long): Response<FileInfo>

    @PATCH("/files/{id}")
    suspend fun patchFile(
        @Path("id") id: Long,
        @Body body: RenameFileRequest
    ): Response<FileItem>

    @DELETE("/files/{id}")
    suspend fun deleteFile(@Path("id") id: Long): Response<DeleteFileResponse>

    @POST("/files/{id}/share")
    suspend fun shareFile(
        @Path("id") id: Long,
        @Body body: ShareRequest
    ): Response<ShareToken>

    @GET("/thumbnails")
    suspend fun listThumbnails(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("type") type: String? = null
    ): Response<FileList>

    @POST("/files/batch")
    suspend fun batchFiles(@Body body: BatchFilesRequest): Response<BatchFilesResponse>

    @GET("/shared")
    suspend fun listShares(): Response<ShareLinkListResponse>

    @DELETE("/shared/{token}")
    suspend fun revokeShare(@Path("token") token: String): Response<Unit>
}
