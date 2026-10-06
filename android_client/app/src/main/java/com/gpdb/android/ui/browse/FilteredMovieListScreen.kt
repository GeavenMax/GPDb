package com.gpdb.android.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gpdb.android.ui.components.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredMovieListScreen(
    filterType: String,
    filterValue: String,
    physicalRootPath: String,
    viewModel: FilteredMovieListViewModel,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(filterType, filterValue) {
        viewModel.load(filterType, filterValue)
    }

    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val displayTitle = remember(uiState.title) {
        if (uiState.title.contains("|||")) {
            val parts = uiState.title.split("|||")
            if (parts.size > 1 && parts[0].isNotBlank()) "${parts[1]} (${parts[0]})" else parts.last()
        } else {
            uiState.title
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayTitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.gpdb.android.util.I18n.string("common.back"))
                    }
                },
                actions = {
                    if (filterType == "series" || filterType == "director") {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (uiState.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (uiState.isFavorite) com.gpdb.android.util.I18n.string("common.unfavorite") else com.gpdb.android.util.I18n.string("common.favorite"),
                                tint = if (uiState.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.movies,
                        key = { it.id ?: it.hashCode() },
                        contentType = { "movie" }
                    ) { movie ->
                        MovieGridItem(
                            movie = movie,
                            physicalRootPath = physicalRootPath,
                            onClick = { onMovieClick(movie.id ?: return@MovieGridItem) },
                            onStudioClick = { studio -> onStudioClick(studio) }
                        )
                    }
                }
            }
        }
    }
}
