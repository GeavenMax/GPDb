package com.gpdb.android.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gpdb.android.ui.browse.*
import com.gpdb.android.ui.detail.*
import com.gpdb.android.ui.home.HomeScreen
import com.gpdb.android.ui.home.HomeViewModel
import com.gpdb.android.ui.homefeed.HomeFeedScreen
import com.gpdb.android.ui.homefeed.HomeFeedViewModel
import com.gpdb.android.ui.library.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object Performers : Screen("performers")
    object Studios : Screen("studios")
    object Directors : Screen("directors")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object Analytics : Screen("analytics")
    
    object Search : Screen("search")
    object MovieDetail : Screen("movie_detail/{movieId}") {
        fun createRoute(movieId: Long) = "movie_detail/$movieId"
    }
    object PerformerDetail : Screen("performer_detail/{performerId}") {
        fun createRoute(performerId: Long) = "performer_detail/$performerId"
    }
    object EpisodeDetail : Screen("episode_detail/{episodeId}") {
        fun createRoute(episodeId: Long) = "episode_detail/$episodeId"
    }
    object StudioDetail : Screen("studio_detail/{studioName}") {
        fun createRoute(studioName: String) = "studio_detail/${android.util.Base64.encodeToString(studioName.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)}"
    }
    object FilteredMovieList : Screen("filtered_movie_list/{filterType}/{filterValue}") {
        fun createRoute(filterType: String, filterValue: String) = "filtered_movie_list/$filterType/${android.util.Base64.encodeToString(filterValue.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)}"
    }
}

