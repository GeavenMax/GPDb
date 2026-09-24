package com.gpdb.android.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.ui.home.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDetailScreen(
    studioName: String,
    physicalRootPath: String,
    viewModel: StudioDetailViewModel,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit = {},
    onStudioClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(studioName) {
        viewModel.loadStudio(studioName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.studioName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (uiState.isFavorite) Color.Red else LocalContentColor.current
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
                Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    var selectedTabIndex by remember { mutableIntStateOf(0) }
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("发行影片 (${uiState.movies.size})") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("发行分集 (${uiState.episodes.size})") }
                        )
                    }
                    
                    if (selectedTabIndex == 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.movies, key = { it.id ?: it.hashCode() }) { movie ->
                                MovieGridItem(
                                    movie = movie,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onMovieClick(movie.id ?: return@MovieGridItem) },
                                    onStudioClick = { studio -> onStudioClick(studio) }
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.episodes, key = { it.id ?: 0L }) { episode ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clickable { episode.id?.let { onEpisodeClick(it) } },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                        val epRelPath = episode.thumbnailUrl?.toImageCachePath()
                                        val epImageData = remember(episode.id, physicalRootPath) {
                                            com.gpdb.android.image.GpdbImageData(
                                                relativePath = epRelPath ?: "image_cache/Episodes/${episode.id}.jpg",
                                                physicalRoot = physicalRootPath,
                                                fallbackUrl = episode.thumbnailUrl
                                            )
                                        }
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(epImageData).crossfade(true).build(),
                                            contentDescription = episode.title,
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.width(160.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = episode.title ?: "未知分集",
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            episode.releaseDate?.let { date ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
