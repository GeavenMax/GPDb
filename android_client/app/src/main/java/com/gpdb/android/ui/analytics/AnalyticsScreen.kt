package com.gpdb.android.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpdb.android.data.analytics.AnalyticsData
import com.gpdb.android.data.analytics.UserAnalyticsRepository
import com.gpdb.android.util.I18n
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserAnalyticsRepository.getInstance(context) }
    val data by repository.analyticsFlow.collectAsState(initial = AnalyticsData())
    var showClearDialog by remember { mutableStateOf(false) }

    val formattedFocusTime = remember(data.totalFocusTimeSeconds) {
        val totalSec = data.totalFocusTimeSeconds
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        if (hours > 0) {
            "${hours}小时 ${mins}分"
        } else if (mins > 0) {
            "${mins}分 ${secs}秒"
        } else {
            "${secs}秒"
        }
    }

    val firstLaunchDateStr = remember(data.firstLaunchTime) {
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(data.firstLaunchTime))
        } catch (_: Exception) {
            "今日"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = I18n.string("analytics.title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = I18n.string("common.back")
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = I18n.string("analytics.clear"),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header summary banner
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本地光影漫游纪实",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "自 $firstLaunchDateStr 启程 · 忠实记录每一次影视交互",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Stat Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = I18n.string("analytics.totalTime"),
                    value = formattedFocusTime,
                    subtitle = "应用交互活跃时长",
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = I18n.string("analytics.moviesExplored"),
                    value = "${data.uniqueMoviesCount} 部",
                    subtitle = "累计点击 ${data.movieViewsCount} 次",
                    icon = Icons.Default.Movie,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = I18n.string("analytics.episodesViewed"),
                    value = "${data.episodeViewsCount} 段",
                    subtitle = "独立分集与片段场景",
                    icon = Icons.Default.Layers,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = I18n.string("analytics.performersKnown"),
                    value = "${data.uniquePerformersCount} 位",
                    subtitle = "累计查阅 ${data.performerViewsCount} 次",
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = I18n.string("analytics.searchesCount"),
                    value = "${data.searchesCount} 次",
                    subtitle = "全库搜索探索频次",
                    icon = Icons.Default.Search,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = I18n.string("analytics.activeDays"),
                    value = "${data.activeDaysCount} 天",
                    subtitle = "累计活跃打卡天数",
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = I18n.string("analytics.nightOwl"),
                    value = "${data.nightOwlViewsCount} 次",
                    subtitle = "午夜 23:00~05:00 沉浸探索",
                    icon = Icons.Default.Bedtime,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = I18n.string("analytics.translationsCount"),
                    value = "${data.translationsCount} 篇",
                    subtitle = "AI 智能翻译生成",
                    icon = Icons.Default.Translate,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "互动收藏与打分",
                    value = "${data.favoritesCount} 藏 / ${data.ratingsCount} 评",
                    subtitle = "个人偏好与评分标记",
                    icon = Icons.Default.Star,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "导演与片商",
                    value = "${data.directorViewsCount} 导 / ${data.studioViewsCount} 厂",
                    subtitle = "深入幕后制作脉络",
                    icon = Icons.Default.Business,
                    modifier = Modifier.weight(1f)
                )
            }

            // Privacy pledge card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "100% 本地隐私保证",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "GPDb 坚持绝对纯粹的无网络追踪设计。所有使用统计数据完全存储在手机私有沙盒内，绝无任何第三方埋点探针或远程数据分析，随时可在设置中关闭或一键清空。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(I18n.string("analytics.clear")) },
            text = { Text(I18n.string("analytics.clearConfirm")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.resetAllAnalytics()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(I18n.string("common.confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(I18n.string("common.cancel"))
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val iconTint = tint ?: MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
