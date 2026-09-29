package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.repository.BrowseRepository
import com.gpdb.android.data.repository.DirectorSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectorListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val directors: List<DirectorSummary> = emptyList(),
    val sortBy: String = "works", // "works" or "name"
    val searchQuery: String = "",
    val hasMore: Boolean = false,
    val error: String? = null
)

class DirectorListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DirectorListUiState())
    val uiState: StateFlow<DirectorListUiState> = _uiState.asStateFlow()

    private val pageSize = 60
    private var currentOffset = 0

    fun loadDirectors(sortBy: String? = null, query: String? = null, isRefresh: Boolean = false) {
        val newSortBy = sortBy ?: _uiState.value.sortBy
        val newQuery = query ?: _uiState.value.searchQuery
        val sortChanged = sortBy != null && sortBy != _uiState.value.sortBy
        val queryChanged = query != null && query != _uiState.value.searchQuery

        if (sortChanged || queryChanged || isRefresh) {
            currentOffset = 0
            _uiState.update {
                it.copy(
                    sortBy = newSortBy,
                    searchQuery = newQuery,
                    directors = emptyList(),
                    hasMore = false,
                    isLoading = true,
                    error = null
                )
            }
        } else if (_uiState.value.directors.isNotEmpty()) {
            return
        }

        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getDirectorsPaged(
                    sortBy = newSortBy,
                    limit = pageSize,
                    offset = 0,
                    search = newQuery.ifBlank { null }
                )
                currentOffset = list.size
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        directors = list,
                        hasMore = list.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        val db = DatabaseHolder.db ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val moreList = repo.getDirectorsPaged(
                    sortBy = _uiState.value.sortBy,
                    limit = pageSize,
                    offset = currentOffset,
                    search = _uiState.value.searchQuery.ifBlank { null }
                )
                currentOffset += moreList.size
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        directors = it.directors + moreList,
                        hasMore = moreList.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
