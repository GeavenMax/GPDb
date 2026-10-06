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

    val currentAppLang = com.gpdb.android.util.LocalAppLanguage.current
    val formattedFocusTime = remember(data.totalFocusTimeSeconds, currentAppLang) {
        val totalSec = data.totalFocusTimeSeconds
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val secs = totalSec % 60
        if (hours > 0) {
            com.gpdb.android.util.I18n.t("analytics.timeHoursMins", currentAppLang, mapOf("hours" to hours, "mins" to mins), "${hours}h ${mins}m")
        } else if (mins > 0) {
            com.gpdb.android.util.I18n.t("analytics.timeMinsSecs", currentAppLang, mapOf("mins" to mins, "secs" to secs), "${mins}m ${secs}s")
        } else {
            com.gpdb.android.util.I18n.t("analytics.timeSecs", currentAppLang, mapOf("secs" to secs), "${secs}s")
        }
    }

    val firstLaunchDateStr = remember(data.firstLaunchTime, currentAppLang) {
        try {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(data.firstLaunchTime))
        } catch (_: Exception) {
            com.gpdb.android.util.I18n.t("common.today", currentAppLang, defaultVal = "Today")
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
                            text = I18n.string("analytics.title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = I18n.string("analytics.journeySince", mapOf("date" to firstLaunchDateStr)),
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
                    subtitle = I18n.string("analytics.appActiveDwell"),
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                val moviesUnit = "${data.uniqueMoviesCount} ${I18n.string("analytics.unitMovies")}"
                StatCard(
                    title = I18n.string("analytics.exploreFeatureMovies"),
                    value = moviesUnit,
                    subtitle = I18n.string("analytics.movieViewsStat", mapOf("count" to data.movieViewsCount.toString())),
                    icon = Icons.Default.Movie,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val episodesUnit = "${data.episodeViewsCount} ${I18n.string("analytics.unitScenes")}"
                StatCard(
                    title = I18n.string("analytics.episodesViewed"),
                    value = episodesUnit,
                    subtitle = I18n.string("analytics.episodeScenesStat"),
                    icon = Icons.Default.Layers,
                    modifier = Modifier.weight(1f)
                )
                val performersUnit = "${data.uniquePerformersCount} ${I18n.string("analytics.unitPersons")}"
                StatCard(
                    title = I18n.string("analytics.performersViewed"),
                    value = performersUnit,
                    subtitle = I18n.string("analytics.performerSummary", mapOf("performers" to data.uniquePerformersCount.toString(), "count" to data.performerViewsCount.toString())),
                    icon = Icons.Default.People,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val searchesUnit = "${data.searchesCount} ${I18n.string("analytics.unitTimes")}"
                StatCard(
                    title = I18n.string("search.history"),
                    value = searchesUnit,
                    subtitle = I18n.string("analytics.searchesExecuted"),
                    icon = Icons.Default.Search,
                    modifier = Modifier.weight(1f)
                )
                val daysUnit = "${data.activeDaysCount} ${I18n.string("analytics.unitDays")}"
                StatCard(
                    title = I18n.string("analytics.activeDaysTitle"),
                    value = daysUnit,
                    subtitle = I18n.string("analytics.activeDaysStat"),
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val nightOwlUnit = "${data.nightOwlViewsCount} ${I18n.string("analytics.unitTimes")}"
                StatCard(
                    title = I18n.string("analytics.midnightExplorer"),
                    value = nightOwlUnit,
                    subtitle = I18n.string("analytics.midnightDesc"),
                    icon = Icons.Default.Bedtime,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.weight(1f)
                )
                val transUnit = "${data.translationsCount} ${I18n.string("analytics.unitItems")}"
                StatCard(
                    title = I18n.string("analytics.aiTranslationsInvoked"),
                    value = transUnit,
                    subtitle = I18n.string("analytics.aiTranslationsDesc"),
                    icon = Icons.Default.Translate,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val favRatingsUnit = com.gpdb.android.util.I18n.string(
                    "analytics.favsAndRatings",
                    mapOf("favs" to data.favoritesCount.toString(), "ratings" to data.ratingsCount.toString()),
                    defaultVal = "${data.favoritesCount} favs / ${data.ratingsCount} rated"
                )
                StatCard(
                    title = I18n.string("analytics.interactionsTitle"),
                    value = favRatingsUnit,
                    subtitle = I18n.string("analytics.interactionsSubtitle"),
                    icon = Icons.Default.Star,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                val dirStudioUnit = com.gpdb.android.util.I18n.string(
                    "analytics.dirsAndStudios",
                    mapOf("dirs" to data.directorViewsCount.toString(), "studios" to data.studioViewsCount.toString()),
                    defaultVal = "${data.directorViewsCount} dir / ${data.studioViewsCount} stu"
                )
                StatCard(
                    title = I18n.string("analytics.directorsStudiosTitle"),
                    value = dirStudioUnit,
                    subtitle = I18n.string("analytics.directorsStudiosSubtitle"),
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
                            text = I18n.string("analytics.privacyGuaranteeTitle"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = I18n.string("analytics.privacyGuaranteeText"),
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
