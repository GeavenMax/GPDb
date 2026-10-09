package com.gpdb.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.gpdb.android.ui.components.MovieGridItem
import com.gpdb.android.ui.components.PerformerGridItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    physicalRootPath: String,
    viewModel: SearchViewModel,
    initialScope: SearchScope = SearchScope.ALL,
    onBackClick: () -> Unit,
    onMovieClick: (Long) -> Unit,
    onStudioClick: (String) -> Unit = {},
    onPerformerClick: (Long) -> Unit,
    onEpisodeClick: (Long) -> Unit = {},
    onDirectorClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialScope) {
        viewModel.setSearchScope(initialScope)
    }

    LaunchedEffect(physicalRootPath) {
        viewModel.initRepository()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val hasResults = uiState.movies.isNotEmpty() || uiState.episodes.isNotEmpty() ||
            uiState.performers.isNotEmpty() || uiState.studios.isNotEmpty() || uiState.directors.isNotEmpty()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.query,
                            onValueChange = { viewModel.updateQuery(it) },
                            placeholder = {
                                val hint = when (uiState.scope) {
                                    SearchScope.ALL -> com.gpdb.android.util.I18n.string("search.placeholder")
                                    SearchScope.MOVIES -> com.gpdb.android.util.I18n.string("search.moviesOnly", defaultVal = "搜索长篇电影...")
                                    SearchScope.EPISODES -> com.gpdb.android.util.I18n.string("search.episodesOnly", defaultVal = "搜索分集标题...")
                                    SearchScope.PERFORMERS -> com.gpdb.android.util.I18n.string("search.performersOnly", defaultVal = "搜索演员姓名...")
                                    SearchScope.STUDIOS -> com.gpdb.android.util.I18n.string("search.studiosOnly", defaultVal = "搜索片商厂牌...")
                                    SearchScope.DIRECTORS -> com.gpdb.android.util.I18n.string("search.directorsOnly", defaultVal = "搜索导演条目...")
                                }
                                Text(hint)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
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
                                        Icon(Icons.Default.Close, contentDescription = com.gpdb.android.util.I18n.string("common.clear"))
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = com.gpdb.android.util.I18n.string("common.back"))
                        }
                    }
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val scopes = listOf(
                        SearchScope.ALL to com.gpdb.android.util.I18n.string("common.all"),
                        SearchScope.MOVIES to com.gpdb.android.util.I18n.string("nav.featureMovies"),
                        SearchScope.EPISODES to com.gpdb.android.util.I18n.string("nav.episodes"),
                        SearchScope.PERFORMERS to com.gpdb.android.util.I18n.string("nav.performers"),
                        SearchScope.STUDIOS to com.gpdb.android.util.I18n.string("nav.studios"),
                        SearchScope.DIRECTORS to com.gpdb.android.util.I18n.string("nav.directors")
                    )
                    scopes.forEach { (scope, label) ->
                        FilterChip(
                            selected = uiState.scope == scope,
                            onClick = { viewModel.setSearchScope(scope) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isSearching && uiState.query.isNotEmpty() && !hasResults) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.query.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (searchHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(com.gpdb.android.util.I18n.string("search.history"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { 
                            viewModel.viewModelScope.launch { viewModel.appPreferences.clearSearchHistory() }
                        }) { Text(com.gpdb.android.util.I18n.string("common.clear")) }
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

                if (uiState.categories.isNotEmpty()) {
                    Text(
                        com.gpdb.android.util.I18n.string("search.hotCategories"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val currentLang = com.gpdb.android.util.LocalAppLanguage.current
                    val isChinese = currentLang.isChinese
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.categories.forEach { cat ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.updateQuery(cat.term) },
                                label = { Text(if (isChinese) cat.zh else cat.term) }
                            )
                        }
                    }
                } else if (searchHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(com.gpdb.android.util.I18n.string("search.emptyPrompt"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (!hasResults) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(com.gpdb.android.util.I18n.string("search.noResults"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val gridState = rememberSaveable(
                saver = LazyGridState.Saver,
                key = "search_grid_state"
            ) {
                LazyGridState()
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(if (uiState.episodes.isNotEmpty() || uiState.directors.isNotEmpty() || uiState.studios.isNotEmpty()) 1 else 3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.performers.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(com.gpdb.android.util.I18n.string("search.performers"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
                        Text(com.gpdb.android.util.I18n.string("search.movies"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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

                if (uiState.episodes.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(com.gpdb.android.util.I18n.string("nav.episodes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    items(
                        items = uiState.episodes,
                        key = { "e_${it.id}" }
                    ) { episode ->
                        com.gpdb.android.ui.components.EpisodeListItem(
                            episodeId = episode.id,
                            title = episode.title,
                            thumbnailUrl = episode.thumbnailUrl,
                            physicalRootPath = physicalRootPath,
                            releaseDate = episode.releaseDate,
                            studioName = episode.studioName,
                            onClick = { episode.id?.let(onEpisodeClick) }
                        )
                    }
                }

                if (uiState.studios.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(com.gpdb.android.util.I18n.string("nav.studios"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    items(
                        items = uiState.studios,
                        key = { "s_${it.id}" }
                    ) { studio ->
                        ListItem(
                            headlineContent = { Text(studio.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { studio.nameZh?.let { Text(it) } },
                            modifier = Modifier.fillMaxWidth().clickable { onStudioClick(studio.name) }
                        )
                    }
                }

                if (uiState.directors.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(com.gpdb.android.util.I18n.string("nav.directors"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    items(
                        items = uiState.directors,
                        key = { "d_${it.id}" }
                    ) { director ->
                        ListItem(
                            headlineContent = { Text(director.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${director.worksCount} ${com.gpdb.android.util.I18n.string("common.works", defaultVal = "部作品")}") },
                            modifier = Modifier.fillMaxWidth().clickable { onDirectorClick(director.name) }
                        )
                    }
                }
            }
        }
    }
}
