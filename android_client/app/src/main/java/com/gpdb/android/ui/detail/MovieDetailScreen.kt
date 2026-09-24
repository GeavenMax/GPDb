package com.gpdb.android.ui.detail

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
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
    onStudioClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current


    LaunchedEffect(movieId) {
        viewModel.loadMovie(movieId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("影片详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val detail = uiState.movieDetail!!
                val movie = detail.movie

                // 收集所有可用的海报路径 (去除 coverIcon，仅使用高清封面和封底)
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
                    // 多图滑动条
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
                            val relativePath = url.toImageCachePath() ?: "image_cache/Covers/${movie.id}.jpg"
                            val coverData = remember(url, physicalRootPath) {
                                GpdbImageData(relativePath, physicalRootPath, url)
                            }
                            
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverData)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "${movie.title} - 海报 $page",
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.Center,
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
                        
                        // 页面指示器 (仅在有多张图时显示)
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

                    // 元数据区
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = movie.titleZh ?: movie.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                                                @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            if (uiState.directors.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable {
                                        uiState.directors.firstOrNull()?.let { onDirectorClick(it) }
                                    }
                                ) {
                                    Text(
                                        text = "导演: ${uiState.directors.joinToString(", ")}",
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
                                    modifier = Modifier.clickable {
                                        onStudioClick(studio)
                                    }
                                ) {
                                    Text(
                                        text = "片商: $studio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val displaySummary = movie.descriptionZh?.takeIf { it.isNotBlank() } ?: movie.description
                        displaySummary?.takeIf { it.isNotBlank() }?.let { summary ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "剧情简介",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 演员列表区
                        if (detail.performers.isNotEmpty()) {
                            Text(
                                text = "出演演员",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(detail.performers, key = { it.id ?: it.hashCode() }) { performer ->
                                    val perfRelativePath = performer.imageUrl.toImageCachePath()
                                    val perfImageData = remember(performer.id, physicalRootPath) {
                                        GpdbImageData(
                                            relativePath = perfRelativePath ?: "image_cache/Performers/${performer.id}.jpg",
                                            physicalRoot = physicalRootPath,
                                            fallbackUrl = performer.imageUrl
                                        )
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(80.dp)
                                            .clickable { performer.id?.let { onPerformerClick(it) } }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(perfImageData)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = performer.name,
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.Center, // 修复由于默认居中裁切导致的头部被切掉的问题
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
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MovieUserActionBar(
    rating: Float?,
    status: String?,
    isFavorite: Boolean,
    onRatingChanged: (Float) -> Unit,
    onStatusChanged: (String?) -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("我的评分", style = MaterialTheme.typography.titleSmall)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = rating ?: 0f,
                        onValueChange = { onRatingChanged(it) },
                        valueRange = 0f..5f,
                        steps = 9,
                        modifier = Modifier.width(150.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (rating != null && rating > 0f) String.format("%.1f", rating) else "-", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = status == "wishlist",
                        onClick = { onStatusChanged(if (status == "wishlist") null else "wishlist") },
                        label = { Text("想看") }
                    )
                    FilterChip(
                        selected = status == "watched",
                        onClick = { onStatusChanged(if (status == "watched") null else "watched") },
                        label = { Text("已看") }
                    )
                }
                
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
