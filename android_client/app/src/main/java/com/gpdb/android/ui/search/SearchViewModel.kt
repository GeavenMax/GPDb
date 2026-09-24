package com.gpdb.android.ui.search

import android.app.Application
import com.gpdb.android.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.repository.SearchRepository
import com.gpdb.android.ui.home.HomeViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
        val db = DatabaseHolder.db
        if (db != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val repo = com.gpdb.android.data.repository.BrowseRepository(db.browseDao())
                    val cats = repo.getAllCategories()
                    _uiState.update { it.copy(categories = cats) }
                } catch (e: Exception) {}
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

    fun initRepository() {
        if (repository == null) {
            val db = DatabaseHolder.db ?: return
            repository = SearchRepository(db.searchDao())
            
            if (searchQueryFlow.value.isNotBlank()) {
                viewModelScope.launch {
                    performSearch(searchQueryFlow.value)
                }
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchQueryFlow.value = newQuery
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(movies = emptyList(), performers = emptyList(), isSearching = false)
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true)
        
        try {
            val repo = repository ?: return
            val movies = repo.searchMovies(query, limit = 50)
            val performers = repo.searchPerformers(query, limit = 20)
            

            _uiState.value = _uiState.value.copy(
                movies = movies,
                performers = performers,
                isSearching = false
            )
            if (movies.isNotEmpty() || performers.isNotEmpty()) {
                viewModelScope.launch { appPreferences.addSearchHistory(query) }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(isSearching = false)
        }
    }
}
