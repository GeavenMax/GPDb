package com.gpdb.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.relations.PerformerWithMovies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.gpdb.android.data.db.entities.EpisodeEntity

data class PerformerDetailUiState(
    val isLoading: Boolean = true,
    val performerDetail: PerformerWithMovies? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val error: String? = null,
    val isFavorite: Boolean = false
)

class PerformerDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PerformerDetailUiState())
    val uiState: StateFlow<PerformerDetailUiState> = _uiState.asStateFlow()

    fun toggleFavorite() {
        val performerId = _uiState.value.performerDetail?.performer?.id ?: return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            com.gpdb.android.data.repository.UserRepository(db, db.userActionDao()).toggleFavorite("performer", performerId.toString(), newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }

    fun loadPerformer(performerId: Long) {
        val currentDetail = _uiState.value.performerDetail
        if (currentDetail != null && currentDetail.performer.id == performerId) {
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
                val detail = db.performerDao().getPerformerWithMovies(performerId)
                val episodesList = db.performerDao().getEpisodesForPerformer(performerId)
                val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                val isFav = userRepo.isFavorite("performer", performerId.toString())
                if (detail != null) {
                    val sortedDetail = detail.copy(
                        movies = detail.movies.sortedByDescending { it.releaseYear ?: 0 }
                    )
                    _uiState.update { it.copy(
                        isLoading = false, 
                        performerDetail = sortedDetail,
                        episodes = episodesList,
                        isFavorite = isFav
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "未找到该演员记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "加载详情失败") }
            }
        }
    }
}
