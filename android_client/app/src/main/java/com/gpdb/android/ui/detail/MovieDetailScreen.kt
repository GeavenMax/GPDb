package com.gpdb.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.TranslationSection
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: Long,
    physicalRootPath: String,
    viewModel: MovieDetailViewModel,
    onBackClick: () -> Unit,
    onPerformerClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onDirectorClick: (String) -> Unit,
    onStudioClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(movieId) {
        viewModel.loadMovie(movieId)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleFavorite() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "收藏",
                    tint = if (uiState.isFavorite) Color.Red else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("影片详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
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
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val detail = uiState.movieDetail ?: return@Box
                val movie = detail.movie

                val coverUrls = listOfNotNull(movie.coverFull, movie.coverBack)
                val validCovers = if (coverUrls.isNotEmpty()) coverUrls else listOf("image_cache/Covers/${movie.id}.jpg")

                var showFullImageIndex by remember { mutableStateOf<Int?>(null) }

                if (showFullImageIndex != null) {
                    val coverDataList = validCovers.map { url ->
                        val relPath = url.toImageCachePath() ?: "image_cache/Covers/${movie.id}.jpg"
                        GpdbImageData(relPath, physicalRootPath, url)
                    }
                    com.gpdb.android.ui.components.ZoomableImageDialog(
                        images = coverDataList,
                        initialIndex = showFullImageIndex!!,
                        onDismiss = { showFullImageIndex = null }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 海报多图轮播
                    val pagerState = rememberPagerState(pageCount = { validCovers.size })

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val url = validCovers[page]
                            GpdbAsyncImage(
                                url = url,
                                physicalRootPath = physicalRootPath,
                                contentDescription = "${movie.title} - 海报 $page",
                                fallbackEntityId = movie.id,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showFullImageIndex = page }
                            )
                        }

                        // 底部渐变遮罩
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                    )
                                )
                        )

                        // 页面指示器
                        if (validCovers.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(validCovers.size) { iteration ->
                                    val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .size(6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 影片元数据区域
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = movie.titleZh ?: movie.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (movie.titleZh != null && movie.titleZh != movie.title) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            movie.releaseYear?.let { year ->
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(year.toString(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }

                            movie.durationMins?.let { duration ->
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = "$duration 分钟",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            uiState.directors.forEach { director ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable { onDirectorClick(director) }
                                ) {
                                    Text(
                                        text = "导演: $director",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            movie.studioName?.let { studio ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.clickable { onStudioClick(studio) }
                                ) {
                                    Text(
                                        text = "片商: $studio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            uiState.seriesName?.let { series ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        onSeriesClick("${movie.studioName ?: ""}|||${series}")
                                    }
                                ) {
                                    Text(
                                        text = "属于 $series 系列",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.clickable {
                                    val encodedName = java.net.URLEncoder.encode(movie.title, "UTF-8")
                                    uriHandler.openUri("https://bt4gprx.com/search?q=$encodedName")
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "BT4G",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // 剧情简介与统一 AI 翻译
                        val originalSummary = movie.descriptionZh?.takeIf { it.isNotBlank() } ?: movie.description
                        if (!originalSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            TranslationSection(
                                originalSummary = originalSummary,
                                translatedSummary = uiState.translatedSummary,
                                isLoading = uiState.translationLoading,
                                errorMessage = uiState.translationError,
                                onTranslateClick = { viewModel.translateSummary(it) }
                            )
                        }

                        // 出演演员列表
                        if (detail.performers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "出演演员",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(
                                    items = detail.performers,
                                    key = { it.id ?: it.hashCode() },
                                    contentType = { "performer" }
                                ) { performer ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(80.dp)
                                            .clickable { performer.id?.let { onPerformerClick(it) } }
                                    ) {
                                        GpdbAsyncImage(
                                            url = performer.imageUrl,
                                            physicalRootPath = physicalRootPath,
                                            contentDescription = performer.name,
                                            fallbackEntityId = performer.id,
                                            defaultFolder = "Performers",
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.TopCenter,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = performer.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // 相关分集列表 (统一使用 EpisodeListItem)
                        if (uiState.episodes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "相关分集 (${uiState.episodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.episodes.forEach { episode ->
                                    EpisodeListItem(
                                        episodeId = episode.id,
                                        title = episode.title,
                                        thumbnailUrl = episode.thumbnailUrl,
                                        physicalRootPath = physicalRootPath,
                                        releaseDate = episode.releaseDate,
                                        onClick = { episode.id?.let { onEpisodeClick(it) } }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
