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
import kotlinx.coroutines.flow.first

data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    val episodeDetail: EpisodeWithPerformers? = null,
    val parentMovie: MovieEntity? = null,
    val error: String? = null,
    val isFavorite: Boolean = false,
    val translationLoading: Boolean = false,
    val translatedSummary: String? = null,
    val translationError: String? = null
)

class EpisodeDetailViewModel(
    private val translationService: com.gpdb.android.data.ai.LLMTranslationService,
    private val appSettingsRepository: com.gpdb.android.data.settings.AppSettingsRepository
) : ViewModel() {

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
                        isFavorite = isFav,
                        translatedSummary = detail.episode.descriptionZh
                    ) }

                    // Check for auto-translate
                    val autoTranslate = appSettingsRepository.llmAutoTranslateFlow.first()
                    if (autoTranslate && detail.episode.descriptionZh.isNullOrBlank() && !detail.episode.description.isNullOrBlank()) {
                        translateSummary(detail.episode.description)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "未找到该分集记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "加载详情失败") }
            }
        }
    }


    fun translateSummary(text: String) {
        if (_uiState.value.translationLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(translationLoading = true, translationError = null) }
            try {
                val translated = translationService.translate(text)
                _uiState.update { it.copy(translationLoading = false, translatedSummary = translated) }
                
                // Save to DB
                val epId = _uiState.value.episodeDetail?.episode?.id
                if (epId != null) {
                    val db = DatabaseHolder.db
                    db?.browseDao()?.updateEpisodeDescriptionZh(epId, translated)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(translationLoading = false, translationError = e.localizedMessage ?: "翻译失败") }
            }
        }
    }
}
