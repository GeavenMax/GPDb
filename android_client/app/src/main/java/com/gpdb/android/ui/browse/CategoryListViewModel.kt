package com.gpdb.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.CategoryGlossaryEntity
import com.gpdb.android.data.repository.BrowseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryListUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryGlossaryEntity> = emptyList(),
    val error: String? = null
)

class CategoryListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    fun loadCategories() {
        if (_uiState.value.categories.isNotEmpty()) return
        val db = DatabaseHolder.db ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val repo = BrowseRepository(db.browseDao())
                val list = repo.getAllCategories()
                _uiState.update { it.copy(isLoading = false, categories = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}
