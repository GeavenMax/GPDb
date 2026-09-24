package com.gpdb.android.ui.browse
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeriesGridItem(
    series: com.gpdb.android.data.db.entities.SeriesCollectionEntity,
    physicalRootPath: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Default to a fallback if null
    val relativePath = series.coverUrl?.toImageCachePath()

    val imageData = remember(series.id, physicalRootPath) {
        GpdbImageData(
            relativePath = relativePath ?: "",
            physicalRoot = physicalRootPath,
            fallbackUrl = series.coverUrl
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageData)
                        .build(),
                    contentDescription = series.rootTitle,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${series.movieCount} 部",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(8.dp)
            ) {
                Text(
                    text = series.rootTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )

                series.studioName?.let { studio ->
                    Text(
                        text = studio,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp).basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesListContent(
    viewModel: SeriesListViewModel,
    onSeriesClick: (String) -> Unit,
    physicalRootPath: String = "",
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSeries()
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.error != null) {
            Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = uiState.series,
                    key = { it.id ?: it.hashCode() },
                    contentType = { "series" }
                ) { series ->
                    SeriesGridItem(
                        series = series,
                        physicalRootPath = physicalRootPath, // Needs to be passed
                        onClick = { onSeriesClick(series.rootTitle) }
                    )
                }
            }
        }
    }
}
