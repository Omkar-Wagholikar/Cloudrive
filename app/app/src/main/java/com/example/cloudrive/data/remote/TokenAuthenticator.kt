package com.example.cloudrive.data.remote

import com.example.cloudrive.data.local.TokenStore
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val bareClient: OkHttpClient
) : okhttp3.Authenticator {

    private val gson = Gson()

    @Volatile private var isRefreshing = false

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if refresh endpoint itself returns 401
        if (response.request.url.encodedPath.contains("/token/refresh")) return null

        val currentRefresh = tokenStore.refreshToken
        if (currentRefresh.isEmpty()) return null

        synchronized(this) {
            // Another thread may have already refreshed — check if token changed
            val latestAccess = tokenStore.accessToken
            val requestAccess = response.request.header("Authorization")?.removePrefix("Bearer ") ?: ""
            if (latestAccess.isNotEmpty() && latestAccess != requestAccess) {
                // Token was refreshed by another concurrent request — retry with new token
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestAccess")
                    .build()
            }

            if (isRefreshing) return null
            isRefreshing = true

            return try {
                val body = gson.toJson(mapOf("refresh_token" to currentRefresh))
                val request = Request.Builder()
                    .url("${tokenStore.serverUrl}/token/refresh")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val refreshResponse = bareClient.newCall(request).execute()
                if (!refreshResponse.isSuccessful) {
                    tokenStore.clear()
                    return null
                }

                val json = refreshResponse.body?.string() ?: return null
                val map = gson.fromJson(json, Map::class.java)
                val newAccess = map["access_token"] as? String ?: return null
                val newRefresh = map["refresh_token"] as? String ?: return null

                tokenStore.accessToken = newAccess
                tokenStore.refreshToken = newRefresh

                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .build()
            } catch (e: Exception) {
                null
            } finally {
                isRefreshing = false
            }
        }
    }
}
