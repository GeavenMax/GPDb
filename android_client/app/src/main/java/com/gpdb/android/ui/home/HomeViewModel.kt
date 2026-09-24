package com.gpdb.android.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.image.ZipHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import com.gpdb.android.data.db.entities.MovieEntity

sealed class MountStatus {
    object Idle : MountStatus()
    data class Mounting(val stepText: String) : MountStatus()
    object Ready : MountStatus()
    data class Error(val title: String, val detail: String) : MountStatus()
}

enum class HomeTab { ALL_MOVIES, SERIES }

data class HomeUiState(
    val homeTab: HomeTab = HomeTab.ALL_MOVIES,
    val mountStatus: MountStatus = MountStatus.Idle,
    val totalCount: Int = 0,
    val zipEntriesCount: Int = 0,
    val movies: List<MovieEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val physicalRootPath: String = "",
    val sortByYear: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val pageSize = 40
    private var currentOffset = 0

    fun mountAndInitialize(
        context: Context,
        mountRoot: String,
        dbPath: String,
        zipPath: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    physicalRootPath = mountRoot,
                    mountStatus = MountStatus.Mounting(stepText = "正在连接 GPDb.db 数据库 (TRUNCATE FUSE 模式)...")
                )
            }

            val dbResult = DatabaseHolder.initDatabaseAsync(context, dbPath)
            if (dbResult.isFailure) {
                val ex = dbResult.exceptionOrNull()
                val errorDetail = "${ex?.javaClass?.simpleName}: ${ex?.message}\n${ex?.stackTraceToString()?.take(500)}"
                _uiState.update {
                    it.copy(
                        mountStatus = MountStatus.Error(
                            title = "数据库挂载失败",
                            detail = errorDetail
                        )
                    )
                }
                return@launch
            }

            val db = dbResult.getOrThrow()

            var zipCount = 0
            if (zipPath != null && File(zipPath).exists()) {
                _uiState.update {
                    it.copy(
                        mountStatus = MountStatus.Mounting(stepText = "正在解析 GPDb_Images.zip 图片索引 (23万+ 条目)...")
                    )
                }

                val zipResult = ZipHolder.setZipPath(zipPath)
                if (zipResult.isSuccess) {
                    zipCount = zipResult.getOrNull() ?: 0
                }
            }

            _uiState.update {
                it.copy(
                    mountStatus = MountStatus.Mounting(stepText = "正在加载影视元数据列表...")
                )
            }

            try {
                val totalCount = db.movieDao().getMovieCount()
                currentOffset = 0
                android.util.Log.i("HomeViewModel", "开始 getMoviesList")
                val initialList = if (_uiState.value.sortByYear) {
                    db.movieDao().getMoviesByYear(limit = pageSize, offset = 0)
                } else {
                    db.movieDao().getMoviesList(limit = pageSize, offset = 0)
                }
                currentOffset += initialList.size
                android.util.Log.i("HomeViewModel", "结束 getMoviesList, count = ${initialList.size}")

                _uiState.update {
                    it.copy(
                        mountStatus = MountStatus.Ready,
                        totalCount = totalCount,
                        zipEntriesCount = zipCount,
                        movies = initialList,
                        hasMore = initialList.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        mountStatus = MountStatus.Error(
                            title = "加载影库数据异常",
                            detail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    fun loadMoreMovies() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        val db = DatabaseHolder.db ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextMovies = if (state.sortByYear) db.movieDao().getMoviesByYear(pageSize, currentOffset) else db.movieDao().getMoviesList(pageSize, currentOffset)
                
                currentOffset += nextMovies.size
                _uiState.update {
                    it.copy(
                        movies = it.movies + nextMovies,
                        isLoadingMore = false,
                        hasMore = nextMovies.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setHomeTab(tab: HomeTab) {
        if (_uiState.value.homeTab != tab) {
            _uiState.update { it.copy(homeTab = tab) }
        }
    }

    fun toggleSortOrder() {
        val nextSort = !_uiState.value.sortByYear
        _uiState.update { it.copy(sortByYear = nextSort) }
        val db = DatabaseHolder.db ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            currentOffset = 0
            val list = if (nextSort) {
                db.movieDao().getMoviesByYear(limit = pageSize, offset = 0)
            } else {
                db.movieDao().getMoviesList(limit = pageSize, offset = 0)
            }
            currentOffset += list.size
            _uiState.update {
                it.copy(
                    movies = list,
                    isLoading = false,
                    hasMore = list.size >= pageSize
                )
            }
        }
    }
}
