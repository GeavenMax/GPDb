package com.gpdb.android.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.ui.components.AdaptiveLogoContainer
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.MovieGridItem
import com.gpdb.android.util.I18n

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
                            val navMonogram = uiState.nameZh?.firstOrNull()?.uppercase() ?: studioName.firstOrNull()?.uppercase() ?: "S"
                            AdaptiveLogoContainer(
                                logoUrl = uiState.logoUrl,
                                monogram = navMonogram,
                                physicalRootPath = physicalRootPath,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(2.dp),
                                modifier = Modifier.size(width = 46.dp, height = 32.dp)
                            )
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
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            StudioHeroHeader(
                                uiState = uiState,
                                physicalRootPath = physicalRootPath
                            )
                        }

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
                        item {
                            StudioHeroHeader(
                                uiState = uiState,
                                physicalRootPath = physicalRootPath
                            )
                        }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudioHeroHeader(
    uiState: StudioDetailUiState,
    physicalRootPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val primaryTitle = uiState.nameZh?.takeIf { it.isNotBlank() } ?: uiState.studioName
    val secondaryTitle = if (uiState.nameZh != null && uiState.nameZh != uiState.studioName) uiState.studioName else null
    val monogram = primaryTitle.firstOrNull()?.uppercase() ?: uiState.studioName.firstOrNull()?.uppercase() ?: "S"

    val hasBanner = !uiState.bannerUrl.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
        ) {
            // 1. Background Banner Layer
            if (hasBanner) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    // Ambient blurred background layer
                    GpdbAsyncImage(
                        url = uiState.bannerUrl,
                        physicalRootPath = physicalRootPath,
                        defaultFolder = "Logos",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(14.dp)
                    )
                    // Sharp artwork right aligned
                    GpdbAsyncImage(
                        url = uiState.bannerUrl,
                        physicalRootPath = physicalRootPath,
                        defaultFolder = "Logos",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 40.dp)
                    )
                    // Scrim gradient overlays for high contrast text/buttons
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.92f),
                                        Color.Black.copy(alpha = 0.78f),
                                        Color.Black.copy(alpha = 0.40f)
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.30f),
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }

            // 2. Foreground Content Layer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Large Logo Badge Card
                    AdaptiveLogoContainer(
                        logoUrl = uiState.logoUrl,
                        monogram = monogram,
                        physicalRootPath = physicalRootPath,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(6.dp),
                        elevation = 4.dp,
                        modifier = Modifier.size(72.dp)
                    )

                    // Titles & counts
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = primaryTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasBanner) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (secondaryTitle != null) {
                            Text(
                                text = secondaryTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasBanner) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (hasBanner) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${uiState.movies.size} ${I18n.string("common.works")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasBanner) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (uiState.episodes.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasBanner) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${uiState.episodes.size} ${I18n.string("common.episodesCount")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Normal,
                                        color = if (hasBanner) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Official Website Action Button (if websiteUrl present)
                if (!uiState.websiteUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val rawUrl = uiState.websiteUrl.trim()
                    val targetUrl = remember(rawUrl) {
                        if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
                            rawUrl
                        } else {
                            "https://$rawUrl"
                        }
                    }

                    val copiedToastText = I18n.string("studio.copiedWebsite")

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasBanner) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (hasBanner) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    try {
                                        uriHandler.openUri(targetUrl)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开浏览器: $targetUrl", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Website", targetUrl))
                                    Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = if (hasBanner) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = I18n.string("studio.officialWebsite"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasBanner) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
