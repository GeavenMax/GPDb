package com.gpdb.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.ui.components.EpisodeListItem
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
                Column(modifier = Modifier.fillMaxSize()) {
                    // ★ 厂牌历史档案与风格深度解析专栏 (181 家核心厂牌深度专栏)
                    if (!uiState.descriptionZh.isNullOrBlank()) {
                        var isExpanded by rememberSaveable { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "厂牌历史档案与风格深度解析专栏",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "深度解析",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                SelectionContainer {
                                    Text(
                                        text = uiState.descriptionZh ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 19.sp,
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if ((uiState.descriptionZh?.length ?: 0) > 100) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isExpanded) "收起专栏" else "展开全文阅读",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .clickable { isExpanded = !isExpanded }
                                            .padding(top = 2.dp, bottom = 2.dp, start = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
                    val tabs = listOf("发行作品 (${uiState.movies.size})", "发行分集 (${uiState.episodes.size})")

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title) }
                            )
                        }
                    }

                    if (selectedTabIndex == 0) {
                        LazyVerticalGrid(
                            state = movieGridState,
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
                                    physicalRootPath = physicalRootPath,
                                    onClick = { onMovieClick(movie.id ?: return@MovieGridItem) },
                                    onStudioClick = { studio -> onStudioClick(studio) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = episodeListState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
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
                                    modifier = Modifier.padding(horizontal = 16.dp),
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
