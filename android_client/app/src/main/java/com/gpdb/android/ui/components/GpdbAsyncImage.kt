package com.gpdb.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData

/**
 * 统一的 GPDb 离线图片渲染组件
 *
 * 自动处理：
 * 1. 相对路径与 ZIP 内部文件路径映射 (toImageCachePath)
 * 2. 缺失时的 ID 回退规则 (如 image_cache/Covers/{id}.jpg)
 * 3. GpdbImageData 封装与 Crossfade 动效
 */
@Composable
fun GpdbAsyncImage(
    url: String?,
    physicalRootPath: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    fallbackEntityId: Long? = null,
    defaultFolder: String = "Covers", // "Covers" | "Performers" | "Episodes"
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val relPath = remember(url, fallbackEntityId, defaultFolder) {
        url.toImageCachePath() ?: fallbackEntityId?.let { "image_cache/$defaultFolder/$it.jpg" }
    }

    val imageData = remember(relPath, physicalRootPath, url) {
        relPath?.let { GpdbImageData(relativePath = it, physicalRoot = physicalRootPath, fallbackUrl = url) }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(true)
            .apply {
                if (onError != null) {
                    listener(onError = { _, _ -> onError() })
                }
            }
            .build(),
        contentDescription = contentDescription,
        alignment = alignment,
        contentScale = contentScale,
        modifier = modifier.privacyBlurImage()
    )
}
