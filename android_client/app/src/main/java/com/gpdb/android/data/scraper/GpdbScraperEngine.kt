package com.gpdb.android.data.scraper

import android.content.Context
import android.os.Build
import android.text.Html
import android.util.Log
import com.gpdb.android.data.db.GpdbDatabase
import com.gpdb.android.data.db.entities.EpisodeEntity
import com.gpdb.android.data.db.entities.EpisodePerformerEntity
import com.gpdb.android.data.db.entities.MovieEntity
import com.gpdb.android.data.db.entities.MoviePerformerEntity
import com.gpdb.android.data.db.entities.PerformerEntity
import com.gpdb.android.data.db.entities.StudioEntity
import com.gpdb.android.image.ImageHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.regex.Pattern

data class DiscoveredUpdates(
    val movieIds: List<Long>,
    val episodeIds: List<Long>,
    val performerIds: List<Long>
) {
    val totalCount: Int get() = movieIds.size + episodeIds.size + performerIds.size
}

data class SyncResult(
    val newMoviesCount: Int,
    val newEpisodesCount: Int,
    val newPerformersCount: Int,
    val cachedImagesCount: Int,
    val message: String
)

/**
 * GPDb 原生轻量级增量刮削与图库自动缓存引擎
 *
 * 移植对齐自桌面端 sync_gpdb.py 与 scraper_v2.py：
 * 1. 扫描官网最新更新页 (/newm, /newe, /newp)
 * 2. 差量对比本地已存在条目，精准提取未收录的新发布内容
 * 3. 抓取长片详情、剧集详情（含 commit 6a94a49 的 wideCols-1 / text-justify 剧情简介解析修复）
 * 4. 自动关联或新建片商 (studios: 官网网站抓取、ID 匹配) 与演员详情 (属性、体征)
 * 5. 分步短事务写入，配合 delay 让渡 SQLite 写锁，彻底杜绝 FUSE / TRUNCATE 模式下的锁竞争与 UI 卡顿
 * 6. 自动预缓存封面与缩略图至沙盒目录，支持即时离线查看
 */
