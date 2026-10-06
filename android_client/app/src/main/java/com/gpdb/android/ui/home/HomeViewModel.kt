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

enum class HomeTab { ALL_MOVIES, EPISODES, SERIES }

enum class DateFilter(val i18nKey: String, val defaultLabel: String) {
    ALL("common.all", "全部"),
    LAST_SCRAPED("filter.lastScraped", "上次入库"),
    RECENT_7("filter.recent7", "最近7天"),
    RECENT_30("filter.recent30", "最近30天"),
    RECENT_90("filter.recent90", "最近90天"),
    RECENT_YEAR("filter.recentYear", "本年度");

    @androidx.compose.runtime.Composable
    fun getLabel(): String = com.gpdb.android.util.I18n.string(i18nKey, defaultVal = defaultLabel)
}

data class HomeUiState(
    val homeTab: HomeTab = HomeTab.ALL_MOVIES,
    val dateFilter: DateFilter = DateFilter.ALL,
    val mountStatus: MountStatus = MountStatus.Idle,
    val totalCount: Int = 0,
    val zipEntriesCount: Int = 0,
    val movies: List<MovieEntity> = emptyList(),
    val episodes: List<com.gpdb.android.data.db.entities.EpisodeEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val episodesLoadingMore: Boolean = false,
    val episodesHasMore: Boolean = true,
    val physicalRootPath: String = "",
    val sortByYear: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val pageSize = 40
    private var currentOffset = 0
    private var episodesOffset = 0

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

                // 启动无感增量自动同步（延迟 3.5 秒，让首页首屏 UI 彻底渲染完毕且处于空闲）
                viewModelScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(3500)
                    com.gpdb.android.data.scraper.GpdbSyncManager.triggerStartupSync(
                        context = context.applicationContext,
                        database = db,
                        physicalRoot = mountRoot,
                        onNewContentAdded = { totalNew ->
                            if (totalNew > 0) {
                                android.util.Log.i("HomeViewModel", "后台自动增量入库完成，发现 $totalNew 项新内容，刷新前台列表")
                                refreshSilently()
                            }
                        }
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

    fun refreshSilently() {
        val db = DatabaseHolder.db ?: return
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalCount = db.movieDao().getMovieCount()
                currentOffset = 0
                val list = queryMoviesByFilter(db, state.dateFilter, state.sortByYear, pageSize, 0)
                currentOffset = list.size
                _uiState.update {
                    it.copy(
                        totalCount = totalCount,
                        movies = list,
                        hasMore = list.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "静默刷新异常: ${e.message}")
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
                val nextMovies = queryMoviesByFilter(db, state.dateFilter, state.sortByYear, pageSize, currentOffset)
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
            if (tab == HomeTab.EPISODES && _uiState.value.episodes.isEmpty()) {
                loadEpisodesInitial()
            }
        }
    }

    fun setDateFilter(filter: DateFilter) {
        if (_uiState.value.dateFilter != filter) {
            _uiState.update { it.copy(dateFilter = filter) }
            val currentTab = _uiState.value.homeTab
            if (currentTab == HomeTab.ALL_MOVIES) {
                reloadMovies()
            } else if (currentTab == HomeTab.EPISODES) {
                loadEpisodesInitial()
            }
        }
    }

    fun reloadMovies() {
        val db = DatabaseHolder.db ?: return
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            currentOffset = 0
            val list = queryMoviesByFilter(db, state.dateFilter, state.sortByYear, pageSize, 0)
            currentOffset = list.size
            _uiState.update {
                it.copy(
                    movies = list,
                    isLoading = false,
                    hasMore = list.size >= pageSize
                )
            }
        }
    }

    private suspend fun queryMoviesByFilter(
        db: com.gpdb.android.data.db.GpdbDatabase,
        filter: DateFilter,
        sortByYear: Boolean,
        limit: Int,
        offset: Int
    ): List<MovieEntity> {
        return when (filter) {
            DateFilter.ALL -> {
                if (sortByYear) db.movieDao().getMoviesByYear(limit, offset)
                else db.movieDao().getMoviesList(limit, offset)
            }
            DateFilter.LAST_SCRAPED -> {
                db.movieDao().getMoviesByScrapedAt(limit, offset)
            }
            DateFilter.RECENT_7, DateFilter.RECENT_30, DateFilter.RECENT_90 -> {
                db.movieDao().getMoviesByMinYear(2026, limit, offset)
            }
            DateFilter.RECENT_YEAR -> {
                db.movieDao().getMoviesByMinYear(2025, limit, offset)
            }
        }
    }

    fun loadEpisodesInitial() {
        val db = DatabaseHolder.db ?: return
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            episodesOffset = 0
            val list = queryEpisodesByFilter(db, state.dateFilter, pageSize, 0)
            episodesOffset = list.size
            _uiState.update {
                it.copy(
                    episodes = list,
                    isLoading = false,
                    episodesHasMore = list.size >= pageSize
                )
            }
        }
    }

    fun loadMoreEpisodes() {
        val state = _uiState.value
        if (state.isLoading || state.episodesLoadingMore || !state.episodesHasMore) return
        val db = DatabaseHolder.db ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(episodesLoadingMore = true) }
            try {
                val nextEpisodes = queryEpisodesByFilter(db, state.dateFilter, pageSize, episodesOffset)
                episodesOffset += nextEpisodes.size
                _uiState.update {
                    it.copy(
                        episodes = it.episodes + nextEpisodes,
                        episodesLoadingMore = false,
                        episodesHasMore = nextEpisodes.size >= pageSize
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(episodesLoadingMore = false) }
            }
        }
    }

    private suspend fun queryEpisodesByFilter(
        db: com.gpdb.android.data.db.GpdbDatabase,
        filter: DateFilter,
        limit: Int,
        offset: Int
    ): List<com.gpdb.android.data.db.entities.EpisodeEntity> {
        val now = java.util.Calendar.getInstance()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return when (filter) {
            DateFilter.ALL, DateFilter.LAST_SCRAPED -> {
                db.episodeDao().getEpisodesPaged(limit, offset)
            }
            DateFilter.RECENT_7 -> {
                now.add(java.util.Calendar.DAY_OF_YEAR, -7)
                val minDate = format.format(now.time)
                db.episodeDao().getEpisodesByMinDate(minDate, limit, offset)
            }
            DateFilter.RECENT_30 -> {
                now.add(java.util.Calendar.DAY_OF_YEAR, -30)
                val minDate = format.format(now.time)
                db.episodeDao().getEpisodesByMinDate(minDate, limit, offset)
            }
            DateFilter.RECENT_90 -> {
                now.add(java.util.Calendar.DAY_OF_YEAR, -90)
                val minDate = format.format(now.time)
                db.episodeDao().getEpisodesByMinDate(minDate, limit, offset)
            }
            DateFilter.RECENT_YEAR -> {
                db.episodeDao().getEpisodesByMinDate("2026-01-01", limit, offset)
            }
        }
    }

    fun toggleSortOrder() {
        val nextSort = !_uiState.value.sortByYear
        _uiState.update { it.copy(sortByYear = nextSort) }
        reloadMovies()
    }
}
