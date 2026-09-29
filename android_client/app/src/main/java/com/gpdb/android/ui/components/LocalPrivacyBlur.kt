package com.gpdb.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局截屏/分享隐私打码状态
 */
data class PrivacyBlurState(
    val enabled: Boolean = false,
    val blurImages: Boolean = true,
    val blurText: Boolean = true
)

val LocalPrivacyBlur = compositionLocalOf { PrivacyBlurState() }

/**
 * 图片隐私高斯模糊修饰符
 */
@Composable
fun Modifier.privacyBlurImage(blurRadius: Dp = 24.dp): Modifier {
    val privacy = LocalPrivacyBlur.current
    return if (privacy.enabled && privacy.blurImages) {
        this.blur(blurRadius).clipToBounds()
    } else {
        this
    }
}

/**
 * 介绍与敏感文本隐私高斯模糊修饰符
 */
@Composable
fun Modifier.privacyBlurText(blurRadius: Dp = 12.dp): Modifier {
    val privacy = LocalPrivacyBlur.current
    return if (privacy.enabled && privacy.blurText) {
        this.blur(blurRadius).clipToBounds()
    } else {
        this
    }
}
