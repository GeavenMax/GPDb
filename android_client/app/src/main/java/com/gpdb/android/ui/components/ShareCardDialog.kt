package com.gpdb.android.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class ShareCardData(
    val title: String,
    val titleZh: String? = null,
    val posterUrl: String? = null,
    val fallbackEntityId: Long? = null,
    val defaultFolder: String = "Covers",
    val releaseYear: Int? = null,
    val studio: String? = null,
    val director: String? = null,
    val durationMins: Int? = null,
    val rating: String? = null,
    val category: String? = null,
    val performers: List<String> = emptyList(),
    val description: String? = null,
    val isEpisode: Boolean = false,
    val episodeDate: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareCardDialog(
    cardData: ShareCardData,
    physicalRootPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    var blurPoster by remember { mutableStateOf(false) }
    var blurText by remember { mutableStateOf(false) }
    var posterBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dominantColor by remember { mutableStateOf(Color(0xFF1E222B)) }
    var isCapturing by remember { mutableStateOf(false) }

    // 异步加载海报 Bitmap 并提取主色调
    LaunchedEffect(cardData.posterUrl, cardData.fallbackEntityId) {
        withContext(Dispatchers.IO) {
            try {
                val relPath = cardData.posterUrl.toImageCachePath()
                    ?: cardData.fallbackEntityId?.let { "image_cache/${cardData.defaultFolder}/$it.jpg" }
                val imageData = relPath?.let { GpdbImageData(it, physicalRootPath, cardData.posterUrl) }
                if (imageData != null) {
                    val request = ImageRequest.Builder(context)
                        .data(imageData)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bmp = drawable.bitmap
                        posterBitmap = bmp
                        dominantColor = ShareCardHelper.extractDominantColor(bmp)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 顶部标题栏与关闭按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (cardData.isEpisode) "生成分集卡片" else "生成电影卡片",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.8f))
                    }
                }

                // 核心卡片容器 (使用 graphicsLayer 进行位图离屏绘制与捕获)
                Box(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171E)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // 1. 底层高斯模糊海报氛围层
                            if (posterBitmap != null) {
                                Image(
                                    bitmap = posterBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .blur(48.dp)
                                        .alpha(0.55f)
                                )
                            }

                            // 2. 取色自海报的渐变映射遮罩
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                dominantColor.copy(alpha = 0.65f),
                                                Color(0xFF0F1116).copy(alpha = 0.88f),
                                                Color(0xFF07080A).copy(alpha = 0.98f)
                                            )
                                        )
                                    )
                            )

                            // 3. 卡片前景排版内容
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 头部品牌条
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.18f),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.MovieFilter,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (cardData.isEpisode) "GPDb · 场景分集档案" else "GPDb · 影视档案",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.9f),
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "COLLECTION",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 美观立体的核心海报框
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .aspectRatio(0.70f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (posterBitmap != null) {
                                        Image(
                                            bitmap = posterBitmap!!.asImageBitmap(),
                                            contentDescription = cardData.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(if (blurPoster) Modifier.blur(32.dp).clipToBounds() else Modifier)
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }

                                    // 海报打码状态标识
                                    if (blurPoster) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "海报已安全打码",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // 中英双语大标题
                                val primaryTitle = cardData.titleZh ?: cardData.title
                                val secondaryTitle = if (cardData.titleZh != null && cardData.title != cardData.titleZh) cardData.title else null

                                Text(
                                    text = primaryTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (secondaryTitle != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = secondaryTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // 详细属性胶囊条 (年份、片商、导演、时长、评分)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    cardData.releaseYear?.let { year ->
                                        ShareMetaChip(text = year.toString())
                                    }
                                    cardData.studio?.let { studio ->
                                        ShareMetaChip(text = studio)
                                    }
                                    cardData.director?.let { dir ->
                                        ShareMetaChip(text = "导: $dir")
                                    }
                                    cardData.durationMins?.let { mins ->
                                        ShareMetaChip(text = "${mins}分")
                                    }
                                    cardData.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { rating ->
                                        ShareMetaChip(text = "★ $rating")
                                    }
                                    cardData.episodeDate?.let { date ->
                                        ShareMetaChip(text = date)
                                    }
                                }

                                // 出演演员列表
                                if (cardData.performers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "主演: " + cardData.performers.take(5).joinToString("  ") + (if (cardData.performers.size > 5) " 等" else ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // 剧情简介卡片
                                if (!cardData.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = cardData.description.take(130) + (if (cardData.description.length > 130) "..." else ""),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.75f),
                                                lineHeight = 18.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.then(
                                                    if (blurText) Modifier.blur(14.dp).clipToBounds() else Modifier
                                                )
                                            )
                                            if (blurText) {
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color.Black.copy(alpha = 0.72f),
                                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = "剧情简介已安全打码",
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // 水印与生成日期底栏
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "GPDb · 个人离线数字影库",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 安全分享开关选项
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = blurPoster,
                        onClick = { blurPoster = !blurPoster },
                        label = { Text("模糊海报") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (blurPoster) Icons.Default.Check else Icons.Default.BlurOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    FilterChip(
                        selected = blurText,
                        onClick = { blurText = !blurText },
                        label = { Text("模糊文字") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (blurText) Icons.Default.Check else Icons.Default.TextFields,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isCapturing = true
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    ShareCardHelper.saveBitmapToGallery(context, bitmap, cardData.title)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isCapturing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isCapturing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存相册")
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCapturing = true
                                try {
                                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                    ShareCardHelper.shareBitmap(context, bitmap, cardData.title)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isCapturing = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isCapturing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一键分享")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareMetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
