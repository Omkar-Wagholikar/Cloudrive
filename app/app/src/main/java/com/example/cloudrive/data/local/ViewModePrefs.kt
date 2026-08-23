package com.example.cloudrive.data.local

import android.content.Context
import android.content.SharedPreferences

enum class ViewMode { LIST, GRID }

/** Persists the chosen list/grid layout per screen (My Drive, Folder, Search, Trash). */
class ViewModePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cloudrive_view_mode", Context.MODE_PRIVATE)

    fun get(screenKey: String): ViewMode =
        if (prefs.getString(screenKey, ViewMode.LIST.name) == ViewMode.GRID.name) {
            ViewMode.GRID
        } else {
            ViewMode.LIST
        }

    fun set(screenKey: String, mode: ViewMode) {
        prefs.edit().putString(screenKey, mode.name).apply()
    }

    companion object {
        const val MY_DRIVE = "my_drive"
        const val FOLDER = "folder"
        const val SEARCH = "search"
        const val TRASH = "trash"
    }
}
