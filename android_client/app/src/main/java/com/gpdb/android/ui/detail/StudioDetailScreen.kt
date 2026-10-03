package com.gpdb.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDetailScreen(
    studioName: String,
    physicalRootPath: String,
    viewModel: StudioDetailViewModel,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(studioName) {
        viewModel.loadStudio(studioName)
    }

    val movieGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val episodeListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!uiState.logoUrl.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(width = 46.dp, height = 32.dp)
                            ) {
                                GpdbAsyncImage(
                                    url = uiState.logoUrl,
                                    physicalRootPath = physicalRootPath,
                                    defaultFolder = "Logos",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.padding(2.dp).fillMaxSize()
                                )
                            }
                        }
                        Column {
                            val mainTitle = uiState.nameZh?.takeIf { it.isNotBlank() } ?: studioName
                            Text(
                                text = mainTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!uiState.nameZh.isNullOrBlank() && uiState.nameZh != studioName) {
                                Text(
                                    text = studioName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (uiState.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf("发行作品 (${uiState.movies.size})", "发行分集 (${uiState.episodes.size})")

                if (selectedTabIndex == 0) {
                    LazyVerticalGrid(
                        state = movieGridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!uiState.descriptionZh.isNullOrBlank()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                StudioIntroductionCard(
                                    descriptionZh = uiState.descriptionZh ?: ""
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StudioTabRow(
                                tabs = tabs,
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                            )
                        }

                        if (uiState.movies.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无发行作品记录",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.movies,
                                key = { it.id ?: it.hashCode() },
                                contentType = { "movie" }
                            ) { movie ->
                                MovieGridItem(
                                    movie = movie,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onMovieClick(movie.id ?: return@MovieGridItem) },
                                    onStudioClick = { studio -> onStudioClick(studio) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = episodeListState,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (!uiState.descriptionZh.isNullOrBlank()) {
                            item {
                                StudioIntroductionCard(
                                    descriptionZh = uiState.descriptionZh ?: ""
                                )
                            }
                        }

                        item {
                            StudioTabRow(
                                tabs = tabs,
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                            )
                        }

                        if (uiState.episodes.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无发行分集记录",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.episodes,
                                key = { it.id ?: 0L },
                                contentType = { "episode" }
                            ) { episode ->
                                EpisodeListItem(
                                    episodeId = episode.id,
                                    title = episode.title,
                                    thumbnailUrl = episode.thumbnailUrl,
                                    physicalRootPath = physicalRootPath,
                                    releaseDate = episode.releaseDate,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onClick = { episode.id?.let { onEpisodeClick(it) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioIntroductionCard(
    descriptionZh: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "厂牌介绍",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SelectionContainer {
                Text(
                    text = descriptionZh,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StudioTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(title) }
            )
        }
    }
}
