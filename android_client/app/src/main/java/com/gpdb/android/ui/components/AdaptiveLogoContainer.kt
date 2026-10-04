package com.gpdb.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 厂牌 Logo 智能取色模型
 *
 * @param backgroundColor 容器边缘背景色（用于填充图片比例差异产生的留白）
 * @param isTransparent 是否为透明背景 PNG
 * @param isLight 是否为浅色/亮色背景
 * @param accentColor Logo 主体提取出的鲜艳强调色（用于容器微光边框与氛围衬托）
 */
data class LogoPalette(
    val backgroundColor: Color,
    val isTransparent: Boolean = false,
    val isLight: Boolean = false,
    val accentColor: Color? = null
) {
    companion object {
        val Default = LogoPalette(
            backgroundColor = Color.Transparent,
            isTransparent = true,
            isLight = false,
            accentColor = null
        )
    }
}

/**
 * 全局内存 LRU 调色板缓存（按图片路径缓存，避免滑动时重复解码取色）
 */
object LogoColorCache {
    private val cache = LruCache<String, LogoPalette>(300)

    fun get(key: String): LogoPalette? = synchronized(cache) { cache.get(key) }
    fun put(key: String, palette: LogoPalette) { synchronized(cache) { cache.put(key, palette) } }
}

/**
 * 自适应取色沉浸式 Logo 容器
 *
 * 核心特性：
 * 1. 毫秒级多点采样：智能检测图片四周边缘像素（4 个拐角与 8 个边缘采样点）；
 * 2. 智能留白填充：当 Logo 为非透明纯色底（如常见的黑底、深蓝底或白底）时，容器底色自动与图片边缘底色 100% 融合，
 *    彻底消除不同长宽比在固定容器中产生的丑陋「框中框」及黑灰接缝；
 * 3. 强调色微光边框：从 Logo 主体内部智能吸取高饱和度强调色（如红色、明黄、天蓝、金黄等），赋予容器优雅的微光边框；
 * 4. 平滑过渡：色彩采用 280ms 动效插值渐变，杜绝图片加载完成时的生硬跳变；
 * 5. 优雅降级：图片缺失或加载失败时，自适应回退至首字母渐变彩色微标。
 */
