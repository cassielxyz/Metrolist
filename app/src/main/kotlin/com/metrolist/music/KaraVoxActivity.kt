package com.metrolist.music

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.ui.screens.karaoke.KaraVoxSettingsScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeDuetScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeHomeScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeLibraryScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeOnlineSearchResult
import com.metrolist.music.ui.screens.karaoke.KaraokePrepareScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeRecordingsScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeSearchScreen
import com.metrolist.music.ui.screens.karaoke.KaraokeSessionScreen
import com.metrolist.music.ui.theme.MetrolistTheme
import com.metrolist.music.utils.SearchRoutes
import dagger.hilt.android.AndroidEntryPoint

/** Dedicated production KaraVox host. Legacy Metrolist player/social navigation is not mounted. */
@AndroidEntryPoint
class KaraVoxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetrolistTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val tabs = listOf(
                    KaraVoxTab("home", "Home", "H"),
                    KaraVoxTab("search_input", "Search", "S"),
                    KaraVoxTab("library", "Library", "L"),
                    KaraVoxTab("recordings", "Recordings", "R"),
                    KaraVoxTab("settings", "Settings", "⚙"),
                )
                val showTabs = route in tabs.map { it.route }

                CompositionLocalProvider(LocalNavController provides navController) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            if (showTabs) {
                                NavigationBar {
                                    tabs.forEach { tab ->
                                        NavigationBarItem(
                                            selected = route == tab.route,
                                            onClick = {
                                                navController.navigate(tab.route) {
                                                    popUpTo("home") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Text(tab.shortLabel) },
                                            label = { Text(tab.label) },
                                        )
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            modifier = Modifier.padding(padding),
                        ) {
                            composable("home") {
                                KaraokeHomeScreen(
                                    onSearchOnline = { navController.navigate("search_input") },
                                    onOfflineSongSelected = { mediaUri ->
                                        navController.navigate("karaoke_prepare_local?uri=${Uri.encode(mediaUri)}")
                                    },
                                    onOpenDuetRoom = { navController.navigate("duet") },
                                    onRecordLaterDuet = { navController.navigate("duet") },
                                    onOpenRecordings = { navController.navigate("recordings") },
                                )
                            }
                            composable("search_input") {
                                KaraokeSearchScreen(
                                    onSearch = { query -> navController.navigate(SearchRoutes.resultRoute(query)) },
                                    onLocalSongSelected = { mediaUri ->
                                        navController.navigate("karaoke_prepare_local?uri=${Uri.encode(mediaUri)}")
                                    },
                                )
                            }
                            composable("library") {
                                KaraokeLibraryScreen { sessionId ->
                                    navController.navigate("karaoke_session/${Uri.encode(sessionId)}")
                                }
                            }
                            composable("recordings") { KaraokeRecordingsScreen() }
                            composable("settings") { KaraVoxSettingsScreen() }
                            composable("duet") {
                                KaraokeDuetScreen(
                                    onOpenSession = { sessionId ->
                                        navController.navigate("karaoke_session/${Uri.encode(sessionId)}")
                                    },
                                    onOpenRecordings = { navController.navigate("recordings") },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(
                                route = "search/{query}",
                                arguments = listOf(navArgument("query") { type = NavType.StringType }),
                            ) { KaraokeOnlineSearchResult() }
                            composable(
                                route = "karaoke_prepare/{videoId}",
                                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                            ) { entry ->
                                KaraokePrepareScreen(
                                    songId = entry.arguments?.getString("videoId").orEmpty(),
                                    source = KaraokeSource.ONLINE,
                                    onStartKaraoke = { sessionId ->
                                        navController.navigate("karaoke_session/${Uri.encode(sessionId)}")
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(
                                route = "karaoke_prepare_local?uri={uri}",
                                arguments = listOf(navArgument("uri") { type = NavType.StringType }),
                            ) { entry ->
                                val mediaUri = entry.arguments?.getString("uri").orEmpty()
                                KaraokePrepareScreen(
                                    songId = "local-${mediaUri.hashCode()}",
                                    source = KaraokeSource.LOCAL,
                                    mediaUri = mediaUri,
                                    onStartKaraoke = { sessionId ->
                                        navController.navigate("karaoke_session/${Uri.encode(sessionId)}")
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable(
                                route = "karaoke_session/{sessionId}",
                                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                            ) { entry ->
                                KaraokeSessionScreen(
                                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class KaraVoxTab(
    val route: String,
    val label: String,
    val shortLabel: String,
)
