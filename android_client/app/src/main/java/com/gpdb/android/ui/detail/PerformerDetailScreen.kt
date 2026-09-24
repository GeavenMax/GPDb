package com.gpdb.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.home.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformerDetailScreen(
    performerId: Long,
    physicalRootPath: String,
    viewModel: PerformerDetailViewModel,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(performerId) {
        viewModel.loadPerformer(performerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.performerDetail?.performer?.name ?: "演员档案") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val detail = uiState.performerDetail!!
                val performer = detail.performer
                val context = LocalContext.current

                val relativePath = performer.imageUrl.toImageCachePath()
                val imageData = remember(performer.id, physicalRootPath) {
                    GpdbImageData(
                        relativePath = relativePath ?: "image_cache/Performers/${performer.id}.jpg",
                        physicalRoot = physicalRootPath,
                        fallbackUrl = performer.imageUrl
                    )
                }

                var showFullImage by remember { mutableStateOf(false) }

                if (showFullImage) {
                    com.gpdb.android.ui.components.ZoomableImageDialog(
                        images = listOf(imageData),
                        onDismiss = { showFullImage = false }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // 头部档案卡
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageData)
                                .crossfade(true)
                                .build(),
                            contentDescription = performer.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter, // 修复头部被裁切
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showFullImage = true }
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = performer.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val basicDetails = listOfNotNull(
                                performer.height?.takeIf { it.isNotBlank() && it != "none available" }?.let { "身高 $it" },
                                performer.weight?.takeIf { it.isNotBlank() && it != "none available" }?.let { "体重 $it" },
                                performer.build?.takeIf { it.isNotBlank() && it != "none available" }?.let { "体型 $it" }
                            )
                            
                            @OptIn(ExperimentalLayoutApi::class)
                            if (basicDetails.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    basicDetails.forEach { detail ->
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text(
                                                text = detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 更多生理特征 (折叠)
                    val advancedDetails = listOfNotNull(
                        performer.hair?.let { "发色 (Hair)" to it },
                        performer.eyes?.let { "瞳色 (Eyes)" to it },
                        performer.facialHair?.let { "胡须 (Facial Hair)" to it },
                        performer.bodyHair?.let { "体毛 (Body Hair)" to it },
                        performer.skin?.let { "肤色 (Skin)" to it },
                        performer.dickSize?.let { "生理特征尺寸 (Dick Size)" to it },
                        performer.foreskin?.let { "包皮 (Foreskin)" to it },
                        performer.tattoos?.let { "纹身 (Tattoos)" to it }
                    ).filter { it.second.isNotBlank() && it.second != "none available" }

                    if (advancedDetails.isNotEmpty()) {
                        var isExpanded by remember { mutableStateOf(false) }
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    advancedDetails.forEach { (label, value) ->
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text(
                                                text = "$label: $value",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text(if (isExpanded) "收起详细特征 ▲" else "展开详细生理特征 ▼")
                            }
                        }
                    }
                    
                    // Buttons: BT4G 搜索 and BoyfriendTV
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.clickable {
                                val encodedName = java.net.URLEncoder.encode(performer.name, "UTF-8")
                                uriHandler.openUri("https://bt4gprx.com/search?q=$encodedName")
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BT4G 搜索",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.clickable {
                                val encodedName = java.net.URLEncoder.encode(performer.name, "UTF-8")
                                uriHandler.openUri("https://boyfriendtv.com/search/video/?q=$encodedName") // Assuming this URL or just "https://boyfriendtv.com/search/?q="
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BoyfriendTV",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    var selectedTabIndex by remember { mutableIntStateOf(0) }
                    val tabs = listOf("参演作品 (${detail.movies.size})", "参演分集 (${uiState.episodes.size})")

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(title, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (selectedTabIndex == 0) {
                        // 作品列表
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = detail.movies,
                                key = { it.id ?: 0L }
                            ) { movie ->
                                MovieGridItem(
                                    movie = movie,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { movie.id?.let { onMovieClick(it) } }
                                )
                            }
                        }
                    } else {
                        // 分集列表
                        androidx.compose.foundation.lazy.LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.episodes,
                                key = { it.id ?: 0L }
                            ) { episode ->
                                // 分集横向卡片 UI
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            episode.id?.let { onEpisodeClick(it) }
                                        },
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                                        val epRelPath = episode.thumbnailUrl?.toImageCachePath()
                                        val epImageData = remember(episode.id, physicalRootPath) {
                                            GpdbImageData(
                                                relativePath = epRelPath ?: "image_cache/Episodes/${episode.id}.jpg",
                                                physicalRoot = physicalRootPath,
                                                fallbackUrl = episode.thumbnailUrl
                                            )
                                        }
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(epImageData)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = episode.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(160.dp)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                                Text(
                                                    text = date,
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
            }
        }
    }
}
