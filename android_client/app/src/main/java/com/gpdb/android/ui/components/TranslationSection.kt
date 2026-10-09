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
    title: String = com.gpdb.android.util.I18n.string("synopsis.title", defaultVal = "剧情简介"),
    onTranslateClick: (String) -> Unit
) {
    val summary = originalSummary?.takeIf { it.isNotBlank() } ?: return
    var showOriginal by rememberSaveable { mutableStateOf(false) }

    val currentLang = com.gpdb.android.util.LocalAppLanguage.current
    val effectiveLang = com.gpdb.android.util.AppLanguage.resolveEffective(currentLang)
    val isEnglish = effectiveLang == com.gpdb.android.util.AppLanguage.EN

    val displayText = if (!isEnglish && translatedSummary != null && !showOriginal) {
        translatedSummary
    } else {
        summary
    }

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

            if (!isEnglish && !isLoading) {
                if (translatedSummary != null) {
                    TextButton(onClick = { showOriginal = !showOriginal }) {
                        val label = if (showOriginal) {
                            com.gpdb.android.util.I18n.t("synopsis.showTranslation", effectiveLang, defaultVal = "显示译文")
                        } else {
                            com.gpdb.android.util.I18n.t("synopsis.showOriginal", effectiveLang, defaultVal = "显示原文")
                        }
                        Text(label)
                    }
                } else {
                    TextButton(onClick = { onTranslateClick(summary) }) {
                        Text(com.gpdb.android.util.I18n.t("synopsis.aiTranslate", effectiveLang, defaultVal = "🪄 AI 翻译"))
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
