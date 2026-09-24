package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilteredMovieListUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val movies: List<MovieEntity> = emptyList(),
    val error: String? = null
)

class FilteredMovieListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FilteredMovieListUiState())
    val uiState: StateFlow<FilteredMovieListUiState> = _uiState.asStateFlow()

    fun load(filterType: String, filterValue: String) {
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, title = filterValue, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = when (filterType) {
                    "category" -> repo.getMoviesByCategory(filterValue, limit = 500)
                    "series" -> repo.getMoviesBySeries(filterValue, limit = 500)
                    "director" -> repo.getMoviesByDirector(filterValue, limit = 500)
                    else -> emptyList()
                }
                _uiState.update { it.copy(isLoading = false, movies = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
