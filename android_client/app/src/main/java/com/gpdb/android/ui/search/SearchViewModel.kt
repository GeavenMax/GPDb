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

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val movies: List<MovieEntity> = emptyList(),
    val performers: List<PerformerEntity> = emptyList(),
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
            _uiState.update { it.copy(movies = emptyList(), performers = emptyList(), isSearching = false) }
            return
        }

        _uiState.update { it.copy(isSearching = true) }

        try {
            val repo = getOrInitRepository()
            if (repo == null) {
                Log.w("SearchViewModel", "Search requested but database/repository is null")
                _uiState.update { it.copy(isSearching = false) }
                return
            }

            val movies = repo.searchMovies(trimmed, limit = 50)
            val performers = repo.searchPerformers(trimmed, limit = 20)

            _uiState.update {
                it.copy(
                    movies = movies,
                    performers = performers,
                    isSearching = false
                )
            }

            if (movies.isNotEmpty() || performers.isNotEmpty()) {
                viewModelScope.launch { appPreferences.addSearchHistory(trimmed) }
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "performSearch error: ${e.message}", e)
            _uiState.update { it.copy(isSearching = false) }
        }
    }
}
