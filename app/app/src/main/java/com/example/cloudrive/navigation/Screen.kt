package com.example.cloudrive.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Folder : Screen("folder/{folderId}") {
        fun createRoute(folderId: Long) = "folder/$folderId"
    }
    object Search : Screen("search")
    object ShareLinks : Screen("share_links")
    object Trash : Screen("trash")
    object Preview : Screen("preview/{fileId}") {
        fun createRoute(fileId: Long) = "preview/$fileId"
    }
    object MusicAlbum : Screen("music/album/{albumKey}") {
        fun createRoute(albumKey: String) = "music/album/$albumKey"
    }
    object MusicArtist : Screen("music/artist/{artistName}") {
        fun createRoute(artistName: String) = "music/artist/$artistName"
    }
    object MusicGenre : Screen("music/genre/{genreName}") {
        fun createRoute(genreName: String) = "music/genre/$genreName"
    }
    object MusicSearch : Screen("music/search")
    object MusicPlaylist : Screen("music/playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "music/playlist/$playlistId"
    }
    object MusicDownloads : Screen("music/downloads")
    object ServerSettings : Screen("server_settings")
    object NowPlaying : Screen("now_playing")
}

object HomeTab {
    const val FILES = "files"
    const val PHOTOS = "photos"
    const val LINKS = "links"
    const val MUSIC = "music"
}
