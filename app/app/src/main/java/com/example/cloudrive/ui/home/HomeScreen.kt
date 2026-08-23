package com.example.cloudrive.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.cloudrive.CloudriveApp
import com.example.cloudrive.navigation.HomeTab
import com.example.cloudrive.navigation.Screen
import com.example.cloudrive.navigation.isReducedMotionEnabled
import com.example.cloudrive.ui.components.AccountSheet
import com.example.cloudrive.ui.components.music.MiniPlayer
import com.example.cloudrive.ui.links.ShareLinksScreen
import com.example.cloudrive.ui.music.MusicTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    var currentTab by remember { mutableStateOf(HomeTab.FILES) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showAccountSheet by remember { mutableStateOf(false) }
    val reducedMotion = isReducedMotionEnabled(LocalContext.current)

    // Warm the LAN-vs-WAN resolution once per session so file/thumbnail URLs
    // prefer the local network address when one is reachable.
    LaunchedEffect(Unit) {
        CloudriveApp.locator.lanResolver.refresh()
    }

    val tabTitle = when (currentTab) {
        HomeTab.FILES -> "Files"
        HomeTab.PHOTOS -> "Photos"
        HomeTab.LINKS -> "Links"
        HomeTab.MUSIC -> "Music"
        else -> "Cloudrive"
    }

    if (showAccountSheet) {
        AccountSheet(
            onDismiss = { showAccountSheet = false },
            onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
            onNavigateToServerSettings = { navController.navigate(Screen.ServerSettings.route) },
            onLoggedOut = {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Music owns its own in-tab header (art-forward, no generic app bar).
            if (currentTab != HomeTab.MUSIC) TopAppBar(
                title = { Text(tabTitle) },
                actions = {
                    if (currentTab == HomeTab.FILES || currentTab == HomeTab.PHOTOS) {
                        IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }
                    IconButton(onClick = { showAccountSheet = true }) {
                        androidx.compose.material3.Surface(
                            shape = CircleShape,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Account",
                                modifier = Modifier.padding(4.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                // Visible on every tab whenever playback is active, sitting just above the nav bar.
                MiniPlayer(onExpand = { navController.navigate(Screen.NowPlaying.route) })
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == HomeTab.FILES,
                        onClick = { currentTab = HomeTab.FILES },
                        icon = { Icon(Icons.Default.Folder, null) },
                        label = { Text("Files") }
                    )
                    NavigationBarItem(
                        selected = currentTab == HomeTab.PHOTOS,
                        onClick = { currentTab = HomeTab.PHOTOS },
                        icon = { Icon(Icons.Default.Image, null) },
                        label = { Text("Photos") }
                    )
                    NavigationBarItem(
                        selected = currentTab == HomeTab.MUSIC,
                        onClick = { currentTab = HomeTab.MUSIC },
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = currentTab == HomeTab.LINKS,
                        onClick = { currentTab = HomeTab.LINKS },
                        icon = { Icon(Icons.Default.Link, null) },
                        label = { Text("Links") }
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        // Fade-through (M3 pattern): outgoing tab content fades out faster than the
        // incoming tab content fades in, with no cross-slide. Reduced-motion collapses
        // both to the same short fade rather than disabling the transition entirely, since
        // fades are explicitly kept per the brief ("disable transforms, keep fades").
        val fadeOutMs = 150
        val fadeInMs = if (reducedMotion) 150 else 200
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(tween(fadeInMs)) togetherWith fadeOut(tween(fadeOutMs)) using
                    SizeTransform(clip = false)
            },
            label = "home_tab_fade_through"
        ) { tab ->
            when (tab) {
                HomeTab.FILES -> MyDriveTab(
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    modifier = contentModifier
                )
                HomeTab.PHOTOS -> PhotosTab(navController = navController, modifier = contentModifier)
                HomeTab.MUSIC -> MusicTab(navController = navController, modifier = contentModifier)
                HomeTab.LINKS -> ShareLinksScreen(
                    navController = navController,
                    asTab = true,
                    modifier = contentModifier
                )
            }
        }
    }
}
