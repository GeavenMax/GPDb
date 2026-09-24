package com.gpdb.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.repository.BrowseRepository
import com.gpdb.android.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.gpdb.android.data.db.entities.EpisodeEntity

data class StudioDetailUiState(
    val isLoading: Boolean = true,
    val studioName: String = "",
    val movies: List<MovieEntity> = emptyList(),
    val episodes: List<EpisodeEntity> = emptyList(),
    val isFavorite: Boolean = false,
    val error: String? = null
)

class StudioDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StudioDetailUiState())
    val uiState: StateFlow<StudioDetailUiState> = _uiState.asStateFlow()

    fun loadStudio(studioName: String) {
        if (_uiState.value.studioName == studioName && !_uiState.value.isLoading) {
            return
        }
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, studioName = studioName, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getMoviesByStudio(studioName, limit = 500)
                val epList = repo.getEpisodesByStudio(studioName, limit = 500)
                val userRepo = UserRepository(db, db.userActionDao())
                val isFav = userRepo.isFavorite("studio", studioName)
                _uiState.update { it.copy(isLoading = false, movies = list, episodes = epList, isFavorite = isFav) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun toggleFavorite() {
        val studioName = _uiState.value.studioName
        if (studioName.isBlank()) return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            UserRepository(db, db.userActionDao()).toggleFavorite("studio", studioName, newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }
}
