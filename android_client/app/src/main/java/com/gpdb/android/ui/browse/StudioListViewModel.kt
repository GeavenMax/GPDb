package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioListUiState(
    val isLoading: Boolean = true,
    val studios: List<String> = emptyList(),
    val sortBy: String = "works",
    val searchQuery: String = "",
    val error: String? = null
)

class StudioListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StudioListUiState())
    val uiState: StateFlow<StudioListUiState> = _uiState.asStateFlow()

    fun loadStudios(sortBy: String? = null, query: String? = null) {
        val newSortBy = sortBy ?: _uiState.value.sortBy
        val newQuery = query ?: _uiState.value.searchQuery
        val sortChanged = sortBy != null && sortBy != _uiState.value.sortBy
        val queryChanged = query != null && query != _uiState.value.searchQuery
        
        if (sortChanged || queryChanged) {
            _uiState.update { it.copy(sortBy = newSortBy, searchQuery = newQuery) }
        } else if (_uiState.value.studios.isNotEmpty()) {
            return
        }
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getAllStudios(newSortBy, _uiState.value.searchQuery)
                _uiState.update { it.copy(isLoading = false, studios = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
