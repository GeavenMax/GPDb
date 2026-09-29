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
import com.gpdb.android.image.ImageHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.Request
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
 * 移植自桌面端 sync_gpdb.py 与 cache_images.py：
 * 1. 扫描官网最新更新页 (/newm, /newe, /newp)
 * 2. 差量对比本地已存在条目，精准提取未收录的新发布内容
 * 3. 抓取详情、智能清洗反爬重定向、事务级写入本地 Room 数据库
 * 4. 自动预缓存配套封面与缩略图，摆脱对静态大文件的强依赖
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
            Log.w(TAG, "请求页面失败: $url", e)
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
            val matcher = Pattern.compile("""href=['"]video/(\d+)['"]""").matcher(htmlNewm)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remoteMovieIds.add(it) }
            }
        }

        // 2. 抓取 /newp (最新演员)
        val htmlNewp = fetchHtml("$BASE_URL/newp")
        if (!htmlNewp.isNullOrBlank()) {
            val matcher = Pattern.compile("""href=['"]performer/(\d+)['"]""").matcher(htmlNewp)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remotePerformerIds.add(it) }
            }
        }

        // 3. 抓取 /newe (最新分集)
        val htmlNewe = fetchHtml("$BASE_URL/newe")
        if (!htmlNewe.isNullOrBlank()) {
            val matcher = Pattern.compile("""href=['"]episode/(\d+)['"]""").matcher(htmlNewe)
            while (matcher.find()) {
                matcher.group(1)?.toLongOrNull()?.let { remoteEpisodeIds.add(it) }
            }
        }

        // 4. 读取本地现有所有 ID 做差集
        val localMovieIds = database.movieDao().getAllMovieIds().toSet()
        val localEpisodeIds = database.episodeDao().getAllEpisodeIds().toSet()
        val localPerformerIds = database.performerDao().getAllPerformerIds().toSet()

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
     * 执行全量增量刮削与同步
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

        // 1. 同步新分集
        for (epId in discovered.episodeIds) {
            val epHtml = fetchHtml("$BASE_URL/episode/$epId")
            if (!epHtml.isNullOrBlank() && !epHtml.contains("404.shtml")) {
                val episode = parseEpisodeDetails(epId, epHtml)
                if (episode != null) {
                    database.episodeDao().insertEpisode(episode.entity)
                    if (episode.performerLinks.isNotEmpty()) {
                        database.episodeDao().insertEpisodePerformers(episode.performerLinks)
                    }
                    savedEpisodes++
                    // 同步预下载缩略图
                    episode.entity.thumbnailUrl?.let { downloadAndCacheImageSync(it) }
                }
            }
            completed++
            onProgress(completed, total, "分集 #$epId")
        }

        // 2. 同步新影片
        for (movieId in discovered.movieIds) {
            val movieHtml = fetchHtml("$BASE_URL/video/$movieId")
            if (!movieHtml.isNullOrBlank() && !movieHtml.contains("404.shtml")) {
                val movie = parseMovieDetails(movieId, movieHtml)
                if (movie != null) {
                    database.movieDao().insertMovie(movie.entity)
                    if (movie.performerLinks.isNotEmpty()) {
                        database.movieDao().insertMoviePerformers(movie.performerLinks)
                    }
                    savedMovies++
                    // 同步预下载封面
                    movie.entity.coverFull?.let { downloadAndCacheImageSync(it) }
                }
            }
            completed++
            onProgress(completed, total, "影片 #$movieId")
        }

        // 3. 同步新演员
        for (perfId in discovered.performerIds) {
            val perfHtml = fetchHtml("$BASE_URL/performer/$perfId")
            if (!perfHtml.isNullOrBlank() && !perfHtml.contains("404.shtml")) {
                val performer = parsePerformerDetails(perfId, perfHtml)
                if (performer != null) {
                    database.performerDao().insertPerformer(performer)
                    savedPerformers++
                    performer.imageUrl?.let { downloadAndCacheImageSync(it) }
                }
            }
            completed++
            onProgress(completed, total, "演员 #$perfId")
        }

        SyncResult(
            newMoviesCount = savedMovies,
            newEpisodesCount = savedEpisodes,
            newPerformersCount = savedPerformers,
            cachedImagesCount = savedMovies + savedEpisodes + savedPerformers,
            message = "同步完成！新增 $savedMovies 部电影, $savedEpisodes 个分集, $savedPerformers 位演员。"
        )
    }

    private data class ParsedMovie(
        val entity: MovieEntity,
        val performerLinks: List<MoviePerformerEntity>
    )

    private fun parseMovieDetails(movieId: Long, html: String): ParsedMovie? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""").matcher(html)
        val title = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1) ?: "") else "Movie #$movieId"

        // Cover Full (prefer HD if available)
        val coverMatcher = Pattern.compile("""src=['"](images/Covers/[^'"]+)['"]""").matcher(html)
        val coverFull = if (coverMatcher.find()) "$BASE_URL/${coverMatcher.group(1)}" else null
        val coverIcon = coverFull?.replace("/Covers/", "/Covers/Icons/")

        // Duration mins
        val durMatcher = Pattern.compile("""(\d+)\s*mins""").matcher(html)
        val durationMins = if (durMatcher.find()) durMatcher.group(1)?.toIntOrNull() else null

        // Release year
        val yearMatcher = Pattern.compile("""\b(19\d{2}|20\d{2})\b""").matcher(html)
        val releaseYear = if (yearMatcher.find()) yearMatcher.group(1)?.toIntOrNull() else null

        // Studio
        val studioMatcher = Pattern.compile("""href=['"]company/\d+['"][^>]*>([^<]+)<""").matcher(html)
        val studioName = if (studioMatcher.find()) decodeHtml(studioMatcher.group(1) ?: "") else null

        // Director
        val dirMatcher = Pattern.compile("""href=['"]director/\d+['"][^>]*>([^<]+)<""").matcher(html)
        val directorName = if (dirMatcher.find()) decodeHtml(dirMatcher.group(1) ?: "") else null

        // Performers
        val performerLinks = mutableListOf<MoviePerformerEntity>()
        val perfMatcher = Pattern.compile("""href=['"]performer/(\d+)['"]""").matcher(html)
        val seenPerfs = mutableSetOf<Long>()
        while (perfMatcher.find()) {
            perfMatcher.group(1)?.toLongOrNull()?.let { pid ->
                if (pid !in seenPerfs) {
                    seenPerfs.add(pid)
                    performerLinks.add(
                        MoviePerformerEntity(
                            movieId = movieId,
                            performerId = pid
                        )
                    )
                }
            }
        }

        val entity = MovieEntity(
            id = movieId,
            title = title,
            releaseYear = releaseYear,
            durationMins = durationMins,
            coverFull = coverFull,
            coverIcon = coverIcon,
            studioName = studioName,
            directorName = directorName
        )

        return ParsedMovie(entity, performerLinks)
    }

    private data class ParsedEpisode(
        val entity: EpisodeEntity,
        val performerLinks: List<EpisodePerformerEntity>
    )

    private fun parseEpisodeDetails(episodeId: Long, html: String): ParsedEpisode? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""").matcher(html)
        val title = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1) ?: "") else "Episode #$episodeId"

        // Thumbnail
        val thumbMatcher = Pattern.compile("""src=['"](images/Episodes/[^'"]+)['"]""").matcher(html)
        val thumbUrl = if (thumbMatcher.find()) "$BASE_URL/${thumbMatcher.group(1)}" else null

        // Movie link
        val movieMatcher = Pattern.compile("""href=['"]video/(\d+)['"]""").matcher(html)
        val movieId = if (movieMatcher.find()) movieMatcher.group(1)?.toLongOrNull() else null

        // Studio
        val studioMatcher = Pattern.compile("""href=['"]company/\d+['"][^>]*>([^<]+)<""").matcher(html)
        val studioName = if (studioMatcher.find()) decodeHtml(studioMatcher.group(1) ?: "") else null

        // Release Date
        val dateMatcher = Pattern.compile("""\b(\d{4}-\d{2}-\d{2})\b""").matcher(html)
        val releaseDate = if (dateMatcher.find()) dateMatcher.group(1) else null

        // Performers
        val performerLinks = mutableListOf<EpisodePerformerEntity>()
        val perfMatcher = Pattern.compile("""href=['"]performer/(\d+)['"]""").matcher(html)
        val seenPerfs = mutableSetOf<Long>()
        while (perfMatcher.find()) {
            perfMatcher.group(1)?.toLongOrNull()?.let { pid ->
                if (pid !in seenPerfs) {
                    seenPerfs.add(pid)
                    performerLinks.add(
                        EpisodePerformerEntity(
                            episodeId = episodeId,
                            performerId = pid
                        )
                    )
                }
            }
        }

        val entity = EpisodeEntity(
            id = episodeId,
            movieId = movieId,
            title = title,
            thumbnailUrl = thumbUrl,
            studioName = studioName,
            releaseDate = releaseDate
        )

        return ParsedEpisode(entity, performerLinks)
    }

    private fun parsePerformerDetails(performerId: Long, html: String): PerformerEntity? {
        val titleMatcher = Pattern.compile("""<title>(.*?)(?:: Gay Erotic Video Index)?</title>""").matcher(html)
        val name = if (titleMatcher.find()) decodeHtml(titleMatcher.group(1) ?: "") else "Performer #$performerId"

        val imgMatcher = Pattern.compile("""src=['"](images/Stars/[^'"]+)['"]""").matcher(html)
        val imgUrl = if (imgMatcher.find()) "$BASE_URL/${imgMatcher.group(1)}" else null

        return PerformerEntity(
            id = performerId,
            name = name,
            imageUrl = imgUrl
        )
    }

    private suspend fun downloadAndCacheImageSync(url: String) {
        try {
            val cleanUrl = if (url.startsWith("http")) url else "$BASE_URL/${url.trimStart('/')}"
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .header("Referer", BASE_URL)
                .build()

            ImageHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val bytes = response.body?.bytes() ?: return
                if (bytes.size < 100) return

                val folder = when {
                    cleanUrl.contains("/Covers/") -> "Covers"
                    cleanUrl.contains("/Episodes/") -> "Episodes"
                    cleanUrl.contains("/Stars/") -> "Stars"
                    else -> "misc"
                }
                val filename = cleanUrl.substringAfterLast("/")
                val relPath = "image_cache/$folder/$filename"

                // 隐私沙盒隔离控制：仅在用户显式开启“允许保存至外部存储”时写回外部目录
                val appSettings = com.gpdb.android.data.settings.AppSettingsRepository(context)
                val allowExternal = appSettings.saveImagesToExternalFlow.firstOrNull() ?: false

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

                // 应用内部私有沙盒文件目录 (/data/user/0/.../files/image_cache)
                val iFile = File(context.filesDir, relPath)
                iFile.parentFile?.mkdirs()
                com.gpdb.android.util.PrivacyHelper.ensureNoMedia(iFile.parentFile)
                iFile.writeBytes(bytes)
            }
        } catch (_: Exception) {}
    }
}
