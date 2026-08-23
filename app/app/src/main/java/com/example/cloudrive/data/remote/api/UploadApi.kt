package com.example.cloudrive.data.remote.api

import com.example.cloudrive.data.model.ResumableSessionsResponse
import com.example.cloudrive.data.model.StartResumableRequest
import com.example.cloudrive.data.model.StartResumableResponse
import com.example.cloudrive.data.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface UploadApi {
    @Multipart
    @POST("/upload")
    suspend fun uploadMultipart(
        @Part file: MultipartBody.Part,
        @Part("folder_id") folderId: RequestBody?
    ): Response<UploadResponse>

    @POST("/uploads/resumable")
    suspend fun startResumable(@Body body: StartResumableRequest): Response<StartResumableResponse>

    @GET("/uploads/resumable")
    suspend fun listResumable(): Response<ResumableSessionsResponse>

    @PATCH("/uploads/resumable/{upload_id}")
    suspend fun patchResumable(
        @Path("upload_id") uploadId: String,
        @Header("Content-Range") contentRange: String,
        @Body chunk: RequestBody
    ): Response<okhttp3.ResponseBody>
}
