package com.gpdb.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.image.GpdbImageData

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableImageDialog(
    images: List<GpdbImageData>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return

    // Limit initialIndex bounds safely
    val safeInitialIndex = initialIndex.coerceIn(0, images.size - 1)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex, pageCount = { images.size })
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // 允许全屏
        )
    ) {
        val alpha = (1f - (Math.abs(verticalDragOffset) / 1000f)).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val imageData = images[page]

                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                // 当切页时恢复缩放状态
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != page) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        verticalDragOffset = 0f
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop
                                var pan = Offset.Zero
                                var zoom = 1f

                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            zoom *= zoomChange
                                            pan += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = Math.abs(1 - zoom) * centroidSize
                                            val panMotion = pan.getDistance()

                                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            // 逻辑：
                                            // 1. 如果当前比例为1（未放大），且为单指操作，且水平移动大于垂直移动，
                                            //    说明用户在尝试左右滑页，此时【不消耗事件】，让外层的 HorizontalPager 捕获！
                                            val isHorizontalSwipe = scale == 1f && event.changes.size == 1 && Math.abs(panChange.x) > Math.abs(panChange.y)
                                            
                                            if (!isHorizontalSwipe) {
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                                
                                                if (scale > 1f) {
                                                    offsetX += panChange.x
                                                    offsetY += panChange.y
                                                } else {
                                                    // 未放大时，非水平滑动的拖拽（即上下滑动），用于触发退出动画
                                                    verticalDragOffset += panChange.y
                                                    offsetY = verticalDragOffset
                                                }
                                            }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                // 手指抬起时的判定
                                if (scale == 1f) {
                                    if (Math.abs(verticalDragOffset) > 250f) {
                                        onDismiss() // 滑动距离足够，退出查看
                                    } else {
                                        // 距离不够，弹回原位
                                        verticalDragOffset = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageData)
                            .crossfade(true)
                            .build(),
                        contentDescription = "全屏查看",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
        }
    }
}
