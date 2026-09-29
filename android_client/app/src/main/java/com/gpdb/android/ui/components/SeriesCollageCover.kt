package com.gpdb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun SeriesCollageCover(
    sampleCoversRaw: String?,
    singleFallbackUrl: String? = null,
    physicalRootPath: String = "",
    title: String = "",
    modifier: Modifier = Modifier
) {
    val covers = remember(sampleCoversRaw, singleFallbackUrl) {
        parseCovers(sampleCoversRaw, singleFallbackUrl)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        when {
            covers.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            covers.size == 1 -> {
                GpdbAsyncImage(
                    url = covers[0],
                    physicalRootPath = physicalRootPath,
                    contentDescription = title,
                    defaultFolder = "Covers",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            covers.size == 2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    covers.take(2).forEach { cov ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            GpdbAsyncImage(
                                url = cov,
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            covers.size == 3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    // Left 50%
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        GpdbAsyncImage(
                            url = covers[0],
                            physicalRootPath = physicalRootPath,
                            contentDescription = title,
                            defaultFolder = "Covers",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Right 50% (2 vertically stacked)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            GpdbAsyncImage(
                                url = covers[1],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            GpdbAsyncImage(
                                url = covers[2],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            else -> {
                // 4+ covers: 2x2 grid
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            GpdbAsyncImage(
                                url = covers[0],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            GpdbAsyncImage(
                                url = covers[1],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            GpdbAsyncImage(
                                url = covers[2],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            GpdbAsyncImage(
                                url = covers[3],
                                physicalRootPath = physicalRootPath,
                                contentDescription = title,
                                defaultFolder = "Covers",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseCovers(raw: String?, singleFallback: String?): List<String> {
    val results = mutableListOf<String>()
    if (!raw.isNullOrBlank()) {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                val jsonArr = JSONArray(trimmed)
                for (i in 0 until jsonArr.length()) {
                    val s = jsonArr.optString(i)
                    if (s.isNotBlank()) {
                        results.add(s.trim())
                    }
                }
            } catch (e: Exception) {
                // Fallback regex extraction
                val pattern = Regex("https?://[^\"',\\s\\]]+")
                pattern.findAll(trimmed).forEach { match ->
                    results.add(match.value)
                }
            }
        } else {
            trimmed.split(",").forEach { s ->
                val clean = s.trim().trim('"', '\'', '[', ']', ' ')
                if (clean.isNotBlank()) results.add(clean)
            }
        }
    }

    if (results.isEmpty() && !singleFallback.isNullOrBlank()) {
        results.add(singleFallback.trim())
    }

    return results
}
