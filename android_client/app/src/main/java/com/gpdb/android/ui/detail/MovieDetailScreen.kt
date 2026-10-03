package com.gpdb.android.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.ShareCardData
import com.gpdb.android.ui.components.ShareCardDialog
import com.gpdb.android.ui.components.TranslationSection
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showShareCard by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showShareCard = true }) {
                        Icon(Icons.Default.Share, contentDescription = "卡片分享")
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (uiState.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
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

                val extraCovers = remember(movie.coversJson) {
                    val list = mutableListOf<String>()
                    if (!movie.coversJson.isNullOrBlank()) {
                        try {
                            val jsonArray = org.json.JSONArray(movie.coversJson)
                            for (i in 0 until jsonArray.length()) {
                                val u = jsonArray.optString(i)?.trim() ?: ""
                                if (u.isNotBlank()) list.add(u)
                            }
                        } catch (_: Exception) {}
                    }
                    list
                }
                val validCovers = remember(movie.coverFull, movie.coverBack, extraCovers) {
                    val result = linkedSetOf<String>()
                    if (extraCovers.isNotEmpty()) {
                        result.addAll(extraCovers.filter { it.isNotBlank() })
                    } else {
                        movie.coverFull?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
                        movie.coverBack?.trim()?.takeIf { it.isNotEmpty() }?.let { result.add(it) }
                    }
                    if (result.isEmpty()) listOf("image_cache/Covers/${movie.id}.jpg") else result.toList()
                }

                // 容错与空框消除：若封底或额外海报在资源包中缺失导致加载失败，自动剔除，绝不显示空白框架
                val failedUrls = remember(validCovers) { mutableStateListOf<String>() }
                val effectiveCovers = remember(validCovers, failedUrls.toList()) {
                    validCovers.filterNot { it in failedUrls }
                }

                var showFullImageIndex by remember { mutableStateOf<Int?>(null) }

                // 全屏手势缩放灯箱 (Lightbox)
                if (showFullImageIndex != null && effectiveCovers.isNotEmpty()) {
                    val coverDataList = effectiveCovers.map { url ->
                        val relPath = url.toImageCachePath() ?: "image_cache/Covers/${movie.id}.jpg"
                        GpdbImageData(relPath, physicalRootPath, url)
                    }
                    com.gpdb.android.ui.components.ZoomableImageDialog(
                        images = coverDataList,
                        initialIndex = showFullImageIndex!!.coerceIn(0, effectiveCovers.lastIndex),
                        onDismiss = { showFullImageIndex = null }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ★ 封面画廊：自适应海报高度，氛围虚化底色跟随贴合 (对齐 macOS 桌面端)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .background(Color.Black)
                    ) {
                        // 动态底层氛围虚化 (matchParentSize 随前景真实高度自适应，不产生多余黑边)
                        val primaryCover = effectiveCovers.firstOrNull() ?: "image_cache/Covers/${movie.id}.jpg"
                        GpdbAsyncImage(
                            url = primaryCover,
                            physicalRootPath = physicalRootPath,
                            fallbackEntityId = movie.id,
                            defaultFolder = "Covers",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(36.dp)
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.5f))
                        )

                        // 前景封面呈现：单图自适应居中，多图（正封面/封底/变体）平铺横向滑动同时展示；失效图自动剔除绝无空框
                        if (effectiveCovers.size == 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AdaptivePosterCard(
                                    url = effectiveCovers[0],
                                    physicalRootPath = physicalRootPath,
                                    contentDescription = "${movie.title} - 海报",
                                    fallbackEntityId = movie.id,
                                    maxHeight = 340.dp,
                                    maxWidth = 260.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = 10.dp,
                                    borderStroke = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    onError = { if (effectiveCovers[0] !in failedUrls) failedUrls.add(effectiveCovers[0]) },
                                    onClick = { showFullImageIndex = 0 }
                                )
                            }
                        } else if (effectiveCovers.size > 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    effectiveCovers.forEachIndexed { index, url ->
                                        AdaptivePosterCard(
                                            url = url,
                                            physicalRootPath = physicalRootPath,
                                            contentDescription = "${movie.title} - 封面 ${index + 1}",
                                            fallbackEntityId = if (index == 0) movie.id else null,
                                            maxHeight = 280.dp,
                                            maxWidth = null,
                                            shape = RoundedCornerShape(14.dp),
                                            elevation = 8.dp,
                                            borderStroke = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                            onError = { if (url !in failedUrls) failedUrls.add(url) },
                                            onClick = { showFullImageIndex = index }
                                        )
                                    }
                                }
                            }
                        } else {
                            // 海报均失效或无海报时的优雅占位
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(280.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.TravelExplore,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "暂无海报",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // 底部渐变过渡
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                                    )
                                )
                        )
                    }

                    // 影片元数据区域
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // 元数据标签 (年份、时长、导演、片商、系列)
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
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ★ 核心操作按钮矩阵 (Action Matrix Panel) - 矩阵化收拢
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val encodedTitle = remember(movie.title) {
                                try { java.net.URLEncoder.encode(movie.title, "UTF-8") } catch (_: Exception) { "" }
                            }

                            // 1. BT4G 磁链搜索
                            SuggestionChip(
                                onClick = {
                                    if (encodedTitle.isNotBlank()) {
                                        uriHandler.openUri("https://bt4gprx.com/search?q=$encodedTitle")
                                    }
                                },
                                label = { Text("BT 磁链") },
                                icon = {
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )

                            // 2. BFTV 资料检索
                            SuggestionChip(
                                onClick = {
                                    if (encodedTitle.isNotBlank()) {
                                        uriHandler.openUri("https://www.boyfriendtv.com/search/videos/?q=$encodedTitle")
                                    }
                                },
                                label = { Text("BFTV") },
                                icon = {
                                    Icon(
                                        Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            // 3. Google 检索
                            SuggestionChip(
                                onClick = {
                                    if (encodedTitle.isNotBlank()) {
                                        uriHandler.openUri("https://www.google.com/search?q=$encodedTitle")
                                    }
                                },
                                label = { Text("Google") },
                                icon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            // 4. 分享卡片
                            SuggestionChip(
                                onClick = { showShareCard = true },
                                label = { Text("分享卡片") },
                                icon = {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )

                            // 5. 收藏电影
                            FilterChip(
                                selected = uiState.isFavorite,
                                onClick = { viewModel.toggleFavorite() },
                                label = { Text(if (uiState.isFavorite) "已收藏" else "收藏电影") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (uiState.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }

                        // ★ 剧情简介板块移至海报与操作组下方通栏展示 (Full-width Synopsis)
                        val originalSummary = movie.descriptionZh?.takeIf { it.isNotBlank() } ?: movie.description
                        if (!originalSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
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

    if (showShareCard && uiState.movieDetail != null) {
        val movie = uiState.movieDetail!!.movie
        val performers = uiState.movieDetail!!.performers.map { it.name }
        val cardData = remember(movie, performers) {
            ShareCardData(
                title = movie.title,
                titleZh = movie.titleZh,
                posterUrl = movie.coverFull ?: movie.coverIcon,
                coverBackUrl = movie.coverBack,
                fallbackEntityId = movie.id,
                defaultFolder = "Covers",
                releaseYear = movie.releaseYear,
                studio = movie.studioName,
                director = movie.directorName,
                durationMins = movie.durationMins,
                rating = movie.rating,
                category = movie.category,
                performers = performers,
                description = movie.descriptionZh?.takeIf { it.isNotBlank() } ?: movie.description,
                isEpisode = false
            )
        }
        ShareCardDialog(
            cardData = cardData,
            physicalRootPath = physicalRootPath,
            onDismiss = { showShareCard = false }
        )
    }
}

/**
 * 具有物理比例自适应贴合能力的封面海报卡片 (消除上下空白框架，自适应贴合边缘)
 */
@Composable
private fun AdaptivePosterCard(
    url: String,
    physicalRootPath: String,
    contentDescription: String,
    fallbackEntityId: Long? = null,
    maxHeight: Dp = 340.dp,
    maxWidth: Dp? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 10.dp,
    borderStroke: BorderStroke = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
    onError: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var aspectRatio by remember(url) { mutableStateOf<Float?>(null) }

    Card(
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = borderStroke,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .height(maxHeight)
            .run {
                if (maxWidth != null) widthIn(max = maxWidth) else this
            }
            .aspectRatio(aspectRatio ?: (2f / 3f), matchHeightConstraintsFirst = true)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        GpdbAsyncImage(
            url = url,
            physicalRootPath = physicalRootPath,
            contentDescription = contentDescription,
            fallbackEntityId = fallbackEntityId,
            defaultFolder = "Covers",
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val w = state.result.drawable.intrinsicWidth
                val h = state.result.drawable.intrinsicHeight
                if (w > 0 && h > 0) {
                    aspectRatio = w.toFloat() / h.toFloat()
                }
            },
            onError = onError,
            modifier = Modifier.fillMaxSize()
        )
    }
}
