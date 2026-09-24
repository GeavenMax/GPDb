package com.gpdb.android.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    viewModel: CategoryListViewModel,
    onCategoryClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCategories()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("分类标签 (Categories)") })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(text = uiState.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.categories, key = { it.term }) { cat ->
                        ListItem(
                            headlineContent = { Text(cat.zh) },
                            supportingContent = { Text(cat.term) },
                            leadingContent = { Icon(Icons.Default.Label, contentDescription = null) },
                            modifier = Modifier.clickable { onCategoryClick(cat.term) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