@Composable
fun GpdbNavGraph(
    navController: NavHostController = rememberNavController(),
    homeViewModel: HomeViewModel,
    physicalRootPath: String,
    onRemountClick: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isBottomBarVisible = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Movies.route,
        Screen.Performers.route,
        Screen.Studios.route,
        Screen.Directors.route,
        Screen.Library.route
    )

    Scaffold(
        bottomBar = {
            if (isBottomBarVisible) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.home")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Home.route } == true,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.movies")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Movies.route } == true,
                        onClick = {
                            navController.navigate(Screen.Movies.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.performers")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Performers.route } == true,
                        onClick = {
                            navController.navigate(Screen.Performers.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Business, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.studios")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Studios.route } == true,
                        onClick = {
                            navController.navigate(Screen.Studios.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.directors")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Directors.route } == true,
                        onClick = {
                            navController.navigate(Screen.Directors.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                        label = { Text(com.gpdb.android.util.I18n.string("nav.library")) },
                        selected = currentDestination?.hierarchy?.any { it.route == Screen.Library.route } == true,
                        onClick = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Home.route) {
                val feedViewModel: HomeFeedViewModel = viewModel()
                HomeFeedScreen(
                    viewModel = feedViewModel,
                    physicalRootPath = physicalRootPath,
                    onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                    onEpisodeClick = { episodeId -> navController.navigate(Screen.EpisodeDetail.createRoute(episodeId)) },
                    onPerformerClick = { perfId -> navController.navigate(Screen.PerformerDetail.createRoute(perfId)) },
                    onSeriesClick = { series -> navController.navigate(Screen.FilteredMovieList.createRoute("series", series)) },
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onNavigateToMovies = {
                        navController.navigate(Screen.Movies.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEpisodes = {
                        homeViewModel.setHomeTab(com.gpdb.android.ui.home.HomeTab.EPISODES)
                        navController.navigate(Screen.Movies.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToPerformers = {
                        navController.navigate(Screen.Performers.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToStudios = {
                        navController.navigate(Screen.Studios.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Movies.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onPerformerClick = { perfId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(perfId))
                    },
                    onRemountClick = onRemountClick,
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onSeriesClick = { series ->
                        navController.navigate(Screen.FilteredMovieList.createRoute("series", series))
                    },
                    onEpisodeClick = { episodeId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                    }
                )
            }

            composable(Screen.Performers.route) {
                val viewModel: PerformerListViewModel = viewModel()
                PerformerListScreen(
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onPerformerClick = { performerId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(performerId))
                    }
                )
            }

            composable(Screen.Studios.route) {
                val viewModel: StudioListViewModel = viewModel()
                StudioListScreen(
                    viewModel = viewModel,
                    onStudioClick = { studioName ->
                        navController.navigate(Screen.StudioDetail.createRoute(studioName))
                    }
                )
            }

            composable(Screen.Directors.route) {
                val viewModel: DirectorListViewModel = viewModel()
                DirectorListScreen(
                    viewModel = viewModel,
                    onDirectorClick = { directorName ->
                        navController.navigate(Screen.FilteredMovieList.createRoute("director", directorName))
                    }
                )
            }

            composable(Screen.Library.route) {
                val viewModel: LibraryViewModel = viewModel()
                LibraryScreen(
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onSeriesClick = { series -> navController.navigate(Screen.FilteredMovieList.createRoute("series", series)) },
                    onPerformerClick = { performerId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(performerId))
                    },
                    onEpisodeClick = { episodeId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                    },
                    onDirectorClick = { directorName ->
                        navController.navigate(Screen.FilteredMovieList.createRoute("director", directorName))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                route = Screen.StudioDetail.route,
                arguments = listOf(navArgument("studioName") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedName = backStackEntry.arguments?.getString("studioName") ?: return@composable
                val studioName = String(android.util.Base64.decode(encodedName, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                val viewModel: StudioDetailViewModel = viewModel()
                StudioDetailScreen(
                    studioName = studioName,
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                    onEpisodeClick = { episodeId -> navController.navigate(Screen.EpisodeDetail.createRoute(episodeId)) }
                )
            }

            composable(
                route = Screen.FilteredMovieList.route,
                arguments = listOf(
                    navArgument("filterType") { type = NavType.StringType },
                    navArgument("filterValue") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("filterType") ?: return@composable
                val encodedValue = backStackEntry.arguments?.getString("filterValue") ?: return@composable
                val filterValue = String(android.util.Base64.decode(encodedValue, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
                val viewModel: FilteredMovieListViewModel = viewModel()
                FilteredMovieListScreen(
                    filterType = type,
                    filterValue = filterValue,
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) }
                )
            }

            composable(Screen.Settings.route) {
                com.gpdb.android.ui.settings.SettingsScreen(
                    appPreferences = com.gpdb.android.data.preferences.AppPreferences(androidx.compose.ui.platform.LocalContext.current),
                    appSettingsRepository = com.gpdb.android.data.settings.AppSettingsRepository(androidx.compose.ui.platform.LocalContext.current),
                    onRemountClick = onRemountClick,
                    onBackClick = { navController.popBackStack() },
                    onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) }
                )
            }

            composable(Screen.Analytics.route) {
                com.gpdb.android.ui.analytics.AnalyticsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Search.route) {
                val viewModel: com.gpdb.android.ui.search.SearchViewModel = viewModel()
                com.gpdb.android.ui.search.SearchScreen(
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    },
                    onPerformerClick = { performerId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(performerId))
                    }
                )
            }

            composable(
                route = Screen.MovieDetail.route,
                arguments = listOf(navArgument("movieId") { type = NavType.LongType })
            ) { backStackEntry ->
                val movieId = backStackEntry.arguments?.getLong("movieId") ?: return@composable
                
                val context = androidx.compose.ui.platform.LocalContext.current
                val appSettingsRepository = remember(context) { com.gpdb.android.data.settings.AppSettingsRepository(context) }
                val translationService = remember(appSettingsRepository) { com.gpdb.android.data.ai.LLMTranslationService(appSettingsRepository) }
                val factory = remember(translationService, appSettingsRepository) { 
                    object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return MovieDetailViewModel(translationService, appSettingsRepository) as T
                        }
                    }
                }
                val viewModel: MovieDetailViewModel = viewModel(factory = factory)
                
                MovieDetailScreen(
                    movieId = movieId,
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onPerformerClick = { performerId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(performerId))
                    },
                    onEpisodeClick = { episodeId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                    },
                    onDirectorClick = { director ->
                        navController.navigate(Screen.FilteredMovieList.createRoute("director", director))
                    },
                    onSeriesClick = { series ->
                        navController.navigate(Screen.FilteredMovieList.createRoute("series", series))
                    }
                )
            }

            composable(
                route = Screen.PerformerDetail.route,
                arguments = listOf(navArgument("performerId") { type = NavType.LongType })
            ) { backStackEntry ->
                val performerId = backStackEntry.arguments?.getLong("performerId") ?: return@composable
                val viewModel: PerformerDetailViewModel = viewModel()
                
                PerformerDetailScreen(
                    performerId = performerId,
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onStudioClick = { studio -> navController.navigate(Screen.StudioDetail.createRoute(studio)) },
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    },
                    onEpisodeClick = { episodeId ->
                        navController.navigate(Screen.EpisodeDetail.createRoute(episodeId))
                    }
                )
            }

            composable(
                route = Screen.EpisodeDetail.route,
                arguments = listOf(navArgument("episodeId") { type = NavType.LongType })
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getLong("episodeId") ?: return@composable
                val context = androidx.compose.ui.platform.LocalContext.current
                val appSettingsRepository = androidx.compose.runtime.remember(context) { com.gpdb.android.data.settings.AppSettingsRepository(context) }
                val translationService = androidx.compose.runtime.remember(appSettingsRepository) { com.gpdb.android.data.ai.LLMTranslationService(appSettingsRepository) }
                val factory = androidx.compose.runtime.remember(translationService, appSettingsRepository) { 
                    object : androidx.lifecycle.ViewModelProvider.Factory {
                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return com.gpdb.android.ui.detail.EpisodeDetailViewModel(translationService, appSettingsRepository) as T
                        }
                    }
                }
                val viewModel: com.gpdb.android.ui.detail.EpisodeDetailViewModel = viewModel(factory = factory)
                
                EpisodeDetailScreen(
                    episodeId = episodeId,
                    physicalRootPath = physicalRootPath,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onPerformerClick = { performerId ->
                        navController.navigate(Screen.PerformerDetail.createRoute(performerId))
                    },
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    }
                )
            }
        }
    }
}
