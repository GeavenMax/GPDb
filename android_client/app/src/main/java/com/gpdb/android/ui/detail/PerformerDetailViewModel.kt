package com.gpdb.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpdb.android.data.db.DatabaseHolder
import com.gpdb.android.data.db.relations.PerformerWithMovies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.db.entities.PerformerPbcProfile
import com.gpdb.android.data.db.GpdbDatabase

data class PerformerDetailUiState(
    val isLoading: Boolean = true,
    val performerDetail: PerformerWithMovies? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    val pbcProfile: PerformerPbcProfile? = null,
    val error: String? = null,
    val isFavorite: Boolean = false
)

class PerformerDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PerformerDetailUiState())
    val uiState: StateFlow<PerformerDetailUiState> = _uiState.asStateFlow()

    fun toggleFavorite() {
        val performerId = _uiState.value.performerDetail?.performer?.id ?: return
        val db = DatabaseHolder.db ?: return
        val newFav = !_uiState.value.isFavorite
        viewModelScope.launch(Dispatchers.IO) {
            com.gpdb.android.data.repository.UserRepository(db, db.userActionDao()).toggleFavorite("performer", performerId.toString(), newFav)
            _uiState.update { it.copy(isFavorite = newFav) }
        }
    }

    private fun loadPbcProfile(db: GpdbDatabase, performerId: Long): PerformerPbcProfile? {
        return try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT performer_id, pbc_url, pbc_id, birth_name, career_start, career_end, career_status, bio, birth_date, birth_place, ethnicity, astrology, height, weight, dick_size, foreskin, tattoos, piercings, roles, social_links, external_ids, tags, image_url, scraped_at FROM performer_pbc_profiles WHERE performer_id = ? LIMIT 1",
                arrayOf(performerId.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    PerformerPbcProfile(
                        performerId = it.getLong(0),
                        pbcUrl = it.getString(1),
                        pbcId = if (it.isNull(2)) null else it.getString(2),
                        birthName = if (it.isNull(3)) null else it.getString(3),
                        careerStart = if (it.isNull(4)) null else it.getInt(4),
                        careerEnd = if (it.isNull(5)) null else it.getInt(5),
                        careerStatus = if (it.isNull(6)) null else it.getString(6),
                        bio = if (it.isNull(7)) null else it.getString(7),
                        birthDate = if (it.isNull(8)) null else it.getString(8),
                        birthPlace = if (it.isNull(9)) null else it.getString(9),
                        ethnicity = if (it.isNull(10)) null else it.getString(10),
                        astrology = if (it.isNull(11)) null else it.getString(11),
                        height = if (it.isNull(12)) null else it.getString(12),
                        weight = if (it.isNull(13)) null else it.getString(13),
                        dickSize = if (it.isNull(14)) null else it.getString(14),
                        foreskin = if (it.isNull(15)) null else it.getString(15),
                        tattoos = if (it.isNull(16)) null else it.getString(16),
                        piercings = if (it.isNull(17)) null else it.getString(17),
                        roles = if (it.isNull(18)) null else it.getString(18),
                        socialLinks = if (it.isNull(19)) null else it.getString(19),
                        externalIds = if (it.isNull(20)) null else it.getString(20),
                        tags = if (it.isNull(21)) null else it.getString(21),
                        imageUrl = if (it.isNull(22)) null else it.getString(22),
                        scrapedAt = if (it.isNull(23)) null else it.getString(23)
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadPerformer(performerId: Long) {
        val currentDetail = _uiState.value.performerDetail
        if (currentDetail != null && currentDetail.performer.id == performerId) {
            return
        }
        val db = DatabaseHolder.db
        if (db == null) {
            _uiState.update { it.copy(isLoading = false, error = "数据库尚未就绪") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = db.performerDao().getPerformerWithMovies(performerId)
                val episodesList = db.performerDao().getEpisodesForPerformer(performerId)
                val pbc = loadPbcProfile(db, performerId)
                val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                val isFav = userRepo.isFavorite("performer", performerId.toString())
                if (detail != null) {
                    val sortedDetail = detail.copy(
                        movies = detail.movies.sortedByDescending { it.releaseYear ?: 0 }
                    )
                    _uiState.update { it.copy(
                        isLoading = false, 
                        performerDetail = sortedDetail,
                        episodes = episodesList,
                        pbcProfile = pbc,
                        isFavorite = isFav
                    ) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "未找到该演员记录") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "加载详情失败") }
            }
        }
    }
}
