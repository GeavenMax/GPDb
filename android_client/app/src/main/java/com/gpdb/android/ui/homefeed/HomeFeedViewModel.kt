package com.gpdb.android.ui.homefeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.repository.HomeFeedData
import com.gpdb.android.data.repository.HomeFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeFeedViewModel(
    private val repository: HomeFeedRepository = HomeFeedRepository()
) : ViewModel() {

    private val _feedData = MutableStateFlow(HomeFeedData())
    val feedData: StateFlow<HomeFeedData> = _feedData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            com.gpdb.android.data.db.DatabaseHolder.isReadyFlow.collect { isReady ->
                if (isReady) {
                    loadFeed()
                }
            }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            val data = repository.getHomeFeed()
            _feedData.value = data
            _isLoading.value = false
        }
    }

    fun refreshLuckyMovies() {
        viewModelScope.launch {
            _feedData.update { it.copy(isLuckyLoading = true) }
            val nextMovies = repository.getMoreLuckyMovies()
            _feedData.update { it.copy(luckyMovies = nextMovies, isLuckyLoading = false) }
        }
    }
}
