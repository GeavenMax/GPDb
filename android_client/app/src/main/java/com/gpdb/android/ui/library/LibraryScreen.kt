package com.gpdb.android.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.MovieGridItem
import com.gpdb.android.ui.components.PerformerGridItem
import com.gpdb.android.ui.components.SearchTopAppBar

import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    physicalRootPath: String,
    viewModel: LibraryViewModel,
    onMovieClick: (Long) -> Unit,
    onPerformerClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onSeriesClick: (String) -> Unit = {},
    onStudioClick: (String) -> Unit = {},
    onDirectorClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    Scaffold(
        topBar = {
            Column {
                SearchTopAppBar(
                    title = com.gpdb.android.util.I18n.string("nav.library"),
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.updateSearch(it) },
                    placeholder = "在我的收藏中搜索...",
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        LibraryTab.FAV_MOVIES to "收藏影片",
                        LibraryTab.FAV_PERFORMERS to "收藏演员",
                        LibraryTab.FAV_SERIES to "收藏系列",
                        LibraryTab.FAV_EPISODES to "收藏分集",
                        LibraryTab.FAV_DIRECTORS to "收藏导演"
                    )
                    tabs.forEach { (tab, text) ->
                        FilterChip(
                            selected = uiState.currentTab == tab,
                            onClick = { viewModel.setTab(tab) },
                            label = { Text(text) },
                            shape = RoundedCornerShape(16.dp)
                        )
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
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (uiState.currentTab) {
                        LibraryTab.FAV_PERFORMERS -> {
                            items(
                                items = uiState.performers,
                                key = { it.id ?: it.hashCode() },
                                contentType = { "performer" }
                            ) { p ->
                                PerformerGridItem(
                                    performer = p,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onPerformerClick(p.id ?: 0L) }
                                )
                            }
                        }
                        LibraryTab.FAV_SERIES -> {
                            items(
                                items = uiState.series,
                                key = { it.id ?: "${it.studioName}_${it.rootTitle}_${it.hashCode()}" },
                                contentType = { "series" }
                            ) { s ->
                                com.gpdb.android.ui.browse.SeriesGridItem(
                                    series = s,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onSeriesClick("${s.studioName ?: ""}|||${s.rootTitle}") }
                                )
                            }
                        }
                        LibraryTab.FAV_EPISODES -> {
                            items(
                                items = uiState.episodes,
                                key = { it.id ?: it.hashCode() },
                                contentType = { "episode" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { episode ->
                                EpisodeListItem(
                                    episodeId = episode.id,
                                    title = episode.title,
                                    thumbnailUrl = episode.thumbnailUrl,
                                    physicalRootPath = physicalRootPath,
                                    studioName = episode.studioName,
                                    releaseDate = episode.releaseDate,
                                    onClick = { episode.id?.let { onEpisodeClick(it) } }
                                )
                            }
                        }
                        LibraryTab.FAV_DIRECTORS -> {
                            items(
                                items = uiState.directors,
                                key = { it.id },
                                contentType = { "director" },
                                span = { GridItemSpan(maxLineSpan) }
                            ) { director ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = director.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "${director.worksCount} 部作品",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    leadingContent = {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Videocam,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { onDirectorClick(director.name) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        else -> {
                            items(
                                items = uiState.movies,
                                key = { it.id ?: it.hashCode() },
                                contentType = { "movie" }
                            ) { m ->
                                MovieGridItem(
                                    movie = m,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onMovieClick(m.id ?: 0L) },
                                    onStudioClick = { studio -> onStudioClick(studio) }
                                )
                            }
                        }
                    }

                    if (uiState.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }
        }
    }
}
