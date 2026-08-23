package com.example.cloudrive.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cloudrive_tokens", Context.MODE_PRIVATE)

    @get:Synchronized @set:Synchronized
    var accessToken: String
        get() = prefs.getString(KEY_ACCESS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ACCESS, v).apply()

    @get:Synchronized @set:Synchronized
    var refreshToken: String
        get() = prefs.getString(KEY_REFRESH, "") ?: ""
        set(v) = prefs.edit().putString(KEY_REFRESH, v).apply()

    @get:Synchronized @set:Synchronized
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_URL) ?: DEFAULT_URL
        set(v) = prefs.edit().putString(KEY_SERVER, v.trimEnd('/')).apply()

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    @Synchronized
    fun isLoggedIn(): Boolean = accessToken.isNotEmpty() && refreshToken.isNotEmpty()

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_SERVER = "server_url"
        const val DEFAULT_URL = "http://localhost:8081"
    }
}
