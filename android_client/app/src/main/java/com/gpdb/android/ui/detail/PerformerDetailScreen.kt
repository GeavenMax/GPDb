package com.gpdb.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.components.EpisodeListItem
import com.gpdb.android.ui.components.MovieGridItem
import com.gpdb.android.ui.components.privacyBlurImage

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
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val detail = uiState.performerDetail!!
                val performer = detail.performer
                val pbc = uiState.pbcProfile
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                val effImageUrl = performer.imageUrl?.takeIf { it.isNotBlank() } ?: pbc?.imageUrl
                val relativePath = effImageUrl.toImageCachePath()
                val imageData = remember(performer.id, physicalRootPath, effImageUrl) {
                    GpdbImageData(
                        relativePath = relativePath ?: "image_cache/Performers/${performer.id}.jpg",
                        physicalRoot = physicalRootPath,
                        fallbackUrl = effImageUrl
                    )
                }

                var showFullImage by rememberSaveable { mutableStateOf(false) }

                if (showFullImage) {
                    com.gpdb.android.ui.components.ZoomableImageDialog(
                        images = listOf(imageData),
                        onDismiss = { showFullImage = false }
                    )
                }

                val currentLang = com.gpdb.android.util.LocalAppLanguage.current
                val isChinese = currentLang == com.gpdb.android.util.AppLanguage.ZH_CN || currentLang == com.gpdb.android.util.AppLanguage.ZH_TW

                val pbcTags = remember(pbc?.tags) {
                    if (!pbc?.tags.isNullOrBlank()) {
                        pbc.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else emptyList()
                }

                val externalLinks = remember(pbc?.socialLinks, pbc?.externalIds) {
                    val links = mutableListOf<Pair<String, String>>()
                    if (!pbc?.socialLinks.isNullOrBlank()) {
                        try {
                            val json = org.json.JSONObject(pbc.socialLinks)
                            val keys = json.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val u = json.optString(key)
                                if (u.isNotBlank()) links.add(key.uppercase() to u)
                            }
                        } catch (_: Exception) {}
                    }
                    if (!pbc?.externalIds.isNullOrBlank()) {
                        try {
                            val json = org.json.JSONObject(pbc.externalIds)
                            val iafd = json.optString("iafd_id")
                            if (iafd.isNotBlank()) {
                                links.add("IAFD" to "https://www.iafd.com/person.rme/perfid=$iafd/gender=m")
                            }
                            val imdb = json.optString("imdb_id")
                            if (imdb.isNotBlank()) {
                                links.add("IMDb" to "https://www.imdb.com/name/nm$imdb")
                            }
                        } catch (_: Exception) {}
                    }
                    links
                }

                fun formatAstro(astro: String?): String? {
                    if (astro.isNullOrBlank()) return null
                    return when (astro.lowercase()) {
                        "aries" -> "白羊座 ♈"
                        "taurus" -> "金牛座 ♉"
                        "gemini" -> "双子座 ♊"
                        "cancer" -> "巨蟹座 ♋"
                        "leo" -> "狮子座 ♌"
                        "virgo" -> "处女座 ♍"
                        "libra" -> "天秤座 ♎"
                        "scorpio" -> "天蝎座 ♏"
                        "sagittarius" -> "射手座 ♐"
                        "capricorn" -> "摩羯座 ♑"
                        "aquarius" -> "水瓶座 ♒"
                        "pisces" -> "双鱼座 ♓"
                        else -> astro
                    }
                }

                fun formatBirth(birth: String?): String? {
                    if (birth.isNullOrBlank()) return null
                    val match = Regex("""^(\d{4})""").find(birth)
                    val ageSuffix = match?.value?.toIntOrNull()?.let { birthYear ->
                        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        val age = currentYear - birthYear
                        if (age in 18..99) " (${age}岁)" else ""
                    } ?: ""
                    return "$birth$ageSuffix"
                }

                val advancedDetails = remember(performer, pbc, isChinese) {
                    listOfNotNull(
                        formatBirth(pbc?.birthDate)?.let { "生日" to it },
                        formatAstro(pbc?.astrology)?.let { "星座" to it },
                        pbc?.birthPlace?.takeIf { it.isNotBlank() }?.let { "籍贯" to it },
                        pbc?.ethnicity?.takeIf { it.isNotBlank() }?.let { "族裔" to it },
                        performer.hair?.let { com.gpdb.android.util.GlossaryHelper.getCleanLabel("hair", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese) },
                        performer.eyes?.let { com.gpdb.android.util.GlossaryHelper.getCleanLabel("eyes", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese) },
                        performer.facialHair?.let { com.gpdb.android.util.GlossaryHelper.getCleanLabel("facialHair", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese) },
                        performer.bodyHair?.let { com.gpdb.android.util.GlossaryHelper.getCleanLabel("bodyHair", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese) },
                        performer.skin?.let { com.gpdb.android.util.GlossaryHelper.getCleanLabel("skin", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese) },
                        (performer.dickSize?.takeIf { it.isNotBlank() && it != "none available" }
                            ?: pbc?.dickSize?.takeIf { it.isNotBlank() })?.let {
                            com.gpdb.android.util.GlossaryHelper.getCleanLabel("dickSize", isChinese) to com.gpdb.android.util.GlossaryHelper.trMeasure(it, isChinese)
                        },
                        (performer.foreskin?.takeIf { it.isNotBlank() && it != "none available" }
                            ?: pbc?.foreskin?.takeIf { it.isNotBlank() })?.let {
                            com.gpdb.android.util.GlossaryHelper.getCleanLabel("foreskin", isChinese) to com.gpdb.android.util.GlossaryHelper.translate(it, isChinese)
                        },
                        (performer.tattoos?.takeIf { it.isNotBlank() && it != "none available" }
                            ?: pbc?.tattoos?.takeIf { it.isNotBlank() })?.let {
                            com.gpdb.android.util.GlossaryHelper.getCleanLabel("tattoos", isChinese) to com.gpdb.android.util.GlossaryHelper.trTattoo(it, isChinese)
                        }
                    ).filter { it.second.isNotBlank() && it.second != "none available" }
                }

                var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
                val tabs = remember(detail.movies.size, uiState.episodes.size) {
                    listOf("出演影片 (${detail.movies.size})", "出演分集 (${uiState.episodes.size})")
                }

                val gridState = rememberLazyGridState()
                val listState = rememberLazyListState()

                if (selectedTabIndex == 0) {
                    // 出演作品网格
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PerformerProfileHeader(
                                performer = performer,
                                pbc = pbc,
                                imageData = imageData,
                                advancedDetails = advancedDetails,
                                pbcTags = pbcTags,
                                externalLinks = externalLinks,
                                allAliases = uiState.allAliases,
                                sjUrl = uiState.sjUrl,
                                isChinese = isChinese,
                                onImageClick = { showFullImage = true },
                                uriHandler = uriHandler
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PerformerTabRow(
                                tabs = tabs,
                                selectedTabIndex = selectedTabIndex,
                                onTabSelected = { selectedTabIndex = it }
                            )
                        }

                        if (detail.movies.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无出演影片记录",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(
                                items = detail.movies,
                                key = { it.id ?: 0L },
                                contentType = { "movie" }
                            ) { movie ->
                                MovieGridItem(
                                    movie = movie,
                                    physicalRootPath = physicalRootPath,
                                    onClick = { movie.id?.let { onMovieClick(it) } }
                                )
                            }
                        }
                    }
                } else {
                    // 出演分集列表
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PerformerProfileHeader(
                                performer = performer,
                                pbc = pbc,
                                imageData = imageData,
                                advancedDetails = advancedDetails,
                                pbcTags = pbcTags,
                                externalLinks = externalLinks,
                                allAliases = uiState.allAliases,
                                sjUrl = uiState.sjUrl,
                                isChinese = isChinese,
                                onImageClick = { showFullImage = true },
                                uriHandler = uriHandler
                            )
                        }

                        item {
                            PerformerTabRow(
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
                                        text = "暂无出演分集记录",
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

@Composable
private fun PerformerTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformerProfileHeader(
    performer: com.gpdb.android.data.db.entities.PerformerEntity,
    pbc: com.gpdb.android.data.db.entities.PerformerPbcProfile?,
    imageData: GpdbImageData,
    advancedDetails: List<Pair<String, String>>,
    pbcTags: List<String>,
    externalLinks: List<Pair<String, String>>,
    allAliases: List<String>,
    sjUrl: String?,
    isChinese: Boolean,
    onImageClick: () -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // 头像与基本名号
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(true)
                    .build(),
                contentDescription = performer.name,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .privacyBlurImage()
                    .clickable { onImageClick() }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = performer.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!pbc?.careerStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val isActive = pbc?.careerStatus?.equals("active", ignoreCase = true) == true
                        Surface(
                            shape = CircleShape,
                            color = if (isActive) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = if (isActive) "活跃" else "退役",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID: #${performer.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!pbc?.birthName.isNullOrBlank() && pbc.birthName != performer.name) {
                        Text(
                            text = "本名: ${pbc.birthName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pbc?.careerStart != null) {
                        Text(
                            text = "出道: ${pbc.careerStart}年",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val basicDetails = listOfNotNull(
                    (performer.height?.takeIf { it.isNotBlank() && it != "none available" }
                        ?: pbc?.height?.takeIf { it.isNotBlank() })?.let {
                        "${com.gpdb.android.util.GlossaryHelper.getCleanLabel("height", isChinese)}: ${com.gpdb.android.util.GlossaryHelper.trMeasure(it, isChinese)}"
                    },
                    (performer.weight?.takeIf { it.isNotBlank() && it != "none available" }
                        ?: pbc?.weight?.takeIf { it.isNotBlank() })?.let {
                        "${com.gpdb.android.util.GlossaryHelper.getCleanLabel("weight", isChinese)}: ${com.gpdb.android.util.GlossaryHelper.trMeasure(it, isChinese)}"
                    },
                    performer.build?.takeIf { it.isNotBlank() && it != "none available" }?.let {
                        "${com.gpdb.android.util.GlossaryHelper.getCleanLabel("build", isChinese)}: ${com.gpdb.android.util.GlossaryHelper.translate(it, isChinese)}"
                    }
                )

                if (basicDetails.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        basicDetails.forEach { detailText ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = detailText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 详细生理与背景特征 (生日、星座、族裔、尺寸等)
        if (advancedDetails.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                advancedDetails.forEach { (label, value) ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = "$label: $value",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 表演标签
        if (pbcTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "表演标签:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                pbcTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // PBC 维基人物小传
        if (!pbc?.bio.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PBC 维基人物小传",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA78BFA)
                        )
                        val pbcLink = performer.pbcUrl ?: pbc.pbcUrl
                        if (!pbcLink.isNullOrBlank()) {
                            Text(
                                text = "完整词条 ↗",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA78BFA),
                                modifier = Modifier.clickable { uriHandler.openUri(pbcLink) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pbc.bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 互联档案外链 (IAFD, IMDb, X 等)
        if (externalLinks.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "互联档案:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                externalLinks.forEach { (name, linkUrl) ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { uriHandler.openUri(linkUrl) }
                    ) {
                        Text(
                            text = "$name ↗",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 快捷资源检索与外链按钮: BT4G 搜索, BoyfriendTV, PBC 百科, SmutJunkies
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BT4G 搜索",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (!performer.bftvUrl.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(performer.bftvUrl)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "BoyfriendTV",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            val pbcDirectUrl = performer.pbcUrl ?: pbc?.pbcUrl
            if (!pbcDirectUrl.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        uriHandler.openUri(pbcDirectUrl)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "PBC 百科 ↗",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC4B5FD),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!sjUrl.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEC4899).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        uriHandler.openUri(sjUrl)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFF472B6)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SmutJunkies ↗",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF472B6),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 曾用艺名 / 别名 (AKA)
        if (allAliases.isNotEmpty()) {
            var isAliasesExpanded by rememberSaveable { mutableStateOf(false) }
            val collapseThreshold = 8
            val visibleAliases = if (isAliasesExpanded || allAliases.size <= collapseThreshold) {
                allAliases
            } else {
                allAliases.take(collapseThreshold)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "曾用艺名 / 别名 (AKA)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "共 ${allAliases.size} 个",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (allAliases.size > collapseThreshold) {
                            Text(
                                text = if (isAliasesExpanded) "收起" else "展开全部",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { isAliasesExpanded = !isAliasesExpanded }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        visibleAliases.forEach { alias ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = alias,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (!isAliasesExpanded && allAliases.size > collapseThreshold) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.clickable { isAliasesExpanded = true }
                            ) {
                                Text(
                                    text = "+${allAliases.size - collapseThreshold} 更多...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
