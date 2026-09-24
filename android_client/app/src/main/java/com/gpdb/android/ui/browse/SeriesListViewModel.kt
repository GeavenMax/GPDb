package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.SeriesCollectionEntity
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SeriesListUiState(
    val isLoading: Boolean = true,
    val series: List<SeriesCollectionEntity> = emptyList(),
    val error: String? = null
)

class SeriesListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SeriesListUiState())
    val uiState: StateFlow<SeriesListUiState> = _uiState.asStateFlow()

    fun loadSeries() {
        if (_uiState.value.series.isNotEmpty()) return
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getSeriesPaged(500, 0)
                _uiState.update { it.copy(isLoading = false, series = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
