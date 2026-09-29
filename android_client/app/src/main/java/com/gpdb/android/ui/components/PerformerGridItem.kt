package com.gpdb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpdb.android.data.db.entities.PerformerEntity

/**
 * 统一的演员网格卡片组件
 *
 * 用于：
 * - PerformerListScreen (演员库)
 * - SearchScreen (演员搜索结果)
 * - LibraryScreen (收藏演员)
 */
@Composable
fun PerformerGridItem(
    performer: PerformerEntity,
    physicalRootPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = performer.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
