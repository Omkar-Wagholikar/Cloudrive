package com.example.cloudrive.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.ui.auth.AuthScreen
import com.example.cloudrive.ui.folder.FolderScreen
import com.example.cloudrive.ui.home.HomeScreen
import com.example.cloudrive.ui.links.ShareLinksScreen
import com.example.cloudrive.ui.music.AlbumDetailScreen
import com.example.cloudrive.ui.music.ArtistDetailScreen
import com.example.cloudrive.ui.music.DownloadsScreen
import com.example.cloudrive.ui.music.GenreDetailScreen
import com.example.cloudrive.ui.music.MusicSearchScreen
import com.example.cloudrive.ui.music.NowPlayingScreen
import com.example.cloudrive.ui.music.PlaylistDetailScreen
import com.example.cloudrive.ui.preview.PreviewScreen
import com.example.cloudrive.ui.search.SearchScreen
import com.example.cloudrive.ui.settings.ServerSettingsScreen
import com.example.cloudrive.ui.trash.TrashTab

/** M3 "emphasized" easing duration for the shared-axis/fade-through transitions below. */
private const val MotionDurationMs = 300

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudriveNavHost(navController: NavHostController) {
    val isLoggedIn = CloudriveApp.locator.tokenStore.isLoggedIn()
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Auth.route
    val reducedMotion = isReducedMotionEnabled(LocalContext.current)

    // Shared-axis X (forward): drilling into a folder slides the new content in from the
    // right while the old content slides out to the left, both fading. Reduced-motion keeps
    // only the fade, per the brief ("disable transforms, keep fades").
    val sharedAxisXEnter: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        if (reducedMotion) {
            fadeIn(tween(MotionDurationMs))
        } else {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(MotionDurationMs, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(MotionDurationMs))
        }
    }
    val sharedAxisXExit: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        if (reducedMotion) {
            fadeOut(tween(MotionDurationMs))
        } else {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(MotionDurationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(MotionDurationMs))
        }
    }
    // Shared-axis X (reverse): backing out of a folder slides back the other way.
    val sharedAxisXPopEnter: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        if (reducedMotion) {
            fadeIn(tween(MotionDurationMs))
        } else {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(MotionDurationMs, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(MotionDurationMs))
        }
    }
    val sharedAxisXPopExit: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        if (reducedMotion) {
            fadeOut(tween(MotionDurationMs))
        } else {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(MotionDurationMs, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(MotionDurationMs))
        }
    }

    // Fade-through (simple): full-screen swaps that aren't "drilling deeper" (opening a
    // preview, opening trash) get a plain cross-fade rather than shared-axis slide.
    val fadeThroughEnter: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
        fadeIn(tween(MotionDurationMs))
    }
    val fadeThroughExit: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
        fadeOut(tween(MotionDurationMs))
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Folder.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
            enterTransition = sharedAxisXEnter,
            exitTransition = sharedAxisXExit,
            popEnterTransition = sharedAxisXPopEnter,
            popExitTransition = sharedAxisXPopExit
        ) {
            FolderScreen(navController = navController)
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType }),
            enterTransition = fadeThroughEnter,
            exitTransition = fadeThroughExit,
            popEnterTransition = fadeThroughEnter,
            popExitTransition = fadeThroughExit
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments!!.getLong("fileId")
            PreviewScreen(navController = navController, fileId = fileId)
        }

        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }

        composable(Screen.ShareLinks.route) {
            ShareLinksScreen(navController = navController)
        }

        composable(
            route = Screen.Trash.route,
            enterTransition = fadeThroughEnter,
            exitTransition = fadeThroughExit,
            popEnterTransition = fadeThroughEnter,
            popExitTransition = fadeThroughExit
        ) {
            val snackbarHostState = remember { SnackbarHostState() }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Trash") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                TrashTab(
                    snackbarHostState = snackbarHostState,
                    modifier = androidx.compose.ui.Modifier.padding(padding)
                )
            }
        }

        composable(
            route = Screen.MusicAlbum.route,
            arguments = listOf(navArgument("albumKey") { type = NavType.StringType }),
            enterTransition = sharedAxisXEnter,
            exitTransition = sharedAxisXExit,
            popEnterTransition = sharedAxisXPopEnter,
            popExitTransition = sharedAxisXPopExit
        ) { backStackEntry ->
            val albumKey = backStackEntry.arguments!!.getString("albumKey")!!
            AlbumDetailScreen(navController = navController, albumKey = albumKey)
        }

        composable(
            route = Screen.MusicArtist.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
            enterTransition = sharedAxisXEnter,
            exitTransition = sharedAxisXExit,
            popEnterTransition = sharedAxisXPopEnter,
            popExitTransition = sharedAxisXPopExit
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments!!.getString("artistName")!!
            ArtistDetailScreen(navController = navController, artistName = artistName)
        }

        composable(
            route = Screen.MusicGenre.route,
            arguments = listOf(navArgument("genreName") { type = NavType.StringType }),
            enterTransition = sharedAxisXEnter,
            exitTransition = sharedAxisXExit,
            popEnterTransition = sharedAxisXPopEnter,
            popExitTransition = sharedAxisXPopExit
        ) { backStackEntry ->
            val genreName = backStackEntry.arguments!!.getString("genreName")!!
            GenreDetailScreen(navController = navController, genreName = genreName)
        }

        composable(
            route = Screen.MusicSearch.route,
            enterTransition = fadeThroughEnter,
            exitTransition = fadeThroughExit,
            popEnterTransition = fadeThroughEnter,
            popExitTransition = fadeThroughExit
        ) {
            MusicSearchScreen(navController = navController)
        }

        composable(
            route = Screen.ServerSettings.route,
            enterTransition = fadeThroughEnter,
            exitTransition = fadeThroughExit,
            popEnterTransition = fadeThroughEnter,
            popExitTransition = fadeThroughExit
        ) {
            ServerSettingsScreen(navController = navController)
        }

        composable(
            route = Screen.MusicPlaylist.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            enterTransition = sharedAxisXEnter,
            exitTransition = sharedAxisXExit,
            popEnterTransition = sharedAxisXPopEnter,
            popExitTransition = sharedAxisXPopExit
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments!!.getLong("playlistId")
            PlaylistDetailScreen(navController = navController, playlistId = playlistId)
        }

        composable(
            route = Screen.MusicDownloads.route,
            enterTransition = fadeThroughEnter,
            exitTransition = fadeThroughExit,
            popEnterTransition = fadeThroughEnter,
            popExitTransition = fadeThroughExit
        ) {
            DownloadsScreen(navController = navController)
        }

        composable(
            route = Screen.NowPlaying.route,
            enterTransition = {
                if (reducedMotion) fadeIn(tween(MotionDurationMs)) else
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Up,
                        tween(MotionDurationMs, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(MotionDurationMs))
            },
            exitTransition = { fadeOut(tween(MotionDurationMs)) },
            popEnterTransition = { fadeIn(tween(MotionDurationMs)) },
            popExitTransition = {
                if (reducedMotion) fadeOut(tween(MotionDurationMs)) else
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Down,
                        tween(MotionDurationMs, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(MotionDurationMs))
            }
        ) {
            NowPlayingScreen(navController = navController)
        }
    }
}
