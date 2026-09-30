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
    val sjUrl: String? = null,
    val allAliases: List<String> = emptyList(),
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
                "SELECT performer_id, pbc_url, birth_name, aliases, career_start, career_status, bio, birth_date, birth_place, ethnicity, astrology, height, weight, penis_size, foreskin, tattoos, piercings, roles_json, social_links_json, external_ids_json, performance_tags, image_url, scraped_at FROM performer_pbc_profiles WHERE performer_id = ? LIMIT 1",
                arrayOf(performerId.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    PerformerPbcProfile(
                        performerId = it.getLong(0),
                        pbcUrl = it.getString(1),
                        pbcId = null,
                        birthName = if (it.isNull(2)) null else it.getString(2),
                        aliases = if (it.isNull(3)) null else it.getString(3),
                        careerStart = if (it.isNull(4)) null else it.getString(4)?.take(4)?.toIntOrNull(),
                        careerEnd = null,
                        careerStatus = if (it.isNull(5)) null else it.getString(5),
                        bio = if (it.isNull(6)) null else it.getString(6),
                        birthDate = if (it.isNull(7)) null else it.getString(7),
                        birthPlace = if (it.isNull(8)) null else it.getString(8),
                        ethnicity = if (it.isNull(9)) null else it.getString(9),
                        astrology = if (it.isNull(10)) null else it.getString(10),
                        height = if (it.isNull(11)) null else it.getString(11),
                        weight = if (it.isNull(12)) null else it.getString(12),
                        dickSize = if (it.isNull(13)) null else it.getString(13),
                        foreskin = if (it.isNull(14)) null else it.getString(14),
                        tattoos = if (it.isNull(15)) null else it.getString(15),
                        piercings = if (it.isNull(16)) null else it.getString(16),
                        roles = if (it.isNull(17)) null else it.getString(17),
                        socialLinks = if (it.isNull(18)) null else it.getString(18),
                        externalIds = if (it.isNull(19)) null else it.getString(19),
                        tags = if (it.isNull(20)) null else it.getString(20),
                        imageUrl = if (it.isNull(21)) null else it.getString(21),
                        scrapedAt = if (it.isNull(22)) null else it.getString(22)
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadSjInfo(db: GpdbDatabase, performerId: Long): Pair<String?, List<String>> {
        return try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT sj_url, aliases FROM performer_sj_profiles WHERE performer_id = ? LIMIT 1",
                arrayOf(performerId.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val url = if (it.isNull(0)) null else it.getString(0)
                    val rawAliases = if (it.isNull(1)) null else it.getString(1)
                    val aliases = rawAliases?.split(",")?.map { a -> a.trim() }?.filter { a -> a.isNotBlank() } ?: emptyList()
                    Pair(url, aliases)
                } else Pair(null, emptyList())
            }
        } catch (_: Exception) {
            Pair(null, emptyList())
        }
    }

    private fun loadCreditAliases(db: GpdbDatabase, performerId: Long, performerName: String): List<String> {
        return try {
            val cursor = db.openHelper.readableDatabase.query(
                """
                SELECT DISTINCT performer_name FROM (
                    SELECT performer_name FROM movie_performers WHERE performer_id = ? AND performer_name != '' AND performer_name IS NOT NULL
                    UNION
                    SELECT performer_name FROM episode_performers WHERE performer_id = ? AND performer_name != '' AND performer_name IS NOT NULL
                ) WHERE performer_name != ? ORDER BY performer_name COLLATE NOCASE
                """.trimIndent(),
                arrayOf(performerId.toString(), performerId.toString(), performerName)
            )
            val list = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)?.trim()
                    if (!name.isNullOrBlank() && !name.equals(performerName, ignoreCase = true)) {
                        list.add(name)
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
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
                val (sjUrl, sjAliases) = loadSjInfo(db, performerId)
                val userRepo = com.gpdb.android.data.repository.UserRepository(db, db.userActionDao())
                val isFav = userRepo.isFavorite("performer", performerId.toString())

                if (detail != null) {
                    val sortedDetail = detail.copy(
                        movies = detail.movies.sortedByDescending { it.releaseYear ?: 0 }
                    )
                    val performerName = detail.performer.name
                    val creditAliases = loadCreditAliases(db, performerId, performerName)
                    val pbcAliases = pbc?.aliases?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

                    // Combine and deduplicate aliases
                    val seen = mutableSetOf<String>()
                    seen.add(performerName.trim().lowercase())
                    val mergedAliases = mutableListOf<String>()

                    for (alias in creditAliases + pbcAliases + sjAliases) {
                        val trimmed = alias.trim()
                        val lower = trimmed.lowercase()
                        if (trimmed.isNotEmpty() && !seen.contains(lower)) {
                            seen.add(lower)
                            mergedAliases.add(trimmed)
                        }
                    }

                    _uiState.update { it.copy(
                        isLoading = false, 
                        performerDetail = sortedDetail,
                        episodes = episodesList,
                        pbcProfile = pbc,
                        sjUrl = sjUrl,
                        allAliases = mergedAliases,
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