@Composable
fun AdaptiveLogoContainer(
    logoUrl: String?,
    monogram: String,
    physicalRootPath: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(6.dp),
    elevation: Dp = 0.dp,
    contentScale: ContentScale = ContentScale.Fit,
    onError: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var hasError by remember(logoUrl) { mutableStateOf(false) }
    val showLogo = !logoUrl.isNullOrBlank() && !hasError

    val cacheKey = logoUrl ?: ""
    val cachedPalette = remember(cacheKey) {
        if (cacheKey.isNotBlank()) LogoColorCache.get(cacheKey) else null
    }
    var palette by remember(cacheKey) { mutableStateOf(cachedPalette) }

    // 默认未加载或透明时的底色
    val defaultSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val defaultBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    // 计算当前目标背景色与边框色
    val targetBgColor: Color = when {
        !showLogo -> Color.Transparent
        palette == null -> defaultSurface
        palette!!.isTransparent -> {
            if (palette!!.accentColor != null) {
                // 透明底且有强调色：以微量强调色为底提升通透质感
                palette!!.accentColor!!.copy(alpha = 0.12f)
            } else {
                defaultSurface
            }
        }
        else -> palette!!.backgroundColor
    }

    val targetBorderColor: Color = when {
        !showLogo -> Color.Transparent
        palette?.accentColor != null -> {
            palette!!.accentColor!!.copy(alpha = if (palette!!.isLight) 0.35f else 0.45f)
        }
        palette?.isLight == true -> Color.Black.copy(alpha = 0.12f)
        else -> defaultBorder
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 280),
        label = "logoContainerBg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 280),
        label = "logoContainerBorder"
    )

    Surface(
        shape = shape,
        color = if (showLogo) animatedBgColor else Color.Transparent,
        border = if (showLogo) BorderStroke(1.dp, animatedBorderColor) else null,
        shadowElevation = elevation,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            if (showLogo) {
                GpdbAsyncImage(
                    url = logoUrl,
                    physicalRootPath = physicalRootPath,
                    defaultFolder = "Logos",
                    contentScale = contentScale,
                    onSuccess = { successState ->
                        if (palette == null) {
                            coroutineScope.launch(Dispatchers.Default) {
                                val bmp = successState.result.drawable.toSoftwareBitmapOrNull()
                                val extracted = bmp?.let { extractLogoPalette(it) } ?: LogoPalette.Default
                                LogoColorCache.put(cacheKey, extracted)
                                withContext(Dispatchers.Main) {
                                    palette = extracted
                                }
                            }
                        }
                    },
                    onError = {
                        hasError = true
                        onError?.invoke()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 回退首字母渐变徽标
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
                                )
                            )
                        )
                ) {
                    Text(
                        text = monogram,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * 安全地将 Drawable 转换为 Software Bitmap（兼容 Android 8.0+ Hardware Bitmap）
 */
private fun Drawable.toSoftwareBitmapOrNull(): Bitmap? {
    if (this is BitmapDrawable) {
        val bmp = this.bitmap ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }
    return try {
        val w = intrinsicWidth.takeIf { it > 0 } ?: 120
        val h = intrinsicHeight.takeIf { it > 0 } ?: 60
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
        bitmap
    } catch (_: Exception) {
        null
    }
}

/**
 * 高性能智能取色算法
 */
private fun extractLogoPalette(srcBitmap: Bitmap): LogoPalette {
    // 降采样至轻量尺寸以保证极速分析 (宽不超 120)
    val targetW = 120
    val targetH = (targetW.toFloat() / srcBitmap.width * srcBitmap.height).toInt().coerceIn(20, 120)
    val bitmap = try {
        Bitmap.createScaledBitmap(srcBitmap, targetW, targetH, false)
    } catch (_: Exception) {
        srcBitmap
    }

    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return LogoPalette.Default

    // 1. 四周边缘 12 点采样检测
    val samplePoints = listOf(
        0 to 0,
        w - 1 to 0,
        0 to h - 1,
        w - 1 to h - 1,
        // 内缩 4% 避开抗锯齿滤镜瑕疵
        (w * 0.04).toInt().coerceIn(0, w - 1) to (h * 0.04).toInt().coerceIn(0, h - 1),
        (w * 0.96).toInt().coerceIn(0, w - 1) to (h * 0.04).toInt().coerceIn(0, h - 1),
        (w * 0.04).toInt().coerceIn(0, w - 1) to (h * 0.96).toInt().coerceIn(0, h - 1),
        (w * 0.96).toInt().coerceIn(0, w - 1) to (h * 0.96).toInt().coerceIn(0, h - 1),
        // 边缘中点
        w / 2 to 0,
        w / 2 to h - 1,
        0 to h / 2,
        w - 1 to h / 2
    )

    var transparentCount = 0
    var totalR = 0L
    var totalG = 0L
    var totalB = 0L
    var opaqueCount = 0

    for ((x, y) in samplePoints) {
        val pixel = bitmap.getPixel(x, y)
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 36) {
            transparentCount++
        } else {
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            totalR += r
            totalG += g
            totalB += b
            opaqueCount++
        }
    }

    // 若半数以上边缘为透明，判定为透明背景 PNG
    if (transparentCount >= 6 || opaqueCount == 0) {
        val accent = findAccentColor(bitmap, ignoreColor = null)
        return LogoPalette(
            backgroundColor = Color.Transparent,
            isTransparent = true,
            isLight = false,
            accentColor = accent
        )
    }

    // 纯色或近色边缘：计算边缘平均颜色
    val avgR = (totalR / opaqueCount).toInt().coerceIn(0, 255)
    val avgG = (totalG / opaqueCount).toInt().coerceIn(0, 255)
    val avgB = (totalB / opaqueCount).toInt().coerceIn(0, 255)

    val luminance = 0.299 * avgR + 0.587 * avgG + 0.114 * avgB
    val isLight = luminance > 180

    // 针对近纯黑和近纯白进行极值压制，确保 OLED 纯黑与纯白卡片质感
    val finalBgColor = when {
        luminance < 20 -> Color(0xFF000000)
        luminance > 240 -> Color(0xFFFFFFFF)
        else -> Color(android.graphics.Color.rgb(avgR, avgG, avgB))
    }

    val bgInt = android.graphics.Color.rgb(avgR, avgG, avgB)
    val accent = findAccentColor(bitmap, ignoreColor = bgInt)

    return LogoPalette(
        backgroundColor = finalBgColor,
        isTransparent = false,
        isLight = isLight,
        accentColor = accent
    )
}

/**
 * 从内部采样网格提取饱和度最高的品牌强调色
 */
private fun findAccentColor(bitmap: Bitmap, ignoreColor: Int?): Color? {
    val w = bitmap.width
    val h = bitmap.height

    var bestSaturation = 0.28f // 阈值：过滤灰白无彩色系
    var bestColor: Color? = null

    val stepX = (w * 0.6f / 5).toInt().coerceAtLeast(1)
    val stepY = (h * 0.6f / 5).toInt().coerceAtLeast(1)
    val startX = (w * 0.2f).toInt()
    val startY = (h * 0.2f).toInt()

    val hsv = FloatArray(3)

    for (ix in 0..4) {
        for (iy in 0..4) {
            val px = (startX + ix * stepX).coerceIn(0, w - 1)
            val py = (startY + iy * stepY).coerceIn(0, h - 1)
            val pixel = bitmap.getPixel(px, py)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 64) continue

            if (ignoreColor != null) {
                val dr = ((pixel ushr 16) and 0xFF) - ((ignoreColor ushr 16) and 0xFF)
                val dg = ((pixel ushr 8) and 0xFF) - ((ignoreColor ushr 8) and 0xFF)
                val db = (pixel and 0xFF) - (ignoreColor and 0xFF)
                val distSq = dr * dr + dg * dg + db * db
                if (distSq < 1500) continue // 距离底色过近跳过
            }

            android.graphics.Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            if (sat > bestSaturation && value in 0.25f..0.98f) {
                bestSaturation = sat
                bestColor = Color(pixel)
            }
        }
    }
    return bestColor
}
