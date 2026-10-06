package com.gpdb.android.ui.homefeed

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.data.repository.HomeAnniversaryItem
import com.gpdb.android.data.repository.HomeFeaturedPerformer
import com.gpdb.android.data.repository.HomeSpotlightMovie
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.MovieGridItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    viewModel: HomeFeedViewModel,
    physicalRootPath: String,
    onMovieClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onPerformerClick: (Long) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    onNavigateToMovies: () -> Unit = {},
    onNavigateToEpisodes: () -> Unit = {},
    onNavigateToPerformers: () -> Unit = {},
    onNavigateToStudios: () -> Unit = {}
) {
    val feed by viewModel.feedData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    val appSettingsRepo = remember { com.gpdb.android.data.settings.AppSettingsRepository(context) }
    val privacyBlurEnabled by appSettingsRepo.screenshotPrivacyBlurEnabledFlow.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(com.gpdb.android.util.I18n.string("home.feedTitle", defaultVal = "GPDb 探索"), fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    val currentAppLang = com.gpdb.android.util.LocalAppLanguage.current
                    IconButton(
                        onClick = {
                            scope.launch {
                                val nextState = !privacyBlurEnabled
                                appSettingsRepo.setScreenshotPrivacyBlurEnabled(nextState)
                                val msg = if (nextState) {
                                    com.gpdb.android.util.I18n.t("home.privacyOn", currentAppLang, defaultVal = "防窥模式已开启")
                                } else {
                                    com.gpdb.android.util.I18n.t("home.privacyOff", currentAppLang, defaultVal = "防窥模式已关闭")
                                }
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (privacyBlurEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (privacyBlurEnabled) {
                                com.gpdb.android.util.I18n.string("home.privacyActiveTooltip", defaultVal = "防窥模式 (已开启)")
                            } else {
                                com.gpdb.android.util.I18n.string("home.privacyInactiveTooltip", defaultVal = "防窥模式 (已关闭)")
                            },
                            tint = if (privacyBlurEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.loadFeed() }) {
                        Icon(Icons.Default.Refresh, contentDescription = com.gpdb.android.util.I18n.string("common.refresh"))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // 若各板块暂无数据，显示一键重试卡片
                if (feed.spotlightMovies.isEmpty() && feed.onThisDay.isEmpty() && feed.starSpotlight.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text(com.gpdb.android.util.I18n.string("home.emptyFeed", defaultVal = "探索发现流尚未加载"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(com.gpdb.android.util.I18n.string("home.emptyFeedDesc", defaultVal = "数据库正在建立索引或后台挂载中，点击下方按钮立即重新加载"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { viewModel.loadFeed() },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(com.gpdb.android.util.I18n.string("home.reloadFeed", defaultVal = "立即刷新发现流"))
                            }
                        }
                    }
                }

                // 1. 焦点推荐轮播 (Hero Spotlight)
                if (feed.spotlightMovies.isNotEmpty()) {
                    SpotlightSection(
                        movies = feed.spotlightMovies,
                        physicalRootPath = physicalRootPath,
                        onMovieClick = onMovieClick
                    )
                }

                // 2. 往年今日 · 经典首映 (On This Day in History)
                if (feed.onThisDay.isNotEmpty()) {
                    OnThisDaySection(
                        items = feed.onThisDay,
                        physicalRootPath = physicalRootPath,
                        onMovieClick = onMovieClick,
                        onEpisodeClick = onEpisodeClick
                    )
                }

                // 3. 今日星光 · 标志面孔 (Star Spotlight)
                if (feed.starSpotlight.isNotEmpty()) {
                    StarSpotlightSection(
                        performers = feed.starSpotlight,
                        physicalRootPath = physicalRootPath,
                        onPerformerClick = onPerformerClick,
                        onViewAllClick = onNavigateToPerformers
                    )
                }

                // 4. 经典系列大放送 (Series Showcase)
                if (feed.seriesList.isNotEmpty()) {
                    SeriesShowcaseSection(
                        seriesList = feed.seriesList,
                        physicalRootPath = physicalRootPath,
                        onSeriesClick = onSeriesClick
                    )
                }

                // 5. 随心探索 · 盲盒发现 (Lucky Discovery)
                if (feed.luckyMovies.isNotEmpty()) {
                    LuckyDiscoverySection(
                        movies = feed.luckyMovies,
                        isLoading = feed.isLuckyLoading,
                        physicalRootPath = physicalRootPath,
                        onMovieClick = onMovieClick,
                        onRefresh = { viewModel.refreshLuckyMovies() }
                    )
                }

                // 6. 影库纵览与快捷探索 (Library Quick Stats)
                QuickStatsSection(
                    totalMovies = feed.totalMovies,
                    totalEpisodes = feed.totalEpisodes,
                    totalPerformers = feed.totalPerformers,
                    totalStudios = feed.totalStudios,
                    onNavigateToMovies = onNavigateToMovies,
                    onNavigateToEpisodes = onNavigateToEpisodes,
                    onNavigateToPerformers = onNavigateToPerformers,
                    onNavigateToStudios = onNavigateToStudios
                )
            }
        }
    }
}

/**
 * 焦点推荐轮播 Banner
 */
@Composable
private fun SpotlightSection(
    movies: List<HomeSpotlightMovie>,
    physicalRootPath: String,
    onMovieClick: (Long) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { movies.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        val currentMovie = movies.getOrNull(pagerState.currentPage) ?: movies.first()

        // 动态氛围背景虚化 (Ambient Glow)
        GpdbAsyncImage(
            url = currentMovie.coverFull,
            physicalRootPath = physicalRootPath,
            fallbackEntityId = currentMovie.id,
            defaultFolder = "Covers",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(40.dp)
        )

        // 渐变蒙层
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.padding(16.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val movie = movies[page]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 海报卡片
                    Card(
                        modifier = Modifier
                            .width(115.dp)
                            .aspectRatio(0.68f)
                            .clickable { onMovieClick(movie.id) },
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        GpdbAsyncImage(
                            url = movie.coverFull,
                            physicalRootPath = physicalRootPath,
                            contentDescription = movie.title,
                            fallbackEntityId = movie.id,
                            defaultFolder = "Covers",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 信息区
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 顶部徽章
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    com.gpdb.android.util.I18n.string("home.spotlight", defaultVal = "焦点推荐"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            movie.releaseYear?.let { year ->
                                Text(
                                    year.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            movie.rating?.let { rating ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                                    Text(rating, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 标题
                        val currentLang = com.gpdb.android.util.LocalAppLanguage.current
                        val titlePrimary = if (currentLang.isChinese) (movie.titleZh ?: movie.title) else movie.title
                        Text(
                            text = titlePrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        movie.studioName?.let { studio ->
                            Text(
                                text = studio,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        // 剧情简介
                        val desc = if (currentLang.isChinese) (movie.descriptionZh ?: movie.description) else (movie.description ?: movie.descriptionZh)
                        if (!desc.isNullOrBlank()) {
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }

                        // 按钮组
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(
                                onClick = { onMovieClick(movie.id) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(com.gpdb.android.util.I18n.string("home.exploreNow", defaultVal = "立即探索"), style = MaterialTheme.typography.labelMedium)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val nextPage = (pagerState.currentPage + 1) % movies.size
                                        pagerState.animateScrollToPage(nextPage)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(com.gpdb.android.util.I18n.string("home.switchOneMovie", defaultVal = "换一部"), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // 轮播圆点指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(minOf(movies.size, 8)) { index ->
                    val isSelected = pagerState.currentPage % minOf(movies.size, 8) == index
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(width = if (isSelected) 14.dp else 5.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

/**
 * 往年今日 · 经典首映
 */
@Composable
private fun OnThisDaySection(
    items: List<HomeAnniversaryItem>,
    physicalRootPath: String,
    onMovieClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit
) {
    val currentLang = com.gpdb.android.util.LocalAppLanguage.current
    val todayFormatted = remember(currentLang) {
        val pattern = if (currentLang.isChinese) "MM月dd日" else "MMM d"
        val locale = if (currentLang.isChinese) Locale.CHINA else Locale.US
        val sdf = SimpleDateFormat(pattern, locale)
        sdf.format(Date())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(com.gpdb.android.util.I18n.string("home.onThisDayFull", defaultVal = "往年今日 · 经典首映"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        todayFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.episodeId }) { item ->
                Card(
                    modifier = Modifier
                        .width(200.dp)
                        .clickable {
                            if (item.movieId != null) onMovieClick(item.movieId)
                            else onEpisodeClick(item.episodeId)
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                            GpdbAsyncImage(
                                url = item.coverFull,
                                physicalRootPath = physicalRootPath,
                                contentDescription = item.episodeTitle,
                                fallbackEntityId = item.episodeId,
                                defaultFolder = "Episodes",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (item.yearsAgo > 0) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    val yearsText = com.gpdb.android.util.I18n.string("home.yearsAgo", mapOf("years" to item.yearsAgo.toString()), defaultVal = "${item.yearsAgo}年前")
                                    Text(
                                        yearsText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val defaultEpisodeTitle = com.gpdb.android.util.I18n.string("home.episodeScene", defaultVal = "经典场景")
                            val titleText = if (currentLang.isChinese) {
                                item.episodeTitle ?: item.movieTitleZh ?: item.movieTitle ?: defaultEpisodeTitle
                            } else {
                                item.episodeTitle ?: item.movieTitle ?: item.movieTitleZh ?: defaultEpisodeTitle
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.studioName ?: com.gpdb.android.util.I18n.string("common.movie", defaultVal = "精选作品"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = item.releaseDate ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 今日星光 · 标志面孔
 */
@Composable
private fun StarSpotlightSection(
    performers: List<HomeFeaturedPerformer>,
    physicalRootPath: String,
    onPerformerClick: (Long) -> Unit,
    onViewAllClick: () -> Unit
) {
    if (performers.isEmpty()) return

    val currentLang = com.gpdb.android.util.LocalAppLanguage.current
    val brokenAvatarIds = remember { mutableStateListOf<Long>() }
    val displayPerformers = remember(performers, brokenAvatarIds.size) {
        performers.filter { !it.imageUrl.isNullOrBlank() && it.id !in brokenAvatarIds }
    }

    if (displayPerformers.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(com.gpdb.android.util.I18n.string("home.curatedPerformersTitle", defaultVal = "名流演员档案"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onViewAllClick) {
                Text(com.gpdb.android.util.I18n.string("common.all", defaultVal = "全部"), style = MaterialTheme.typography.labelMedium)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(displayPerformers, key = { it.id }) { perf ->
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .clickable { onPerformerClick(perf.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        GpdbAsyncImage(
                            url = perf.imageUrl,
                            physicalRootPath = physicalRootPath,
                            contentDescription = perf.name,
                            fallbackEntityId = perf.id,
                            defaultFolder = "Pornstars",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                            onError = {
                                if (perf.id !in brokenAvatarIds) {
                                    brokenAvatarIds.add(perf.id)
                                }
                            }
                        )
                    }

                    Text(
                        text = perf.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        val filmsUnit = com.gpdb.android.util.I18n.string("common.filmsCount")
                        val worksUnit = "${perf.worksCount} $filmsUnit"
                        Text(
                            worksUnit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 经典系列大放送
 */
@Composable
private fun SeriesShowcaseSection(
    seriesList: List<com.gpdb.android.data.db.entities.SeriesCollectionEntity>,
    physicalRootPath: String,
    onSeriesClick: (String) -> Unit
) {
    val currentLang = com.gpdb.android.util.LocalAppLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(com.gpdb.android.util.I18n.string("home.classicSeries", defaultVal = "经典系列大放送"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(seriesList, key = { it.id ?: it.rootTitle }) { series ->
                Card(
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { onSeriesClick(series.rootTitle) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                            com.gpdb.android.ui.components.SeriesCollageCover(
                                sampleCoversRaw = series.sampleCovers,
                                singleFallbackUrl = series.coverUrl,
                                physicalRootPath = physicalRootPath,
                                title = series.rootTitle,
                                modifier = Modifier.fillMaxSize()
                            )

                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(topStart = 8.dp),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                val filmsUnit = com.gpdb.android.util.I18n.string("common.filmsCount")
                                val countText = "${series.movieCount} $filmsUnit"
                                Text(
                                    countText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = series.rootTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            series.studioName?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 随心探索 · 盲盒发现
 */
@Composable
private fun LuckyDiscoverySection(
    movies: List<com.gpdb.android.data.db.entities.MovieEntity>,
    isLoading: Boolean,
    physicalRootPath: String,
    onMovieClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(targetValue = rotationAngle, label = "dice_spin")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Casino, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(com.gpdb.android.util.I18n.string("home.luckyDiscovery", defaultVal = "随心探索 · 盲盒发现"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    rotationAngle += 360f
                    onRefresh()
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).rotate(animatedRotation)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(com.gpdb.android.util.I18n.string("home.luckyShuffle", defaultVal = "换一批"), style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            movies.take(3).forEach { movie ->
                Box(modifier = Modifier.weight(1f)) {
                    MovieGridItem(
                        movie = movie,
                        physicalRootPath = physicalRootPath,
                        onClick = { movie.id?.let(onMovieClick) }
                    )
                }
            }
        }

        if (movies.size > 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                movies.drop(3).take(3).forEach { movie ->
                    Box(modifier = Modifier.weight(1f)) {
                        MovieGridItem(
                            movie = movie,
                            physicalRootPath = physicalRootPath,
                            onClick = { movie.id?.let(onMovieClick) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 影库纵览与快捷探索磁贴
 */
@Composable
private fun QuickStatsSection(
    totalMovies: Int,
    totalEpisodes: Int,
    totalPerformers: Int,
    totalStudios: Int,
    onNavigateToMovies: () -> Unit,
    onNavigateToEpisodes: () -> Unit,
    onNavigateToPerformers: () -> Unit,
    onNavigateToStudios: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(com.gpdb.android.util.I18n.string("home.quickExploreSection", defaultVal = "影库纵览与快捷探索"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = com.gpdb.android.util.I18n.string("home.statMoviesLabel", defaultVal = "影视长片"),
                count = totalMovies,
                icon = Icons.Default.Movie,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onNavigateToMovies,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = com.gpdb.android.util.I18n.string("home.statEpisodesLabel", defaultVal = "独立分集"),
                count = totalEpisodes,
                icon = Icons.Default.VideoLibrary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onNavigateToEpisodes,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = com.gpdb.android.util.I18n.string("home.statPerformersLabel", defaultVal = "入库演员"),
                count = totalPerformers,
                icon = Icons.Default.People,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onNavigateToPerformers,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = com.gpdb.android.util.I18n.string("home.statStudiosLabel", defaultVal = "收录片商"),
                count = totalStudios,
                icon = Icons.Default.Business,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onNavigateToStudios,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = contentColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            }
            Text(
                text = String.format(Locale.getDefault(), "%,d", count),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}
