package com.example.cloudrive.data.remote.api

import com.example.cloudrive.data.model.LoginRequest
import com.example.cloudrive.data.model.LogoutRequest
import com.example.cloudrive.data.model.RefreshRequest
import com.example.cloudrive.data.model.RegisterRequest
import com.example.cloudrive.data.model.TokenResponse
import com.example.cloudrive.data.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("/register")
    suspend fun register(@Body body: RegisterRequest): Response<Map<String, Any>>

    @POST("/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("/token/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>

    @POST("/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<Unit>

    @GET("/me")
    suspend fun getMe(): Response<User>
}
