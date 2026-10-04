package com.gpdb.android.ui.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpdb.android.ui.components.AdaptiveLogoContainer
import com.gpdb.android.ui.components.GpdbAsyncImage
import com.gpdb.android.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioListScreen(
    physicalRootPath: String,
    viewModel: StudioListViewModel,
    onStudioClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStudios()
    }

    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    Scaffold(
        topBar = {
            var showSortMenu by remember { mutableStateOf(false) }

            SearchTopAppBar(
                title = com.gpdb.android.util.I18n.string("nav.studios"),
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = { viewModel.loadStudios(query = it) },
                placeholder = "搜索片商 (支持中英文)...",
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("按作品数排序") },
                            onClick = {
                                showSortMenu = false
                                viewModel.loadStudios(sortBy = "works")
                            },
                            trailingIcon = { if (uiState.sortBy == "works") Icon(Icons.Default.Check, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("按拼音/字母排序") },
                            onClick = {
                                showSortMenu = false
                                viewModel.loadStudios(sortBy = "name")
                            },
                            trailingIcon = { if (uiState.sortBy == "name") Icon(Icons.Default.Check, null) }
                        )
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
            } else if (uiState.studios.isEmpty()) {
                Text(
                    text = "未找到匹配的片商",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.studios, key = { it.name }) { studio ->
                        StudioShelfCard(
                            studio = studio,
                            physicalRootPath = physicalRootPath,
                            onClick = { onStudioClick(studio.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioShelfCard(
    studio: StudioItem,
    physicalRootPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryTitle = studio.nameZh?.takeIf { it.isNotBlank() } ?: studio.name
    val secondaryTitle = if (studio.nameZh != null && studio.nameZh != studio.name) studio.name else null
    val monogram = primaryTitle.firstOrNull()?.uppercase() ?: studio.name.firstOrNull()?.uppercase() ?: "S"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Adaptive Logo Stand (Shelf)
            AdaptiveLogoContainer(
                logoUrl = studio.logoUrl,
                monogram = monogram,
                physicalRootPath = physicalRootPath,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Studio Name
            Text(
                text = primaryTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            if (secondaryTitle != null) {
                Text(
                    text = secondaryTitle,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
