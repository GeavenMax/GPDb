package com.gpdb.android.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpdb.android.ui.browse.PerformerGridItem
import com.gpdb.android.ui.home.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    physicalRootPath: String,
    viewModel: LibraryViewModel,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {},
    onSeriesClick: (String) -> Unit = {},
    onPerformerClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 每次进入页面时刷新数据，确保实时同步收藏状态
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            var isSearchOpen by remember { mutableStateOf(false) }
            Column {
                TopAppBar(
                    title = {
                        if (isSearchOpen) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.updateSearch(it) },
                                placeholder = { Text("在当前页面搜索...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("我的库")
                        }
                    },
                    actions = {
                        if (isSearchOpen) {
                            IconButton(onClick = { isSearchOpen = false; viewModel.updateSearch("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Close Search")
                            }
                        } else {
                            IconButton(onClick = { isSearchOpen = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }
                )
                ScrollableTabRow(
                    selectedTabIndex = uiState.currentTab.ordinal,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Tab(selected = uiState.currentTab == LibraryTab.FAV_MOVIES, onClick = { viewModel.setTab(LibraryTab.FAV_MOVIES) }, text = { Text("收藏影片") })
                    Tab(selected = uiState.currentTab == LibraryTab.WISHLIST, onClick = { viewModel.setTab(LibraryTab.WISHLIST) }, text = { Text("想看") })
                    Tab(selected = uiState.currentTab == LibraryTab.WATCHED, onClick = { viewModel.setTab(LibraryTab.WATCHED) }, text = { Text("已看") })
                    Tab(selected = uiState.currentTab == LibraryTab.FAV_PERFORMERS, onClick = { viewModel.setTab(LibraryTab.FAV_PERFORMERS) }, text = { Text("收藏演员") })
                    Tab(selected = uiState.currentTab == LibraryTab.FAV_SERIES, onClick = { viewModel.setTab(LibraryTab.FAV_SERIES) }, text = { Text("收藏系列") })
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.currentTab == LibraryTab.FAV_PERFORMERS) {
                        items(
                            items = uiState.performers,
                            key = { it.id ?: it.hashCode() },
                            contentType = { "performer" }
                        ) { p ->
                            PerformerGridItem(performer = p, physicalRootPath = physicalRootPath, onClick = { onPerformerClick(p.id ?: 0L) })
                        }
                    } else if (uiState.currentTab == LibraryTab.FAV_SERIES) {
                        items(
                            items = uiState.series,
                            key = { it.rootTitle },
                            contentType = { "series" }
                        ) { s ->
                            com.gpdb.android.ui.browse.SeriesGridItem(series = s, physicalRootPath = physicalRootPath, onClick = { onSeriesClick(s.rootTitle) })
                        }
                    } else {
                        items(
                            items = uiState.movies,
                            key = { it.id ?: it.hashCode() },
                            contentType = { "movie" }
                        ) { m ->
                            MovieGridItem(movie = m, physicalRootPath = physicalRootPath, onClick = { onMovieClick(m.id ?: 0L) },
    onStudioClick = { studio -> onStudioClick(studio) }
)
                        }
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
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }
        }
    }
}
