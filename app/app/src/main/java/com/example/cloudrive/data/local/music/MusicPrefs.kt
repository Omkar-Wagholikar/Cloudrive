package com.example.cloudrive.data.local.music

import android.content.Context
import android.content.SharedPreferences

/** Small persisted state for the music sync job (delta-sync cursor). */
class MusicPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cloudrive_music", Context.MODE_PRIVATE)

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var wifiOnlyDownloads: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** Persisted sort-mode key for [com.example.cloudrive.ui.music.SongsScreen], e.g. "TITLE". */
    var sortMode: String?
        get() = prefs.getString(KEY_SORT_MODE, null)
        set(value) = prefs.edit().putString(KEY_SORT_MODE, value).apply()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_WIFI_ONLY = "wifi_only_downloads"
        private const val KEY_SORT_MODE = "sort_mode"
    }
}
