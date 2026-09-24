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
    val filterType: String = "",
    val movies: List<MovieEntity> = emptyList(),
    val error: String? = null,
    val isFavorite: Boolean = false
)

class FilteredMovieListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FilteredMovieListUiState())
    val uiState: StateFlow<FilteredMovieListUiState> = _uiState.asStateFlow()

    fun load(filterType: String, filterValue: String) {
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, title = filterValue, filterType = filterType, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = when (filterType) {
                    "category" -> repo.getMoviesByCategory(filterValue, limit = 500)
                    "series" -> repo.getMoviesBySeries(filterValue, limit = 500)
                    "director" -> repo.getMoviesByDirector(filterValue, limit = 500)
                    else -> emptyList()
                }
                
                var isFav = false
                if (filterType == "series") {
                    val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                    isFav = userRepo.isFavorite("series", filterValue)
                }

                _uiState.update { it.copy(isLoading = false, movies = list, isFavorite = isFav) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun toggleFavorite() {
        if (_uiState.value.filterType != "series") return
        val seriesName = _uiState.value.title
        if (seriesName.isBlank()) return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            com.gpdb.android.data.repository.UserRepository(db, db.userActionDao()).toggleFavorite("series", seriesName, newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }
}
