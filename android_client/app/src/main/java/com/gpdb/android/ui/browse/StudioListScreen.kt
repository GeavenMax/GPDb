package com.gpdb.android.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.gpdb.android.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioListScreen(
    viewModel: StudioListViewModel,
    onStudioClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadStudios()
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

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
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.studios, key = { it.name }) { studio ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = studio.nameZh ?: studio.name,
                                    fontWeight = if (studio.nameZh != null) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            supportingContent = if (studio.nameZh != null && studio.nameZh != studio.name) {
                                {
                                    Text(
                                        text = studio.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else null,
                            leadingContent = {
                                Icon(
                                    Icons.Default.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable { onStudioClick(studio.name) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
