package com.gpdb.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 统一的分集列表卡片组件
 *
 * 用于：
 * - MovieDetailScreen (相关分集)
 * - StudioDetailScreen (发行分集)
 * - LibraryScreen (收藏分集)
 */
@Composable
fun EpisodeListItem(
    episodeId: Long?,
    title: String?,
    thumbnailUrl: String?,
    physicalRootPath: String,
    modifier: Modifier = Modifier,
    releaseDate: String? = null,
    studioName: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
        ) {
            GpdbAsyncImage(
                url = thumbnailUrl,
                physicalRootPath = physicalRootPath,
                contentDescription = title,
                fallbackEntityId = episodeId,
                defaultFolder = "Episodes",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: "未知分集",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val subText = when {
                    !releaseDate.isNullOrBlank() && !studioName.isNullOrBlank() -> "$releaseDate · $studioName"
                    !releaseDate.isNullOrBlank() -> releaseDate
                    !studioName.isNullOrBlank() -> studioName
                    else -> null
                }

                if (subText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
