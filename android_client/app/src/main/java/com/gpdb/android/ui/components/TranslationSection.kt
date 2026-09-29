package com.gpdb.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的 AI 翻译与简介展示组件
 *
 * 封装：
 * 1. 简介标题与“🪄 AI 翻译”操作入口
 * 2. 翻译中状态（CircularProgressIndicator）与错误提示
 * 3. 翻译完成后的“显示原文 / 显示译文”双向切换
 */
@Composable
fun TranslationSection(
    originalSummary: String?,
    translatedSummary: String?,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    title: String = "剧情简介",
    onTranslateClick: (String) -> Unit
) {
    val summary = originalSummary?.takeIf { it.isNotBlank() } ?: return
    var showOriginal by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!isLoading) {
                if (translatedSummary != null) {
                    TextButton(onClick = { showOriginal = !showOriginal }) {
                        Text(if (showOriginal) "显示译文" else "显示原文")
                    }
                } else {
                    TextButton(onClick = { onTranslateClick(summary) }) {
                        Text("🪄 AI 翻译")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else {
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            val displayText = if (translatedSummary != null && !showOriginal) {
                translatedSummary
            } else {
                summary
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.privacyBlurText()
            )
        }
    }
}
