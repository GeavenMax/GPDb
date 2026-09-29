package com.gpdb.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.ShareCardData
import com.gpdb.android.ui.components.ShareCardDialog
import com.gpdb.android.ui.components.TranslationSection
import com.gpdb.android.ui.components.ZoomableImageDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    episodeId: Long,
    physicalRootPath: String,
    viewModel: EpisodeDetailViewModel,
    onBackClick: () -> Unit,
    onPerformerClick: (Long) -> Unit,
    onMovieClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showShareCard by remember { mutableStateOf(false) }

    LaunchedEffect(episodeId) {
        viewModel.loadEpisode(episodeId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分集档案") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showShareCard = true }) {
                        Icon(Icons.Default.Share, contentDescription = "卡片分享")
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
                val detail = uiState.episodeDetail ?: return@Box
                val episode = detail.episode
                val parentMovie = uiState.parentMovie

                var showFullImage by remember { mutableStateOf(false) }

                if (showFullImage) {
                    val relPath = episode.thumbnailUrl.toImageCachePath() ?: "image_cache/Episodes/${episode.id}.jpg"
                    val imageData = GpdbImageData(relPath, physicalRootPath, episode.thumbnailUrl)
                    ZoomableImageDialog(
                        images = listOf(imageData),
                        onDismiss = { showFullImage = false }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 分集缩略大图
                    GpdbAsyncImage(
                        url = episode.thumbnailUrl,
                        physicalRootPath = physicalRootPath,
                        contentDescription = episode.title,
                        fallbackEntityId = episode.id,
                        defaultFolder = "Episodes",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(Color.Black)
                            .clickable { showFullImage = true }
                    )

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = episode.title ?: "未知分集",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        episode.releaseDate?.let { date ->
                            Text(
                                text = "发布日期: $date",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 所属影片卡片
                        if (parentMovie != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                onClick = { parentMovie.id?.let { onMovieClick(it) } },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "所属影片",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = parentMovie.titleZh ?: parentMovie.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // 分集简介与 AI 翻译 (统一组件)
                        val summary = episode.descriptionZh?.takeIf { it.isNotBlank() } ?: episode.description
                        if (!summary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            TranslationSection(
                                originalSummary = summary,
                                translatedSummary = uiState.translatedSummary,
                                isLoading = uiState.translationLoading,
                                errorMessage = uiState.translationError,
                                title = "分集简介",
                                onTranslateClick = { viewModel.translateSummary(it) }
                            )
                        }

                        // 动作短评
                        episode.actionNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "动作短评",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 参演演员列表
                        if (detail.performers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "参演演员",
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

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showShareCard && uiState.episodeDetail != null) {
        val detail = uiState.episodeDetail!!
        val episode = detail.episode
        val parentMovie = uiState.parentMovie
        val studio = episode.studioName ?: parentMovie?.studioName
        val releaseYear = episode.releaseDate?.take(4)?.toIntOrNull() ?: parentMovie?.releaseYear
        val performers = detail.performers.map { it.name }
        val cardData = remember(episode, parentMovie, performers) {
            ShareCardData(
                title = episode.title ?: "未知分集",
                titleZh = parentMovie?.titleZh ?: parentMovie?.title,
                posterUrl = episode.thumbnailUrl,
                fallbackEntityId = episode.id,
                defaultFolder = "Episodes",
                releaseYear = releaseYear,
                studio = studio,
                director = parentMovie?.directorName,
                durationMins = null,
                rating = parentMovie?.rating,
                category = parentMovie?.category,
                performers = performers,
                description = episode.descriptionZh?.takeIf { it.isNotBlank() } ?: episode.description ?: episode.actionNotes,
                isEpisode = true
            )
        }
        ShareCardDialog(
            cardData = cardData,
            physicalRootPath = physicalRootPath,
            onDismiss = { showShareCard = false }
        )
    }
}
