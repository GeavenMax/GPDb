package com.gpdb.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.relations.EpisodeWithPerformers
import com.gpdb.android.data.db.entities.MovieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    val episodeDetail: EpisodeWithPerformers? = null,
    val parentMovie: MovieEntity? = null,
    val error: String? = null,
    val isFavorite: Boolean = false
)

class EpisodeDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    fun toggleFavorite() {
        val epId = _uiState.value.episodeDetail?.episode?.id ?: return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            com.gpdb.android.data.repository.UserRepository(db, db.userActionDao()).toggleFavorite("episode", epId.toString(), newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }

    fun loadEpisode(episodeId: Long) {
        val currentDetail = _uiState.value.episodeDetail
        if (currentDetail != null && currentDetail.episode.id == episodeId) {
            return
        }
        val db = DatabaseHolder.db
        if (db == null) {
            _uiState.update { it.copy(isLoading = false, error = "数据库尚未就绪") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = db.episodeDao().getEpisodeWithPerformers(episodeId)
                if (detail != null) {
                    val movieId = detail.episode.movieId
                    val parent = if (movieId != null) db.movieDao().getMovieById(movieId) else null
                    val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                    val isFav = userRepo.isFavorite("episode", episodeId.toString())
                    _uiState.update { it.copy(
                        isLoading = false, 
                        episodeDetail = detail,
                        parentMovie = parent,
                        isFavorite = isFav
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "未找到该分集记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "加载详情失败") }
            }
        }
    }
}
