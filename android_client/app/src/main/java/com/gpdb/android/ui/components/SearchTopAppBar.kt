package com.gpdb.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 统一的带即时搜索功能的 TopAppBar
 *
 * 自动管理展开/折叠状态，提供统一视觉外观，支持在动作区添加自定义按钮（如设置、排序菜单）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopAppBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = com.gpdb.android.util.I18n.string("browse.searchInList"),
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearching) {
        if (isSearching) {
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    TopAppBar(
        modifier = modifier,
        navigationIcon = { navigationIcon?.invoke() },
        title = {
            AnimatedContent(
                targetState = isSearching,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "SearchTopAppBarContent"
            ) { searching ->
                if (searching) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(placeholder) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else {
                    Text(title)
                }
            }
        },
        actions = {
            if (isSearching) {
                IconButton(onClick = {
                    isSearching = false
                    onSearchQueryChange("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = com.gpdb.android.util.I18n.string("browse.closeSearch"))
                }
            } else {
                IconButton(onClick = { isSearching = true }) {
                    Icon(Icons.Default.Search, contentDescription = com.gpdb.android.util.I18n.string("common.search"))
                }
                actions()
            }
        }
    )
}
