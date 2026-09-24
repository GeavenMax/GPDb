package com.gpdb.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.relations.MovieWithPerformers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.repository.UserRepository

data class MovieDetailUiState(
    val isLoading: Boolean = true,
    val movieDetail: MovieWithPerformers? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val directors: List<String> = emptyList(),
    val rating: Float? = null,
    val status: String? = null,
    val isFavorite: Boolean = false,
    val seriesName: String? = null,
    val error: String? = null
)

class MovieDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    fun loadMovie(movieId: Long) {
        val db = DatabaseHolder.db
        if (db == null) {
            _uiState.update { it.copy(isLoading = false, error = "数据库尚未就绪") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = db.movieDao().getMovieWithPerformers(movieId)
                val eps = db.movieDao().getEpisodesForMovie(movieId)
                
                val userRepo = UserRepository(db, db.userActionDao())
                val userData = userRepo.getMovieData(movieId)
                val isFav = userRepo.isFavorite("movie", movieId.toString())
                
                val dCursor = db.openHelper.readableDatabase.query("SELECT d.name FROM directors d JOIN movie_directors md ON d.id = md.director_id WHERE md.movie_id = ?", arrayOf(movieId))
                val dirs = mutableListOf<String>()
                while(dCursor.moveToNext()) {
                    dirs.add(dCursor.getString(0))
                }
                dCursor.close()

                var seriesName: String? = null
                if (detail != null) {
                    val title = detail.movie.title
                    val sCursor = db.openHelper.readableDatabase.query("SELECT root_title FROM series_collections WHERE ? LIKE root_title || '%' OR ? LIKE '%' || root_title || '%' ORDER BY LENGTH(root_title) DESC LIMIT 1", arrayOf(title, title))
                    if (sCursor.moveToFirst()) {
                        seriesName = sCursor.getString(0)
                    }
                    sCursor.close()
                }

                if (detail != null) {
                    _uiState.update { it.copy(
                        isLoading = false, 
                        movieDetail = detail,
                        episodes = eps,
                        directors = dirs,
                        rating = userData?.rating,
                        status = userData?.status,
                        isFavorite = isFav,
                        seriesName = seriesName
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "未找到该影片记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "加载详情失败") }
            }
        }
    }

    fun setRating(rating: Float) {
        val movieId = _uiState.value.movieDetail?.movie?.id ?: return
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            UserRepository(db, db.userActionDao()).updateMovieRating(movieId, rating)
            _uiState.update { it.copy(rating = rating) }
        }
    }

    fun setStatus(status: String?) {
        val movieId = _uiState.value.movieDetail?.movie?.id ?: return
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            UserRepository(db, db.userActionDao()).updateMovieStatus(movieId, status)
            _uiState.update { it.copy(status = status) }
        }
    }

    fun toggleFavorite() {
        val movieId = _uiState.value.movieDetail?.movie?.id ?: return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            UserRepository(db, db.userActionDao()).toggleFavorite("movie", movieId.toString(), newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }
}
