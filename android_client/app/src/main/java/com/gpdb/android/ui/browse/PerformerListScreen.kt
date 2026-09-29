package com.gpdb.android.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gpdb.android.ui.components.PerformerGridItem
import com.gpdb.android.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformerListScreen(
    physicalRootPath: String,
    viewModel: PerformerListViewModel,
    onPerformerClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPerformers()
    }

    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "演员库",
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.loadPerformers(query = it) },
                placeholder = "搜索演员..."
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
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.performers,
                        key = { it.id ?: it.hashCode() },
                        contentType = { "performer" }
                    ) { performer ->
                        PerformerGridItem(
                            performer = performer,
                            physicalRootPath = physicalRootPath,
                            onClick = { onPerformerClick(performer.id ?: 0L) }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }
        }
    }
}
