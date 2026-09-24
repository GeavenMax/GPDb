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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.browse.SeriesListContent
import com.gpdb.android.ui.browse.SeriesListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {},
    onPerformerClick: (Long) -> Unit,
    onRemountClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSeriesClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val seriesViewModel: SeriesListViewModel = viewModel()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("GPDb") },
                    actions = {
                        var showSortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("按收录顺序") },
                                onClick = {
                                    showSortMenu = false
                                    if (uiState.sortByYear) viewModel.toggleSortOrder()
                                },
                                trailingIcon = { if (!uiState.sortByYear) Icon(Icons.Default.Check, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("按发行年份") },
                                onClick = {
                                    showSortMenu = false
                                    if (!uiState.sortByYear) viewModel.toggleSortOrder()
                                },
                                trailingIcon = { if (uiState.sortByYear) Icon(Icons.Default.Check, null) }
                            )
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                    }
                )
                TabRow(selectedTabIndex = uiState.homeTab.ordinal) {
                    Tab(
                        selected = uiState.homeTab == HomeTab.ALL_MOVIES,
                        onClick = { viewModel.setHomeTab(HomeTab.ALL_MOVIES) },
                        text = { Text("全部影片") }
                    )
                    Tab(
                        selected = uiState.homeTab == HomeTab.SERIES,
                        onClick = { viewModel.setHomeTab(HomeTab.SERIES) },
                        text = { Text("系列") }
                    )
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
                        val text = if (status is MountStatus.Mounting) status.stepText else "等待挂载..."
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
                        Text(status.title, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(status.detail, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRemountClick) {
                            Text("重试")
                        }
                    }
                }
                is MountStatus.Ready -> {
                    if (uiState.homeTab == HomeTab.ALL_MOVIES) {
                        val gridState = androidx.compose.runtime.saveable.rememberSaveable(
                            saver = androidx.compose.foundation.lazy.grid.LazyGridState.Saver,
                            key = "home_all_movies_grid"
                        ) {
                            androidx.compose.foundation.lazy.grid.LazyGridState()
                        }
                        LazyVerticalGrid(
                            state = gridState,                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.movies, key = { it.id ?: it.hashCode() }) { movie ->
                                MovieGridItem(
                                    movie = movie,
                                    physicalRootPath = uiState.physicalRootPath,
                                    onClick = { onMovieClick(movie.id ?: 0) }
                                ,
    onStudioClick = { studio -> onStudioClick(studio) }
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
                    } else {
                        // Series Tab
                        SeriesListContent(
                            viewModel = seriesViewModel,
                            onSeriesClick = onSeriesClick,
                            physicalRootPath = uiState.physicalRootPath
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun MovieGridItem(
    movie: MovieEntity,
    physicalRootPath: String,
    onClick: () -> Unit,
    onStudioClick: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    val relativePath = (movie.coverFull ?: movie.coverIcon).toImageCachePath()
    val imageData = remember(movie.id, physicalRootPath) {
        GpdbImageData(
            relativePath = relativePath ?: "image_cache/Covers/${movie.id}.jpg",
            physicalRoot = physicalRootPath,
            fallbackUrl = movie.coverFull ?: movie.coverIcon
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageData)
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        movie.releaseYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        movie.durationMins?.let { mins ->
                            Text(
                                text = "${mins}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(8.dp)
            ) {
                val displayTitle = movie.titleZh ?: movie.title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )

                if (movie.titleZh != null && movie.title.isNotBlank()) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                movie.studioName?.let { studio ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(if (onStudioClick != null) Modifier.clickable { onStudioClick(studio) } else Modifier)
                    ) {
                        Text(
                            text = studio,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }
        }
    }
}
