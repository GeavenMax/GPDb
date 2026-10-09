package com.gpdb.android.ui.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.preferences.AppPreferences
import com.gpdb.android.data.repository.BrowseRepository
import com.gpdb.android.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchScope { ALL, MOVIES, EPISODES, PERFORMERS, STUDIOS, DIRECTORS }

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val isSearching: Boolean = false,
    val movies: List<MovieEntity> = emptyList(),
    val episodes: List<com.gpdb.android.data.db.entities.EpisodeEntity> = emptyList(),
    val performers: List<PerformerEntity> = emptyList(),
    val studios: List<com.gpdb.android.data.db.entities.StudioEntity> = emptyList(),
    val directors: List<com.gpdb.android.data.repository.DirectorSummary> = emptyList(),
    val categories: List<com.gpdb.android.data.db.entities.CategoryGlossaryEntity> = emptyList()
)

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    val appPreferences = AppPreferences(application)
    val searchHistory = appPreferences.searchHistoryFlow.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var repository: SearchRepository? = null

    init {
        val currentDb = DatabaseHolder.db
        if (currentDb != null) {
            repository = SearchRepository(currentDb.searchDao(), currentDb.movieDao(), currentDb.performerDao())
            loadCategories(currentDb)
        }

        viewModelScope.launch(Dispatchers.IO) {
            DatabaseHolder.isReadyFlow.collectLatest { isReady ->
                if (isReady) {
                    val db = DatabaseHolder.db ?: return@collectLatest
                    if (repository == null) {
                        repository = SearchRepository(db.searchDao(), db.movieDao(), db.performerDao())
                    }
                    loadCategories(db)
                }
            }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }

    fun setSearchScope(scope: SearchScope) {
        _uiState.update { it.copy(scope = scope) }
        if (searchQueryFlow.value.isNotBlank()) {
            viewModelScope.launch { performSearch(searchQueryFlow.value) }
        }
    }

    private fun loadCategories(db: GpdbDatabase) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = BrowseRepository(db.browseDao())
                val cats = repo.getAllCategories()
                _uiState.update { it.copy(categories = cats) }
            } catch (e: Exception) {
                Log.w("SearchViewModel", "Failed to load categories: ${e.message}")
            }
        }
    }

    private fun getOrInitRepository(): SearchRepository? {
        if (repository != null) return repository
        val db = DatabaseHolder.db ?: return null
        val repo = SearchRepository(db.searchDao(), db.movieDao(), db.performerDao())
        repository = repo
        return repo
    }

    fun initRepository() {
        if (repository == null) {
            val db = DatabaseHolder.db ?: return
            repository = SearchRepository(db.searchDao(), db.movieDao(), db.performerDao())
        }
        if (searchQueryFlow.value.isNotBlank()) {
            viewModelScope.launch {
                performSearch(searchQueryFlow.value)
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchQueryFlow.value = newQuery
    }

    private suspend fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update { 
                it.copy(
                    movies = emptyList(), 
                    episodes = emptyList(),
                    performers = emptyList(), 
                    studios = emptyList(),
                    directors = emptyList(),
                    isSearching = false
                ) 
            }
            return
        }

        _uiState.update { it.copy(isSearching = true) }

        try {
            val repo = getOrInitRepository()
            val db = DatabaseHolder.db
            if (repo == null || db == null) {
                Log.w("SearchViewModel", "Search requested but database/repository is null")
                _uiState.update { it.copy(isSearching = false) }
                return
            }

            val currentScope = _uiState.value.scope
            var movies = emptyList<MovieEntity>()
            var episodes = emptyList<com.gpdb.android.data.db.entities.EpisodeEntity>()
            var performers = emptyList<PerformerEntity>()
            var studios = emptyList<com.gpdb.android.data.db.entities.StudioEntity>()
            var directors = emptyList<com.gpdb.android.data.repository.DirectorSummary>()

            when (currentScope) {
                SearchScope.ALL -> {
                    movies = repo.searchMovies(trimmed, limit = 40)
                    performers = repo.searchPerformers(trimmed, limit = 20)
                }
                SearchScope.MOVIES -> {
                    movies = repo.searchMovies(trimmed, limit = 60)
                }
                SearchScope.EPISODES -> {
                    episodes = repo.searchEpisodes(trimmed, limit = 60)
                }
                SearchScope.PERFORMERS -> {
                    performers = repo.searchPerformers(trimmed, limit = 60)
                }
                SearchScope.STUDIOS -> {
                    studios = db.studioDao().searchStudios(trimmed)
                }
                SearchScope.DIRECTORS -> {
                    directors = repo.searchDirectors(trimmed, limit = 60)
                }
            }

            _uiState.update {
                it.copy(
                    movies = movies,
                    episodes = episodes,
                    performers = performers,
                    studios = studios,
                    directors = directors,
                    isSearching = false
                )
            }

            if (movies.isNotEmpty() || episodes.isNotEmpty() || performers.isNotEmpty() || studios.isNotEmpty() || directors.isNotEmpty()) {
                viewModelScope.launch { appPreferences.addSearchHistory(trimmed) }
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "performSearch error: ${e.message}", e)
            _uiState.update { it.copy(isSearching = false) }
        }
    }
}
