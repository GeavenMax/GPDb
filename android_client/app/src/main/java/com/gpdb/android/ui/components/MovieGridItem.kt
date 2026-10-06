package com.gpdb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpdb.android.data.db.entities.MovieEntity

import androidx.compose.material3.Surface

/**
 * 统一的影片网格卡片组件
 *
 * 用于：
 * - HomeScreen / MovieBrowseScreen (长片)
 * - LibraryScreen (收藏/想看/已看)
 * - FilteredMovieListScreen (分类/厂牌/系列影片列表)
 * - SearchScreen (影片搜索结果)
 * - PerformerDetailScreen (演员参演作品)
 */
@Composable
fun MovieGridItem(
    movie: MovieEntity,
    physicalRootPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    badgeText: String? = null,
    onStudioClick: ((String) -> Unit)? = null
) {
    val displayBadge = badgeText ?: if (isNew || (movie.releaseYear != null && movie.releaseYear >= 2026)) "NEW" else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
            ) {
                GpdbAsyncImage(
                    url = movie.coverFull ?: movie.coverIcon,
                    physicalRootPath = physicalRootPath,
                    contentDescription = movie.title,
                    fallbackEntityId = movie.id,
                    defaultFolder = "Covers",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                if (displayBadge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = displayBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // 底部渐变半透明信息栏 (年份 + 时长)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        movie.releaseYear?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        movie.durationMins?.let { mins ->
                            val unit = com.gpdb.android.util.I18n.string("common.minutes")
                            Text(
                                text = "$mins $unit",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // 标题与副标题信息区 (统一固定高度，跑马灯滚动)
            val isZh = com.gpdb.android.util.LocalAppLanguage.current.isChinese
            val primaryTitle = if (isZh) (movie.titleZh?.takeIf { it.isNotBlank() } ?: movie.title) else movie.title
            val secondaryTitle = if (isZh && !movie.titleZh.isNullOrBlank() && movie.title != movie.titleZh) movie.title else null

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                if (secondaryTitle != null) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                movie.studioName?.let { studio ->
                    Text(
                        text = studio,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarquee()
                            .then(
                                if (onStudioClick != null) Modifier.clickable { onStudioClick(studio) } else Modifier
                            )
                    )
                }
            }
        }
    }
}
