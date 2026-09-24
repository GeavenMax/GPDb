package com.gpdb.android.ui.search
import com.gpdb.android.ui.browse.PerformerGridItem
import androidx.compose.foundation.lazy.grid.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gpdb.android.data.db.entities.toImageCachePath
import com.gpdb.android.image.GpdbImageData
import com.gpdb.android.ui.home.MovieGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    physicalRootPath: String,
    viewModel: SearchViewModel,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {},
    onPerformerClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(physicalRootPath) {
        viewModel.initRepository()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = uiState.query,
                        onValueChange = { viewModel.updateQuery(it) },
                        placeholder = { Text("搜索影片或演员...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isSearching && uiState.query.isNotEmpty() && uiState.movies.isEmpty() && uiState.performers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.query.isEmpty()) {
                        if (searchHistory.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("搜索历史", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { 
                            viewModel.viewModelScope.launch { viewModel.appPreferences.clearSearchHistory() }
                        }) { Text("清除") }
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        searchHistory.forEach { term ->
                            SuggestionChip(
                                onClick = { viewModel.updateQuery(term) },
                                label = { Text(term) }
                            )
                        }
                    }
                }
            }

            if (uiState.categories.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                    Text("热门分类标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.categories.forEach { cat ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.updateQuery(cat.term) },
                                label = { Text(cat.zh) }
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text("输入关键字开始检索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (uiState.movies.isEmpty() && uiState.performers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("未找到相关结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.performers.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("演员", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    items(
                        items = uiState.performers,
                        key = { "p_${it.id}" },
                        contentType = { "performer" }
                    ) { performer ->
                        PerformerGridItem(
                            performer = performer,
                            physicalRootPath = physicalRootPath,
                            onClick = { performer.id?.let { onPerformerClick(it) } }
                        )
                    }
                }

                if (uiState.movies.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("影片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    items(
                        items = uiState.movies,
                        key = { "m_${it.id}" },
                        contentType = { "movie" }
                    ) { movie ->
                        MovieGridItem(
                            movie = movie,
                            physicalRootPath = physicalRootPath,
                            onClick = { movie.id?.let { onMovieClick(it) } }
                        )
                    }
                }
            }
        }
    }
}
