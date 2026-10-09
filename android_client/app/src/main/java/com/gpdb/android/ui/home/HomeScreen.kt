package com.gpdb.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpdb.android.ui.browse.SeriesListContent
import com.gpdb.android.ui.browse.SeriesListViewModel
import com.gpdb.android.ui.components.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {},
    onDirectorClick: (String) -> Unit = {},
    onPerformerClick: (Long) -> Unit,
    onRemountClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onEpisodeClick: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val seriesViewModel: SeriesListViewModel = viewModel()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isTopExpanded = scrollBehavior.state.collapsedFraction < 0.5f

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = { Text(com.gpdb.android.util.I18n.string("nav.movies")) },
                    actions = {
                        // 仅在主长篇电影 Tab 下按需显示排序按钮
                        if (uiState.homeTab == HomeTab.ALL_MOVIES) {
                            var showSortMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = com.gpdb.android.util.I18n.string("filter.sortBy"))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(com.gpdb.android.util.I18n.string("sort.byOrder", defaultVal = "按收录顺序")) },
                                    onClick = {
                                        showSortMenu = false
                                        if (uiState.sortByYear) viewModel.toggleSortOrder()
                                    },
                                    trailingIcon = { if (!uiState.sortByYear) Icon(Icons.Default.Check, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(com.gpdb.android.util.I18n.string("sort.byYear", defaultVal = "按发行年份")) },
                                    onClick = {
                                        showSortMenu = false
                                        if (!uiState.sortByYear) viewModel.toggleSortOrder()
                                    },
                                    trailingIcon = { if (uiState.sortByYear) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }

                        val context = LocalContext.current
                        var isScraping by remember { mutableStateOf(false) }
                        IconButton(
                            enabled = !isScraping,
                            onClick = {
                                isScraping = true
                                viewModel.triggerManualScrape(context) { resultMsg ->
                                    isScraping = false
                                    Toast.makeText(context, resultMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            if (isScraping) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = com.gpdb.android.util.I18n.string("home.scrapeLatest", defaultVal = "刮削最新影片")
                                )
                            }
                        }
                        IconButton(onClick = {
                            val scopeStr = when (uiState.homeTab) {
                                HomeTab.ALL_MOVIES -> "MOVIES"
                                HomeTab.EPISODES -> "EPISODES"
                                HomeTab.SERIES -> "MOVIES"
                                HomeTab.STUDIOS -> "STUDIOS"
                                HomeTab.DIRECTORS -> "DIRECTORS"
                            }
                            onSearchClick(scopeStr)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = com.gpdb.android.util.I18n.string("common.search"))
                        }
                    }
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = isTopExpanded,
                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                ) {
                    Column {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tabs = listOf(
                                HomeTab.ALL_MOVIES to "nav.featureMovies",
                                HomeTab.EPISODES to "nav.episodes",
                                HomeTab.SERIES to "nav.series",
                                HomeTab.STUDIOS to "nav.studios",
                                HomeTab.DIRECTORS to "nav.directors"
                            )
                            tabs.forEach { (tab, key) ->
                                FilterChip(
                                    selected = uiState.homeTab == tab,
                                    onClick = { viewModel.setHomeTab(tab) },
                                    label = { Text(com.gpdb.android.util.I18n.string(key)) },
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }

                        // 快速时间/入库筛选胶囊条
                        if (uiState.homeTab == HomeTab.ALL_MOVIES || uiState.homeTab == HomeTab.EPISODES) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(DateFilter.entries) { filter ->
                                    FilterChip(
                                        selected = uiState.dateFilter == filter,
                                        onClick = { viewModel.setDateFilter(filter) },
                                        label = { Text(filter.getLabel(), style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val status = uiState.mountStatus) {
                is MountStatus.Idle, is MountStatus.Mounting -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        val text = if (status is MountStatus.Mounting) {
                            if (status.i18nKey != null) {
                                com.gpdb.android.util.I18n.string(status.i18nKey, defaultVal = status.stepText)
                            } else {
                                status.stepText
                            }
                        } else {
                            com.gpdb.android.util.I18n.string("common.loading")
                        }
                        Text(text = text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MountStatus.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val errorTitle = if (status.titleKey != null) {
                            com.gpdb.android.util.I18n.string(status.titleKey, defaultVal = status.title)
                        } else {
                            status.title
                        }
                        Text(errorTitle, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        val errorDetail = if (status.detailKey != null) {
                            com.gpdb.android.util.I18n.string(status.detailKey, defaultVal = status.detail)
                        } else {
                            status.detail
                        }
                        Text(errorDetail, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRemountClick) {
                            Text(com.gpdb.android.util.I18n.string("common.retry"))
                        }
                    }
                }
                is MountStatus.Ready -> {
                    when (uiState.homeTab) {
                        HomeTab.ALL_MOVIES -> {
                            val gridState = androidx.compose.runtime.saveable.rememberSaveable(
                                saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver,
                                key = "home_all_movies_grid"
                            ) {
                                androidx.compose.foundation.lazy.grid.LazyGridState()
                            }
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.movies,
                                    key = { it.id ?: it.hashCode() },
                                    contentType = { "movie" }
                                ) { movie ->
                                    MovieGridItem(
                                        movie = movie,
                                        physicalRootPath = uiState.physicalRootPath,
                                        onClick = { onMovieClick(movie.id ?: 0) },
                                        onStudioClick = { studio -> onStudioClick(studio) },
                                        isNew = (movie.releaseYear != null && movie.releaseYear >= 2026) || uiState.dateFilter == DateFilter.LAST_SCRAPED
                                    )
                                }
                                if (uiState.isLoadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                } else if (uiState.hasMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadMoreMovies()
                                        }
                                    }
                                }
                            }
                        }
                        HomeTab.EPISODES -> {
                            val listState = androidx.compose.runtime.saveable.rememberSaveable(
                                saver = LazyListState.Saver,
                                key = "home_episodes_list"
                            ) {
                                LazyListState()
                            }
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.episodes,
                                    key = { it.id ?: it.hashCode().toLong() }
                                ) { episode ->
                                    com.gpdb.android.ui.components.EpisodeListItem(
                                        episodeId = episode.id,
                                        title = episode.title,
                                        thumbnailUrl = episode.thumbnailUrl,
                                        physicalRootPath = uiState.physicalRootPath,
                                        releaseDate = episode.releaseDate,
                                        studioName = episode.studioName,
                                        isNew = (episode.releaseDate != null && episode.releaseDate.startsWith("2026")) || uiState.dateFilter == DateFilter.LAST_SCRAPED,
                                        onClick = { episode.id?.let(onEpisodeClick) }
                                    )
                                }
                                if (uiState.episodesLoadingMore) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                } else if (uiState.episodesHasMore) {
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadMoreEpisodes()
                                        }
                                    }
                                }
                            }
                        }
                        HomeTab.SERIES -> {
                            SeriesListContent(
                                viewModel = seriesViewModel,
                                onSeriesClick = onSeriesClick,
                                physicalRootPath = uiState.physicalRootPath
                            )
                        }
                        HomeTab.STUDIOS -> {
                            val studioViewModel: com.gpdb.android.ui.browse.StudioListViewModel = viewModel()
                            com.gpdb.android.ui.browse.StudioListContent(
                                physicalRootPath = uiState.physicalRootPath,
                                viewModel = studioViewModel,
                                onStudioClick = onStudioClick
                            )
                        }
                        HomeTab.DIRECTORS -> {
                            val directorViewModel: com.gpdb.android.ui.browse.DirectorListViewModel = viewModel()
                            com.gpdb.android.ui.browse.DirectorListContent(
                                viewModel = directorViewModel,
                                onDirectorClick = onDirectorClick
                            )
                        }
                    }
                }
            }
        }
    }
}