class GpdbScraperEngine(
    private val context: Context,
    private val database: GpdbDatabase,
    private val physicalRootPath: String = ""
) {
    companion object {
        private const val TAG = "GpdbScraperEngine"
        private const val BASE_URL = "https://gayeroticvideoindex.com"
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    private fun fetchHtml(url: String): String? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", BASE_URL)
                .build()

            ImageHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                return response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "请求页面失败: $url (${e.message})")
            return null
        }
    }

    private fun decodeHtml(source: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(source).toString().trim()
        }
    }

    /**
     * 探测最新更新，对比本地数据库，返回差量更新列表
     */
    suspend fun checkForUpdates(): DiscoveredUpdates = withContext(Dispatchers.IO) {
        val remoteMovieIds = mutableSetOf<Long>()
        val remotePerformerIds = mutableSetOf<Long>()
        val remoteEpisodeIds = mutableSetOf<Long>()

        // 1. 抓取 /newm (最新影片)
        val htmlNewm = fetchHtml("$BASE_URL/newm")
        if (!htmlNewm.isNullOrBlank()) {
            val matcher = Pattern.compile("""(?:href=['"]?(?:/)?(?:video/|video\.php\?id=)(\d+))""", Pattern.CASE_INSENSITIVE).matcher(htmlNewm)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remoteMovieIds.add(it) }
            }
        }

        // 2. 抓取 /newp (最新演员)
        val htmlNewp = fetchHtml("$BASE_URL/newp")
        if (!htmlNewp.isNullOrBlank()) {
            val matcher = Pattern.compile("""(?:href=['"]?(?:/)?(?:performer/|performer\.php\?id=)(\d+))""", Pattern.CASE_INSENSITIVE).matcher(htmlNewp)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remotePerformerIds.add(it) }
            }
        }

        // 3. 抓取 /newe (最新分集)
        val htmlNewe = fetchHtml("$BASE_URL/newe")
        if (!htmlNewe.isNullOrBlank()) {
            val matcher = Pattern.compile("""(?:href=['"]?(?:/)?(?:episode/|episode\.php\?id=)(\d+))""", Pattern.CASE_INSENSITIVE).matcher(htmlNewe)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remoteEpisodeIds.add(it) }
            }
        }

        // 4. 读取本地现有所有 ID 做差集
        val localMovieIds = database.movieDao().getAllMovieIds().toSet()
        val localEpisodeIds = database.episodeDao().getAllEpisodeIds().toSet()
        val localPerformerIds = database.performerDao().getAllPerformerIds().toSet()

        // 5. 【前向 ID 探针 (Probe Depth = 50)】对齐桌面端 sync_gpdb.py
        // 探查本地 MAX(id) 之上的递增 ID 序列，确保即便未被 /newm 推荐首页展现的新影片也能即时自动搜刮入库
        val maxMovieId = localMovieIds.maxOrNull() ?: 0L
        if (maxMovieId > 0) {
            for (i in (maxMovieId + 1)..(maxMovieId + 50)) {
                remoteMovieIds.add(i)
            }
        }

        val maxPerformerId = localPerformerIds.maxOrNull() ?: 0L
        if (maxPerformerId > 0) {
            for (i in (maxPerformerId + 1)..(maxPerformerId + 50)) {
                remotePerformerIds.add(i)
            }
        }

        val maxEpisodeId = localEpisodeIds.maxOrNull() ?: 0L
        if (maxEpisodeId > 0) {
            for (i in (maxEpisodeId + 1)..(maxEpisodeId + 50)) {
                remoteEpisodeIds.add(i)
            }
        }

        val deltaMovies = remoteMovieIds.filter { it !in localMovieIds }
        val deltaEpisodes = remoteEpisodeIds.filter { it !in localEpisodeIds }
        val deltaPerformers = remotePerformerIds.filter { it !in localPerformerIds }

        Log.i(TAG, "更新探测完成: 发现待增量同步 影片 ${deltaMovies.size} 部, 分集 ${deltaEpisodes.size} 个, 演员 ${deltaPerformers.size} 位")

        DiscoveredUpdates(
            movieIds = deltaMovies,
            episodeIds = deltaEpisodes,
            performerIds = deltaPerformers
        )
    }

    /**
     * 执行增量刮削入库与同步
     */
    suspend fun syncUpdates(
        discovered: DiscoveredUpdates,
        onProgress: (current: Int, total: Int, currentName: String) -> Unit = { _, _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val total = discovered.totalCount
        var completed = 0
        var savedMovies = 0
        var savedEpisodes = 0
        var savedPerformers = 0
        var cachedImages = 0

        // 1. 同步新分集
        for (epId in discovered.episodeIds) {
            val epHtml = fetchHtml("$BASE_URL/episode/$epId")
            if (!epHtml.isNullOrBlank() && !epHtml.contains("404.shtml")) {
                val episode = parseEpisodeDetails(epId, epHtml)
                if (episode != null) {
                    try {
                        database.episodeDao().insertEpisode(episode.entity)
                        if (episode.performersToEnsure.isNotEmpty()) {
                            database.performerDao().insertPerformers(episode.performersToEnsure)
                        }
                        if (episode.performerLinks.isNotEmpty()) {
                            database.episodeDao().insertEpisodePerformers(episode.performerLinks)
                        }
                        ensureStudioExists(episode.entity.studioId, episode.entity.studioName)
                        savedEpisodes++

                        // 短暂让渡写锁，保障前台读取不受阻塞
                        delay(40)

                        // 异步缓存缩略图
                        episode.entity.thumbnailUrl?.let {
                            if (downloadAndCacheImageSync(it)) cachedImages++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "写入分集 #$epId 异常", e)
                    }
                }
            }
            completed++
            onProgress(completed, total, "分集 #$epId")
        }

        // 2. 同步新长片
        for (movieId in discovered.movieIds) {
            val movieHtml = fetchHtml("$BASE_URL/video/$movieId")
            if (!movieHtml.isNullOrBlank() && !movieHtml.contains("404.shtml")) {
                val movie = parseMovieDetails(movieId, movieHtml)
                if (movie != null) {
                    try {
                        database.movieDao().insertMovie(movie.entity)
                        if (movie.performersToEnsure.isNotEmpty()) {
                            database.performerDao().insertPerformers(movie.performersToEnsure)
                        }
                        if (movie.performerLinks.isNotEmpty()) {
                            database.movieDao().insertMoviePerformers(movie.performerLinks)
                        }
                        if (movie.embeddedEpisodes.isNotEmpty()) {
                            database.episodeDao().insertEpisodes(movie.embeddedEpisodes)
                        }
                        ensureStudioExists(movie.entity.studioId, movie.entity.studioName)
                        updateMovieFts(movie)
                        savedMovies++

                        // 短暂让渡写锁
                        delay(50)

                        // 异步缓存封面
                        movie.entity.coverFull?.let { if (downloadAndCacheImageSync(it)) cachedImages++ }
                        movie.entity.coverIcon?.let { downloadAndCacheImageSync(it) }
                        movie.entity.coverBack?.let { downloadAndCacheImageSync(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "写入长片 #$movieId 异常", e)
                    }
                }
            }
            completed++
            onProgress(completed, total, "长片 #$movieId")
        }

        // 3. 同步新演员
        for (perfId in discovered.performerIds) {
            val perfHtml = fetchHtml("$BASE_URL/performer/$perfId")
            if (!perfHtml.isNullOrBlank() && !perfHtml.contains("404.shtml")) {
                val performer = parsePerformerDetails(perfId, perfHtml)
                if (performer != null) {
                    try {
                        database.performerDao().insertPerformer(performer)
                        updatePerformerFts(performer)
                        savedPerformers++

                        // 让渡写锁
                        delay(40)

                        performer.imageUrl?.let { if (downloadAndCacheImageSync(it)) cachedImages++ }
                    } catch (e: Exception) {
                        Log.e(TAG, "写入演员 #$perfId 异常", e)
                    }
                }
            }
            completed++
            onProgress(completed, total, "演员 #$perfId")
        }

        if (savedMovies > 0) {
            refreshSeriesIndex()
        }

        SyncResult(
            newMoviesCount = savedMovies,
            newEpisodesCount = savedEpisodes,
            newPerformersCount = savedPerformers,
            cachedImagesCount = cachedImages,
            message = "同步完成！新增 $savedMovies 部长片, $savedEpisodes 个分集, $savedPerformers 位演员。"
        )
    }

    private fun refreshSeriesIndex() {
        try {
            val db = database.openHelper.writableDatabase
            db.execSQL("""
                INSERT OR REPLACE INTO series_collections (root_title, studio_name, movie_count, cover_url, year_start, year_end, sample_movie_ids, sample_covers)
                SELECT 
                    CASE 
                        WHEN INSTR(title, ':') > 0 THEN TRIM(SUBSTR(title, 1, INSTR(title, ':') - 1))
                        WHEN INSTR(title, ' Part ') > 0 THEN TRIM(SUBSTR(title, 1, INSTR(title, ' Part ') - 1))
                        WHEN INSTR(title, ' Vol') > 0 THEN TRIM(SUBSTR(title, 1, INSTR(title, ' Vol') - 1))
                        WHEN INSTR(title, ' #') > 0 THEN TRIM(SUBSTR(title, 1, INSTR(title, ' #') - 1))
                        ELSE TRIM(title)
                    END AS root_title,
                    studio_name,
                    COUNT(*) AS movie_count,
                    MIN(cover_full) AS cover_url,
                    MIN(release_year) AS year_start,
                    MAX(release_year) AS year_end,
                    '[' || GROUP_CONCAT(id) || ']' AS sample_movie_ids,
                    '[' || GROUP_CONCAT('"' || IFNULL(cover_full, '') || '"') || ']' AS sample_covers
                FROM movies
                WHERE studio_name IS NOT NULL AND TRIM(studio_name) != ''
                GROUP BY studio_name, root_title
                HAVING movie_count >= 2
            """.trimIndent())
        } catch (e: Exception) {
            Log.w(TAG, "刷新系列专题索引异常", e)
        }
    }

    private data class ParsedMovie(
        val entity: MovieEntity,
        val performerLinks: List<MoviePerformerEntity>,
        val performersToEnsure: List<PerformerEntity>,
        val embeddedEpisodes: List<EpisodeEntity>
    )

    private fun parseMovieDetails(movieId: Long, html: String): ParsedMovie? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
        var title = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1)?.replace(": Gay Erotic Video Index", "") ?: "") else ""
        if (title.isBlank()) {
            val h1Matcher = Pattern.compile("""<h1[^>]*>(.*?)</h1>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            title = if (h1Matcher.find()) decodeHtml(h1Matcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "") else "Movie #$movieId"
        }
        if (title.isBlank() || title.startsWith("404") || title.contains("not found", ignoreCase = true)) {
            return null
        }

        // Studio & ID
        var studioId: Long? = null
        var studioName: String? = null
        val stdMatcher = Pattern.compile("""Studio:</div>\s*<div[^>]*>(?:<a href=['"](?:/)?company/(\d+)['"][^>]*>)?(.*?)(?:</a>)?</div>""", Pattern.DOTALL).matcher(html)
        if (stdMatcher.find()) {
            studioId = stdMatcher.group(1)?.toLongOrNull()
            val rawName = stdMatcher.group(2) ?: ""
            val cleanName = decodeHtml(rawName.replace(Regex("<[^>]+>"), "")).trim()
            if (cleanName.isNotBlank()) studioName = cleanName
        }

        // Release Year
        var releaseYear: Int? = null
        val yearTableMatcher = Pattern.compile("""<th[^>]*>Released</th>.*?<tr>.*?<td[^>]*>.*?</td>\s*<td[^>]*>(.*?)</td>""", Pattern.DOTALL).matcher(html)
        if (yearTableMatcher.find()) {
            val yearDigits = Regex("""\b(19\d{2}|20\d{2})\b""").find(yearTableMatcher.group(1) ?: "")?.value
            releaseYear = yearDigits?.toIntOrNull()
        }
        if (releaseYear == null) {
            val yearMatcher = Pattern.compile("""\b(19\d{2}|20\d{2})\b""").matcher(html)
            if (yearMatcher.find()) releaseYear = yearMatcher.group(1)?.toIntOrNull()
        }

        // Duration mins
        var durationMins: Int? = null
        val durTableMatcher = Pattern.compile("""Length</th>.*?<tr>.*?<td[^>]*>.*?</td>\s*<td[^>]*>.*?</td>\s*<td[^>]*>(\d+)</td>""", Pattern.DOTALL).matcher(html)
        if (durTableMatcher.find()) {
            durationMins = durTableMatcher.group(1)?.toIntOrNull()
        } else {
            val durMatcher = Pattern.compile("""(\d+)\s*mins""", Pattern.CASE_INSENSITIVE).matcher(html)
            if (durMatcher.find()) durationMins = durMatcher.group(1)?.toIntOrNull()
        }

        // Category
        val catMatcher = Pattern.compile("""Category:</div>\s*<div[^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
        val category = if (catMatcher.find()) decodeHtml(catMatcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "").ifBlank { null } else null

        // Rating
        val ratMatcher = Pattern.compile("""Rating Out of 4:</div>\s*<div[^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
        val rating = if (ratMatcher.find()) decodeHtml(ratMatcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "").ifBlank { null } else null

        // Movie Type
        val typeMatcher = Pattern.compile("""Type:</div>\s*<div>\s*<div>(.*?)</div>""", Pattern.DOTALL).matcher(html)
        val movieType = if (typeMatcher.find()) decodeHtml(typeMatcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "").ifBlank { null } else null

        // Description
        var description: String? = null
        val descMatcher = Pattern.compile("""Description source:.*?<div class=["']text-justify[^"']*["']>(.*?)</div>\s*<!-- scenes""", Pattern.DOTALL).matcher(html)
        if (descMatcher.find()) {
            val raw = descMatcher.group(1) ?: ""
            val clean = raw.replace(Regex("<[^>]+>"), " ").replace(Regex("""\s+"""), " ")
            val decoded = decodeHtml(clean).trim()
            if (decoded.isNotBlank()) description = decoded
        }
        if (description == null) {
            val descAlt = Pattern.compile("""class=["'][^"']*text-justify[^"']*["'][^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
            if (descAlt.find()) {
                val raw = descAlt.group(1) ?: ""
                val clean = raw.replace(Regex("<[^>]+>"), " ").replace(Regex("""\s+"""), " ")
                val decoded = decodeHtml(clean).trim()
                if (decoded.isNotBlank()) description = decoded
            }
        }

        // Directors
        var directorId: Long? = null
        var directorName: String? = null
        val dirMatcher = Pattern.compile("""Director:</div>\s*<div[^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
        if (dirMatcher.find()) {
            val dirContent = dirMatcher.group(1) ?: ""
            val dAnchorMatcher = Pattern.compile("""<a\s+href=['"](?:/)?director/(\d+)['"][^>]*>(.*?)</a>""", Pattern.DOTALL).matcher(dirContent)
            val dirNames = mutableListOf<String>()
            while (dAnchorMatcher.find()) {
                val did = dAnchorMatcher.group(1)?.toLongOrNull()
                if (directorId == null && did != null) directorId = did
                val rawDName = dAnchorMatcher.group(2) ?: ""
                val cleanDName = decodeHtml(rawDName.replace(Regex("<[^>]+>"), " ")).trim()
                if (cleanDName.isNotBlank()) dirNames.add(cleanDName)
            }
            if (dirNames.isNotEmpty()) {
                directorName = dirNames.joinToString(" / ")
            } else {
                val plain = decodeHtml(dirContent.replace(Regex("<[^>]+>"), " ")).trim()
                if (plain.isNotBlank()) directorName = plain
            }
        }

        // Covers (coverContainer)
        val coverContainerMatcher = Pattern.compile("""id=['"]coverContainer['"](.*?)(?=<div[^>]*id=['"](?!coverContainer)|</section>|<!--\s*full covers)""", Pattern.DOTALL).matcher(html)
        val gallery = if (coverContainerMatcher.find()) coverContainerMatcher.group(1) ?: "" else ""
        val rawCovers = mutableListOf<String>()
        val cMatcher = Pattern.compile("""image=['"](images/Covers/[^'"]+)['"]""").matcher(gallery)
        while (cMatcher.find()) {
            val rel = cMatcher.group(1) ?: continue
            rawCovers.add(if (rel.startsWith("http")) rel else "$BASE_URL/$rel")
        }
        val coverFull = rawCovers.firstOrNull()
        val iconMatcher = Pattern.compile("""src=['"](images/Covers/Icons/[^'"]+)['"]""").matcher(gallery)
        val coverIcon = if (iconMatcher.find()) {
            val rel = iconMatcher.group(1) ?: ""
            if (rel.startsWith("http")) rel else "$BASE_URL/$rel"
        } else coverFull?.replace("/Covers/", "/Covers/Icons/")

        val coverBack = rawCovers.drop(1).firstOrNull { it.matches(Regex(""".*b\.(?:jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)) }
        val coversJson = if (rawCovers.isNotEmpty()) {
            JSONArray(rawCovers).toString()
        } else null

        // Performers
        val perfMatcher = Pattern.compile("""<a\s+href=['"](?:/)?performer/(\d+)['"][^>]*>(?:<span[^>]*>)?([^<]+)<""").matcher(html)
        val performerLinks = mutableListOf<MoviePerformerEntity>()
        val performersToEnsure = mutableListOf<PerformerEntity>()
        val seenPerfs = mutableSetOf<Long>()
        while (perfMatcher.find()) {
            val pid = perfMatcher.group(1)?.toLongOrNull() ?: continue
            val pname = decodeHtml(perfMatcher.group(2) ?: "").trim()
            if (pid !in seenPerfs) {
                seenPerfs.add(pid)
                performerLinks.add(MoviePerformerEntity(movieId = movieId, performerId = pid, performerName = pname.ifBlank { null }))
                if (pname.isNotBlank()) {
                    performersToEnsure.add(PerformerEntity(id = pid, name = pname))
                }
            }
        }

        // Embedded Scenes
        val sceneMatcher = Pattern.compile("""<a\s+href=['"](?:/)?episode/(\d+)['"]>\s*<img\s+src=['"](images/Episodes/[^'"]+)['"]""", Pattern.DOTALL).matcher(html)
        val embeddedEpisodes = mutableListOf<EpisodeEntity>()
        while (sceneMatcher.find()) {
            val epId = sceneMatcher.group(1)?.toLongOrNull() ?: continue
            val epThumbRel = sceneMatcher.group(2) ?: ""
            val epThumb = if (epThumbRel.isNotBlank()) "$BASE_URL/$epThumbRel" else null
            embeddedEpisodes.add(
                EpisodeEntity(
                    id = epId,
                    movieId = movieId,
                    title = "Episode #$epId",
                    thumbnailUrl = epThumb,
                    studioId = studioId,
                    studioName = studioName,
                    releaseDate = releaseYear?.let { "$it-01-01" }
                )
            )
        }

        val entity = MovieEntity(
            id = movieId,
            title = title,
            studioId = studioId,
            studioName = studioName,
            releaseYear = releaseYear,
            durationMins = durationMins,
            category = category,
            rating = rating,
            movieType = movieType,
            description = description,
            coverIcon = coverIcon,
            coverFull = coverFull,
            coverBack = coverBack,
            coversJson = coversJson,
            directorId = directorId,
            directorName = directorName
        )

        return ParsedMovie(entity, performerLinks, performersToEnsure, embeddedEpisodes)
    }

    private data class ParsedEpisode(
        val entity: EpisodeEntity,
        val performerLinks: List<EpisodePerformerEntity>,
        val performersToEnsure: List<PerformerEntity>
    )

    private fun parseEpisodeDetails(episodeId: Long, html: String): ParsedEpisode? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""").matcher(html)
        val title = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1) ?: "") else "Episode #$episodeId"

        // Thumbnail (prefer HD variant if found)
        val thumbMatcher = Pattern.compile("""src=['"](images/Episodes/[^'"]+)['"]""").matcher(html)
        val thumbUrl = if (thumbMatcher.find()) "$BASE_URL/${thumbMatcher.group(1)}" else null

        // Parent Movie
        val movieMatcher = Pattern.compile("""href=['"](?:/)?video/(\d+)['"]""").matcher(html)
        val movieId = if (movieMatcher.find()) movieMatcher.group(1)?.toLongOrNull() else null

        // Studio & ID
        val studioMatcher = Pattern.compile("""href=['"](?:/)?company/(\d+)['"][^>]*>([^<]+)<""").matcher(html)
        var studioId: Long? = null
        var studioName: String? = null
        if (studioMatcher.find()) {
            studioId = studioMatcher.group(1)?.toLongOrNull()
            studioName = decodeHtml(studioMatcher.group(2) ?: "").trim().ifBlank { null }
        }

        // Release Date
        val dateMatcher = Pattern.compile("""\b(\d{4}-\d{2}-\d{2})\b""").matcher(html)
        val releaseDate = if (dateMatcher.find()) dateMatcher.group(1) else null

        // Description: 关键修复（对齐 macOS commit 6a94a49: 适配 wideCols-1 与 text-justify）
        val wideColsMatcher = Pattern.compile("""class=['"][^'"]*wideCols-1[^'"]*['"][^>]*>(.*?)(?:</div>\s*</div>|<!--)""", Pattern.DOTALL).matcher(html)
        var descHtml = if (wideColsMatcher.find()) wideColsMatcher.group(1) else null
        if (descHtml.isNullOrBlank()) {
            val textJustifyMatcher = Pattern.compile("""class=['"][^'"]*text-justify[^'"]*['"][^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
            if (textJustifyMatcher.find()) descHtml = textJustifyMatcher.group(1)
        }
        var episodeDesc: String? = null
        if (!descHtml.isNullOrBlank()) {
            val cleaned = descHtml.replace(Regex("""<(script|style)[^>]*>.*?</\1>""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
            val decoded = decodeHtml(cleaned).trim()
            if (decoded.isNotBlank()) episodeDesc = decoded
        }

        // Performers
        val performerLinks = mutableListOf<EpisodePerformerEntity>()
        val performersToEnsure = mutableListOf<PerformerEntity>()
        val perfMatcher = Pattern.compile("""href=['"](?:/)?performer/(\d+)['"][^>]*>(?:<span[^>]*>)?([^<]+)<""").matcher(html)
        val seenPerfs = mutableSetOf<Long>()
        while (perfMatcher.find()) {
            val pid = perfMatcher.group(1)?.toLongOrNull() ?: continue
            val pname = decodeHtml(perfMatcher.group(2) ?: "").trim()
            if (pid !in seenPerfs) {
                seenPerfs.add(pid)
                performerLinks.add(EpisodePerformerEntity(episodeId = episodeId, performerId = pid, performerName = pname.ifBlank { null }))
                if (pname.isNotBlank()) {
                    performersToEnsure.add(PerformerEntity(id = pid, name = pname))
                }
            }
        }

        val entity = EpisodeEntity(
            id = episodeId,
            movieId = movieId,
            title = title,
            thumbnailUrl = thumbUrl,
            description = episodeDesc,
            studioId = studioId,
            studioName = studioName,
            releaseDate = releaseDate
        )

        return ParsedEpisode(entity, performerLinks, performersToEnsure)
    }

    private fun parsePerformerDetails(performerId: Long, html: String): PerformerEntity? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""").matcher(html)
        var name = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1)?.replace(": Gay Erotic Video Index", "") ?: "") else ""
        if (name.isBlank()) {
            val h1Matcher = Pattern.compile("""<h1[^>]*>(.*?)</h1>""", Pattern.DOTALL).matcher(html)
            name = if (h1Matcher.find()) decodeHtml(h1Matcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "") else "Performer #$performerId"
        }
        if (name.isBlank() || name.startsWith("404") || name.contains("not found", ignoreCase = true)) {
            return null
        }

        val imgMatcher = Pattern.compile("""src=['"](images/Stars/[^'"]+)['"]""").matcher(html)
        val imgUrl = if (imgMatcher.find()) "$BASE_URL/${imgMatcher.group(1)}" else null

        fun extractStat(label: String): String? {
            val pattern = Pattern.compile("""<div class=["']text-yellow-100 whitespace-nowrap["']>$label:</div>\s*<div class=['"]whitespace-nowrap['"]>(.*?)</div>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val clean = decodeHtml(matcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "").trim()
                if (clean.isNotBlank()) return clean
            }
            return null
        }

        val hair = extractStat("Hair")
        val eyes = extractStat("Eyes")
        val height = extractStat("Height")
        val weight = extractStat("Weight")
        val build = extractStat("Build")
        val skin = extractStat("Skin")
        val bodyHair = extractStat("Body Hair")
        val facialHair = extractStat("Facial Hair")
        val dickSize = extractStat("Dick")
        val foreskin = extractStat("Foreskin")

        val notesMatcher = Pattern.compile("""Notes:</div>\s*<div[^>]*>(.*?)</div>""", Pattern.DOTALL).matcher(html)
        val notes = if (notesMatcher.find()) {
            val clean = decodeHtml(notesMatcher.group(1)?.replace(Regex("<[^>]+>"), "") ?: "").trim()
            clean.ifBlank { null }
        } else null

        return PerformerEntity(
            id = performerId,
            name = name,
            imageUrl = imgUrl,
            hair = hair,
            eyes = eyes,
            height = height,
            weight = weight,
            build = build,
            skin = skin,
            bodyHair = bodyHair,
            facialHair = facialHair,
            dickSize = dickSize,
            foreskin = foreskin,
            notes = notes
        )
    }

    /**
     * 自动补全或新建片商 (studios) 记录，支持抓取官网链接
     */
    private suspend fun ensureStudioExists(studioId: Long?, studioName: String?) {
        if (studioName.isNullOrBlank()) return
        try {
            val existing = database.studioDao().getStudioByName(studioName)
            if (existing == null) {
                var websiteUrl: String? = null
                if (studioId != null && studioId > 0) {
                    websiteUrl = fetchStudioWebsite(studioId)
                }
                database.studioDao().insertOrUpdate(
                    StudioEntity(
                        name = studioName,
                        siteId = studioId,
                        websiteUrl = websiteUrl
                    )
                )
            } else if (existing.siteId == null && studioId != null) {
                val websiteUrl = if (existing.websiteUrl.isNullOrBlank()) fetchStudioWebsite(studioId) else existing.websiteUrl
                database.studioDao().insertOrUpdate(
                    existing.copy(siteId = studioId, websiteUrl = websiteUrl)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "更新片商数据异常: $studioName", e)
        }
    }

    private fun fetchStudioWebsite(companyId: Long): String? {
        val html = fetchHtml("$BASE_URL/company/$companyId") ?: return null
        val secMatcher = Pattern.compile("""<!--\s*name\s*-->.*?<!--\s*stats""", Pattern.DOTALL).matcher(html)
        if (secMatcher.find()) {
            val sec = secMatcher.group(0) ?: ""
            val linkMatcher = Pattern.compile("""<a\s+[^>]*href=['"](https?://[^'"]+)['"]""", Pattern.CASE_INSENSITIVE).matcher(sec)
            while (linkMatcher.find()) {
                val url = linkMatcher.group(1)?.trim() ?: continue
                if (!url.contains("gayeroticvideoindex.com", ignoreCase = true)) {
                    return url
                }
            }
        }
        return null
    }

    private fun updateMovieFts(movie: ParsedMovie) {
        try {
            val perfNames = movie.performerLinks.mapNotNull { it.performerName }.joinToString(" ")
            database.openHelper.writableDatabase.execSQL("DELETE FROM movies_fts WHERE id = ?", arrayOf(movie.entity.id))
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO movies_fts (id, title, studio_name, category, description, performers) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(movie.entity.id, movie.entity.title, movie.entity.studioName ?: "", movie.entity.category ?: "", movie.entity.description ?: "", perfNames)
            )
        } catch (_: Exception) {}
    }

    private fun updatePerformerFts(performer: PerformerEntity) {
        try {
            database.openHelper.writableDatabase.execSQL("DELETE FROM performers_fts WHERE id = ?", arrayOf(performer.id))
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO performers_fts (id, name, tattoos, notes) VALUES (?, ?, ?, ?)",
                arrayOf(performer.id, performer.name, "", performer.notes ?: "")
            )
        } catch (_: Exception) {}
    }

    private suspend fun downloadAndCacheImageSync(url: String): Boolean {
        try {
            val cleanUrl = if (url.startsWith("http")) url else "$BASE_URL/${url.trimStart('/')}"
            val folder = when {
                cleanUrl.contains("/Covers/") -> "Covers"
                cleanUrl.contains("/Episodes/") -> "Episodes"
                cleanUrl.contains("/Stars/") -> "Stars"
                else -> "misc"
            }
            val filename = cleanUrl.substringAfterLast("/")
            val relPath = "image_cache/$folder/$filename"

            val iFile = File(context.filesDir, relPath)
            if (iFile.exists() && iFile.length() > 100) {
                return true // 已经存在于私有沙盒中，跳过重复下载
            }

            val appSettings = com.gpdb.android.data.settings.AppSettingsRepository(context)
            val allowExternal = appSettings.saveImagesToExternalFlow.firstOrNull() ?: false
            if (allowExternal && physicalRootPath.isNotBlank()) {
                val pFile = File(physicalRootPath, relPath)
                if (pFile.exists() && pFile.length() > 100) {
                    return true // 已经存在于外部存储目录中，跳过重复下载
                }
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Referer", BASE_URL)
                .build()

            ImageHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val bytes = response.body?.bytes() ?: return false
                if (bytes.size < 100) return false

                // 1. 外部存储写回（若用户在设置中开启允许写回外部）
                if (allowExternal && physicalRootPath.isNotBlank()) {
                    try {
                        val pFile = File(physicalRootPath, relPath)
                        pFile.parentFile?.mkdirs()
                        if (pFile.parentFile?.canWrite() == true) {
                            pFile.writeBytes(bytes)
                            com.gpdb.android.util.PrivacyHelper.ensureNoMedia(File(physicalRootPath))
                            com.gpdb.android.util.PrivacyHelper.ensureNoMedia(pFile.parentFile)
                        }
                    } catch (_: Exception) {}
                }

                // 2. 应用内部私有沙盒文件目录 (/data/user/0/.../files/image_cache)
                iFile.parentFile?.mkdirs()
                com.gpdb.android.util.PrivacyHelper.ensureNoMedia(iFile.parentFile)
                iFile.writeBytes(bytes)
                return true
            }
        } catch (_: Exception) {
            return false
        }
    }
}
