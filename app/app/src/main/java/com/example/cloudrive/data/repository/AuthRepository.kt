package com.example.cloudrive.data.repository

import com.example.cloudrive.data.local.TokenStore
import com.example.cloudrive.data.model.LoginRequest
import com.example.cloudrive.data.model.LogoutRequest
import com.example.cloudrive.data.model.RegisterRequest
import com.example.cloudrive.data.model.TokenResponse
import com.example.cloudrive.data.model.User
import com.example.cloudrive.data.remote.api.AuthApi

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore
) {
    suspend fun login(username: String, password: String): Result<TokenResponse> = runCatching {
        val response = api.login(LoginRequest(username, password))
        val body = response.body() ?: error(parseError(response.errorBody()?.string()))
        tokenStore.accessToken = body.accessToken
        tokenStore.refreshToken = body.refreshToken
        body
    }

    suspend fun register(username: String, password: String): Result<Unit> = runCatching {
        val response = api.register(RegisterRequest(username, password))
        if (!response.isSuccessful) error(parseError(response.errorBody()?.string()))
    }

    suspend fun logout(): Result<Unit> = runCatching {
        val refresh = tokenStore.refreshToken
        api.logout(LogoutRequest(refresh))
        tokenStore.clear()
    }

    suspend fun getMe(): Result<User> = runCatching {
        val response = api.getMe()
        response.body() ?: error(parseError(response.errorBody()?.string()))
    }

    private fun parseError(body: String?): String {
        if (body == null) return "Unknown error"
        return try {
            val map = com.google.gson.Gson().fromJson(body, Map::class.java)
            map["error"] as? String ?: body
        } catch (e: Exception) {
            body
        }
    }
}
