package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerformerListUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val performers: List<PerformerEntity> = emptyList(),
    val hasMore: Boolean = true,
    val sortBy: String = "works",
    val searchQuery: String = "",
    val error: String? = null
)

class PerformerListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PerformerListUiState())
    val uiState: StateFlow<PerformerListUiState> = _uiState.asStateFlow()
    
    private val pageSize = 50
    private var currentOffset = 0

    fun loadPerformers(sortBy: String? = null, query: String? = null) {
        val newSortBy = sortBy ?: _uiState.value.sortBy
        val newQuery = query ?: _uiState.value.searchQuery
        val queryChanged = query != null && query != _uiState.value.searchQuery
        val sortChanged = sortBy != null && sortBy != _uiState.value.sortBy
        if (sortChanged || queryChanged) {
            _uiState.update { it.copy(sortBy = newSortBy, searchQuery = newQuery, performers = emptyList(), hasMore = true) }
            currentOffset = 0
        } else if (_uiState.value.performers.isNotEmpty()) return // Already loaded
        
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getPerformersPaged(newSortBy, pageSize, 0, _uiState.value.searchQuery)
                currentOffset = list.size
                _uiState.update { it.copy(isLoading = false, performers = list, hasMore = list.size >= pageSize) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
    
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val nextList = repo.getPerformersPaged(_uiState.value.sortBy, pageSize, currentOffset, _uiState.value.searchQuery)
                currentOffset += nextList.size
                _uiState.update { 
                    it.copy(
                        isLoadingMore = false, 
                        performers = it.performers + nextList,
                        hasMore = nextList.size >= pageSize
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
