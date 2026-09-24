package com.gpdb.android.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab {
    FAV_MOVIES, WISHLIST, WATCHED, FAV_PERFORMERS, FAV_SERIES
}

data class LibraryUiState(
    val currentTab: LibraryTab = LibraryTab.FAV_MOVIES,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val movies: List<MovieEntity> = emptyList(),
    val performers: List<PerformerEntity> = emptyList(),
    val series: List<com.gpdb.android.data.db.entities.SeriesCollectionEntity> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null
)

class LibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val pageSize = 50
    private var currentOffset = 0

    fun setTab(tab: LibraryTab) {
        if (_uiState.value.currentTab != tab) {
            _uiState.update { it.copy(currentTab = tab) }
            refresh()
        }
    }
    
    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query, movies = emptyList(), performers = emptyList(), series = emptyList(), hasMore = false) }
        refresh()
    }

    fun refresh() {
        val tab = _uiState.value.currentTab
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlyEmpty = when (tab) {
                LibraryTab.FAV_PERFORMERS -> _uiState.value.performers.isEmpty()
                LibraryTab.FAV_SERIES -> _uiState.value.series.isEmpty()
                else -> _uiState.value.movies.isEmpty()
            }
            if (isCurrentlyEmpty) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(error = null) }
            }
            
            var db = DatabaseHolder.db
            var retries = 0
            while (db == null && retries < 20) {
                kotlinx.coroutines.delay(500)
                db = DatabaseHolder.db
                retries++
            }
            
            if (db == null) {
                if (isCurrentlyEmpty) {
                    _uiState.update { it.copy(isLoading = false, error = "数据库挂载未完成，请返回主页等待完成") }
                }
                return@launch
            }
            
            try {
                val repo = BrowseRepository(db.browseDao())
                currentOffset = 0
                if (tab == LibraryTab.FAV_PERFORMERS) {
                    val pList = repo.getFavoritePerformers(pageSize, 0, _uiState.value.searchQuery)
                    currentOffset = pList.size
                    _uiState.update { it.copy(isLoading = false, performers = pList, movies = emptyList(), series = emptyList(), hasMore = pList.size >= pageSize) }
                } else if (tab == LibraryTab.FAV_SERIES) {
                    val sList = repo.getFavoriteSeries(pageSize, 0, _uiState.value.searchQuery)
                    currentOffset = sList.size
                    _uiState.update { it.copy(isLoading = false, series = sList, performers = emptyList(), movies = emptyList(), hasMore = sList.size >= pageSize) }
                } else {
                    val mList = when (tab) {
                        LibraryTab.FAV_MOVIES -> repo.getLibraryMovies("fav", pageSize, 0, _uiState.value.searchQuery)
                        LibraryTab.WISHLIST -> repo.getLibraryMovies("wishlist", pageSize, 0, _uiState.value.searchQuery)
                        LibraryTab.WATCHED -> repo.getLibraryMovies("watched", pageSize, 0, _uiState.value.searchQuery)
                        else -> emptyList()
                    }
                    currentOffset = mList.size
                    android.util.Log.i("LibraryViewModel", "loadInitial success, size=${mList.size}")
                    _uiState.update { it.copy(isLoading = false, movies = mList, performers = emptyList(), series = emptyList(), hasMore = mList.size >= pageSize) }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Exception: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val repo = BrowseRepository(db.browseDao())
                if (state.currentTab == LibraryTab.FAV_PERFORMERS) {
                    val nextList = repo.getFavoritePerformers(pageSize, currentOffset, _uiState.value.searchQuery)
                    currentOffset += nextList.size
                    _uiState.update { it.copy(isLoadingMore = false, performers = it.performers + nextList, hasMore = nextList.size >= pageSize) }
                } else if (state.currentTab == LibraryTab.FAV_SERIES) {
                    val nextList = repo.getFavoriteSeries(pageSize, currentOffset, _uiState.value.searchQuery)
                    currentOffset += nextList.size
                    _uiState.update { it.copy(isLoadingMore = false, series = it.series + nextList, hasMore = nextList.size >= pageSize) }
                } else {
                    val nextMovies = when (state.currentTab) {
                        LibraryTab.FAV_MOVIES -> repo.getLibraryMovies("fav", pageSize, currentOffset, _uiState.value.searchQuery)
                        LibraryTab.WISHLIST -> repo.getLibraryMovies("wishlist", pageSize, currentOffset, _uiState.value.searchQuery)
                        LibraryTab.WATCHED -> repo.getLibraryMovies("watched", pageSize, currentOffset, _uiState.value.searchQuery)
                        else -> emptyList()
                    }
                    currentOffset += nextMovies.size
                    _uiState.update { it.copy(isLoadingMore = false, movies = it.movies + nextMovies, hasMore = nextMovies.size >= pageSize) }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryViewModel", "Exception: ${e.message}", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
