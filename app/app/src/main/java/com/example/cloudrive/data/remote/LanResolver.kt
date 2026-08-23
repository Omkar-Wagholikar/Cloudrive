package com.example.cloudrive.data.remote

import com.example.cloudrive.data.local.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Picks the fastest reachable base URL for a session: races a LAN candidate
 * (from GET /network) against the user-configured server URL with a short
 * timeout, per the backend README's LAN-vs-WAN recommendation for a
 * self-hosted box. Resolved once and cached; call [refresh] to re-probe
 * (e.g. on reconnect or Wi-Fi change).
 */
class LanResolver(
    private val probeClient: OkHttpClient,
    private val tokenStore: TokenStore
) {
    @Volatile private var resolvedBaseUrl: String? = null

    /** Synchronous, safe to call from Compose — falls back to the configured server URL until resolved. */
    fun currentBaseUrl(): String = resolvedBaseUrl ?: tokenStore.serverUrl

    /** True once [refresh] has resolved a LAN candidate distinct from the configured server URL. */
    fun isOnLan(): Boolean = resolvedBaseUrl != null && resolvedBaseUrl != tokenStore.serverUrl

    fun invalidate() {
        resolvedBaseUrl = null
    }

    suspend fun refresh() {
        val configured = tokenStore.serverUrl
        val candidate = fetchLanCandidate(configured)
        resolvedBaseUrl = if (candidate != null && candidate != configured && isReachable(candidate)) {
            candidate
        } else {
            configured
        }
    }

    private suspend fun fetchLanCandidate(configured: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$configured/network").build()
            probeClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val addrs = JSONObject(body).optJSONArray("local_addresses") ?: return@use null
                if (addrs.length() == 0) null else "http://${addrs.getString(0)}"
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun isReachable(baseUrl: String): Boolean = withTimeoutOrNull(LAN_PROBE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$baseUrl/ping").build()
                probeClient.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }
    } ?: false

    companion object {
        private const val LAN_PROBE_TIMEOUT_MS = 300L
    }
}
