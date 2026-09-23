package com.gpdb.android.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.image.ZipHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// ============================================================
//  挂载状态枚举与 UI State
// ============================================================
sealed interface MountStatus {
    object Idle : MountStatus
    data class Mounting(val stepText: String, val progress: Float? = null) : MountStatus
    object Ready : MountStatus
    data class Error(val title: String, val detail: String) : MountStatus
}

data class HomeUiState(
    val mountStatus: MountStatus = MountStatus.Idle,
    val totalCount: Int = 0,
    val zipEntriesCount: Int = 0,
    val movies: List<MovieEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val searchQuery: String = "",
    val physicalRootPath: String = "",
    val sortByYear: Boolean = false
)

// ============================================================
//  HomeViewModel — 异步挂载流与影片检索管理
// ============================================================
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val pageSize = 40
    private var currentOffset = 0
    private var searchJob: Job? = null

    /**
     * 统一全异步挂载入口（DB 连接 + 23万条目 ZIP 解析）
     * 严格在 Dispatchers.IO 调度，绝不发生主线程丢帧。
     */
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

            // 1. 异步初始化挂载 SQLite (TRUNCATE mode)
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

            // 2. 异步解析 15GB ZIP 中央目录 (23万+条目)
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

            // 3. 读取第一页影片数据
            _uiState.update {
                it.copy(
                    mountStatus = MountStatus.Mounting(stepText = "正在加载影视元数据列表...")
                )
            }

            try {
                val totalCount = db.movieDao().getMovieCount()
                currentOffset = 0
                val initialList = if (_uiState.value.sortByYear) {
                    db.movieDao().getMoviesByYear(limit = pageSize, offset = 0)
                } else {
                    db.movieDao().getMoviesList(limit = pageSize, offset = 0)
                }
                currentOffset += initialList.size

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

    /**
     * 滑动到底部触发加载下一页 (Infinite Scroll)
     */
    fun loadMoreMovies() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        val db = DatabaseHolder.db ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextList = if (state.searchQuery.isNotBlank()) {
                    db.movieDao().searchMovies(
                        query = state.searchQuery,
                        limit = pageSize,
                        offset = currentOffset
                    )
                } else if (state.sortByYear) {
                    db.movieDao().getMoviesByYear(limit = pageSize, offset = currentOffset)
                } else {
                    db.movieDao().getMoviesList(limit = pageSize, offset = currentOffset)
                }

                currentOffset += nextList.size
                _uiState.update {
                    it.copy(
                        movies = it.movies + nextList,
                        isLoadingMore = false,
                        hasMore = nextList.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * 全局模糊搜索
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val db = DatabaseHolder.db ?: return@launch
            if (query.isBlank()) {
                currentOffset = 0
                val initialList = if (_uiState.value.sortByYear) {
                    db.movieDao().getMoviesByYear(limit = pageSize, offset = 0)
                } else {
                    db.movieDao().getMoviesList(limit = pageSize, offset = 0)
                }
                currentOffset += initialList.size
                _uiState.update {
                    it.copy(
                        movies = initialList,
                        isLoading = false,
                        hasMore = initialList.size >= pageSize
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }
            currentOffset = 0
            val results = db.movieDao().searchMovies(query = query, limit = pageSize, offset = 0)
            currentOffset += results.size

            _uiState.update {
                it.copy(
                    movies = results,
                    isLoading = false,
                    hasMore = results.size >= pageSize
                )
            }
        }
    }

    /**
     * 切换排序
     */
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
