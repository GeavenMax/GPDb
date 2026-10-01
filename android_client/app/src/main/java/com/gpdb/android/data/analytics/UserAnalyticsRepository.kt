package com.gpdb.android.data.analytics

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Context.analyticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gpdb_user_analytics")

data class AnalyticsData(
    val totalFocusTimeSeconds: Long = 0,
    val movieViewsCount: Int = 0,
    val uniqueMoviesCount: Int = 0,
    val episodeViewsCount: Int = 0,
    val performerViewsCount: Int = 0,
    val uniquePerformersCount: Int = 0,
    val directorViewsCount: Int = 0,
    val studioViewsCount: Int = 0,
    val searchesCount: Int = 0,
    val favoritesCount: Int = 0,
    val ratingsCount: Int = 0,
    val translationsCount: Int = 0,
    val nightOwlViewsCount: Int = 0,
    val firstLaunchTime: Long = System.currentTimeMillis(),
    val activeDaysCount: Int = 1
)

class UserAnalyticsRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val KEY_FOCUS_TIME_SECONDS = longPreferencesKey("total_focus_seconds")
        private val KEY_MOVIE_VIEWS = intPreferencesKey("movie_views")
        private val KEY_UNIQUE_MOVIES = stringPreferencesKey("unique_movies_json")
        private val KEY_EPISODE_VIEWS = intPreferencesKey("episode_views")
        private val KEY_PERFORMER_VIEWS = intPreferencesKey("performer_views")
        private val KEY_UNIQUE_PERFORMERS = stringPreferencesKey("unique_performers_json")
        private val KEY_DIRECTOR_VIEWS = intPreferencesKey("director_views")
        private val KEY_STUDIO_VIEWS = intPreferencesKey("studio_views")
        private val KEY_SEARCHES = intPreferencesKey("searches_count")
        private val KEY_FAVORITES = intPreferencesKey("favorites_count")
        private val KEY_RATINGS = intPreferencesKey("ratings_count")
        private val KEY_TRANSLATIONS = intPreferencesKey("translations_count")
        private val KEY_NIGHT_OWL_VIEWS = intPreferencesKey("night_owl_views")
        private val KEY_FIRST_LAUNCH = longPreferencesKey("first_launch_time")
        private val KEY_ACTIVE_DAYS = stringPreferencesKey("active_days_json")

        @Volatile
        private var INSTANCE: UserAnalyticsRepository? = null

        fun getInstance(context: Context): UserAnalyticsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserAnalyticsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        // Record active day on launch
        recordActiveDay()
    }

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun isNightOwlTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 5
    }

    val analyticsFlow: Flow<AnalyticsData> = context.analyticsDataStore.data.map { prefs ->
        val uniqueMoviesJson = prefs[KEY_UNIQUE_MOVIES] ?: "[]"
        val uniquePerformersJson = prefs[KEY_UNIQUE_PERFORMERS] ?: "[]"
        val activeDaysJson = prefs[KEY_ACTIVE_DAYS] ?: "[]"

        val uniqueMovies = try {
            val arr = JSONArray(uniqueMoviesJson)
            arr.length()
        } catch (_: Exception) { 0 }

        val uniquePerformers = try {
            val arr = JSONArray(uniquePerformersJson)
            arr.length()
        } catch (_: Exception) { 0 }

        val activeDays = try {
            val arr = JSONArray(activeDaysJson)
            maxOf(1, arr.length())
        } catch (_: Exception) { 1 }

        AnalyticsData(
            totalFocusTimeSeconds = prefs[KEY_FOCUS_TIME_SECONDS] ?: 0L,
            movieViewsCount = prefs[KEY_MOVIE_VIEWS] ?: 0,
            uniqueMoviesCount = uniqueMovies,
            episodeViewsCount = prefs[KEY_EPISODE_VIEWS] ?: 0,
            performerViewsCount = prefs[KEY_PERFORMER_VIEWS] ?: 0,
            uniquePerformersCount = uniquePerformers,
            directorViewsCount = prefs[KEY_DIRECTOR_VIEWS] ?: 0,
            studioViewsCount = prefs[KEY_STUDIO_VIEWS] ?: 0,
            searchesCount = prefs[KEY_SEARCHES] ?: 0,
            favoritesCount = prefs[KEY_FAVORITES] ?: 0,
            ratingsCount = prefs[KEY_RATINGS] ?: 0,
            translationsCount = prefs[KEY_TRANSLATIONS] ?: 0,
            nightOwlViewsCount = prefs[KEY_NIGHT_OWL_VIEWS] ?: 0,
            firstLaunchTime = prefs[KEY_FIRST_LAUNCH] ?: System.currentTimeMillis(),
            activeDaysCount = activeDays
        )
    }

    private fun recordActiveDay() {
        scope.launch {
            val today = getTodayString()
            context.analyticsDataStore.edit { prefs ->
                if (prefs[KEY_FIRST_LAUNCH] == null) {
                    prefs[KEY_FIRST_LAUNCH] = System.currentTimeMillis()
                }
                val raw = prefs[KEY_ACTIVE_DAYS] ?: "[]"
                try {
                    val arr = JSONArray(raw)
                    var found = false
                    for (i in 0 until arr.length()) {
                        if (arr.getString(i) == today) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        arr.put(today)
                        prefs[KEY_ACTIVE_DAYS] = arr.toString()
                    }
                } catch (_: Exception) {
                    val newArr = JSONArray().apply { put(today) }
                    prefs[KEY_ACTIVE_DAYS] = newArr.toString()
                }
            }
        }
    }

    fun addFocusSeconds(seconds: Long) {
        if (seconds <= 0) return
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                val current = prefs[KEY_FOCUS_TIME_SECONDS] ?: 0L
                prefs[KEY_FOCUS_TIME_SECONDS] = current + seconds
            }
        }
    }

    fun recordMovieView(movieId: Long) {
        val nightOwl = isNightOwlTime()
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                val views = (prefs[KEY_MOVIE_VIEWS] ?: 0) + 1
                prefs[KEY_MOVIE_VIEWS] = views

                if (nightOwl) {
                    prefs[KEY_NIGHT_OWL_VIEWS] = (prefs[KEY_NIGHT_OWL_VIEWS] ?: 0) + 1
                }

                val raw = prefs[KEY_UNIQUE_MOVIES] ?: "[]"
                try {
                    val arr = JSONArray(raw)
                    var found = false
                    for (i in 0 until arr.length()) {
                        if (arr.getLong(i) == movieId) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        arr.put(movieId)
                        prefs[KEY_UNIQUE_MOVIES] = arr.toString()
                    }
                } catch (_: Exception) {
                    val newArr = JSONArray().apply { put(movieId) }
                    prefs[KEY_UNIQUE_MOVIES] = newArr.toString()
                }
            }
        }
    }

    fun recordEpisodeView(episodeId: Long) {
        val nightOwl = isNightOwlTime()
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_EPISODE_VIEWS] = (prefs[KEY_EPISODE_VIEWS] ?: 0) + 1
                if (nightOwl) {
                    prefs[KEY_NIGHT_OWL_VIEWS] = (prefs[KEY_NIGHT_OWL_VIEWS] ?: 0) + 1
                }
            }
        }
    }

    fun recordPerformerView(performerId: Long) {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_PERFORMER_VIEWS] = (prefs[KEY_PERFORMER_VIEWS] ?: 0) + 1

                val raw = prefs[KEY_UNIQUE_PERFORMERS] ?: "[]"
                try {
                    val arr = JSONArray(raw)
                    var found = false
                    for (i in 0 until arr.length()) {
                        if (arr.getLong(i) == performerId) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        arr.put(performerId)
                        prefs[KEY_UNIQUE_PERFORMERS] = arr.toString()
                    }
                } catch (_: Exception) {
                    val newArr = JSONArray().apply { put(performerId) }
                    prefs[KEY_UNIQUE_PERFORMERS] = newArr.toString()
                }
            }
        }
    }

    fun recordDirectorView() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_DIRECTOR_VIEWS] = (prefs[KEY_DIRECTOR_VIEWS] ?: 0) + 1
            }
        }
    }

    fun recordStudioView() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_STUDIO_VIEWS] = (prefs[KEY_STUDIO_VIEWS] ?: 0) + 1
            }
        }
    }

    fun recordSearch() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_SEARCHES] = (prefs[KEY_SEARCHES] ?: 0) + 1
            }
        }
    }

    fun recordFavoriteToggle() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_FAVORITES] = (prefs[KEY_FAVORITES] ?: 0) + 1
            }
        }
    }

    fun recordRating() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_RATINGS] = (prefs[KEY_RATINGS] ?: 0) + 1
            }
        }
    }

    fun recordTranslation() {
        scope.launch {
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_TRANSLATIONS] = (prefs[KEY_TRANSLATIONS] ?: 0) + 1
            }
        }
    }

    fun resetAllAnalytics() {
        scope.launch {
            val today = getTodayString()
            context.analyticsDataStore.edit { prefs ->
                prefs[KEY_FOCUS_TIME_SECONDS] = 0L
                prefs[KEY_MOVIE_VIEWS] = 0
                prefs[KEY_UNIQUE_MOVIES] = "[]"
                prefs[KEY_EPISODE_VIEWS] = 0
                prefs[KEY_PERFORMER_VIEWS] = 0
                prefs[KEY_UNIQUE_PERFORMERS] = "[]"
                prefs[KEY_DIRECTOR_VIEWS] = 0
                prefs[KEY_STUDIO_VIEWS] = 0
                prefs[KEY_SEARCHES] = 0
                prefs[KEY_FAVORITES] = 0
                prefs[KEY_RATINGS] = 0
                prefs[KEY_TRANSLATIONS] = 0
                prefs[KEY_NIGHT_OWL_VIEWS] = 0
                prefs[KEY_FIRST_LAUNCH] = System.currentTimeMillis()
                val newArr = JSONArray().apply { put(today) }
                prefs[KEY_ACTIVE_DAYS] = newArr.toString()
            }
        }
    }

    suspend fun exportAnalyticsMap(): Map<String, Any> {
        val prefs = context.analyticsDataStore.data.first()
        return mapOf(
            "totalFocusTimeSeconds" to (prefs[KEY_FOCUS_TIME_SECONDS] ?: 0L),
            "movieViewsCount" to (prefs[KEY_MOVIE_VIEWS] ?: 0),
            "uniqueMoviesJson" to (prefs[KEY_UNIQUE_MOVIES] ?: "[]"),
            "episodeViewsCount" to (prefs[KEY_EPISODE_VIEWS] ?: 0),
            "performerViewsCount" to (prefs[KEY_PERFORMER_VIEWS] ?: 0),
            "uniquePerformersJson" to (prefs[KEY_UNIQUE_PERFORMERS] ?: "[]"),
            "directorViewsCount" to (prefs[KEY_DIRECTOR_VIEWS] ?: 0),
            "studioViewsCount" to (prefs[KEY_STUDIO_VIEWS] ?: 0),
            "searchesCount" to (prefs[KEY_SEARCHES] ?: 0),
            "favoritesCount" to (prefs[KEY_FAVORITES] ?: 0),
            "ratingsCount" to (prefs[KEY_RATINGS] ?: 0),
            "translationsCount" to (prefs[KEY_TRANSLATIONS] ?: 0),
            "nightOwlViewsCount" to (prefs[KEY_NIGHT_OWL_VIEWS] ?: 0),
            "firstLaunchTime" to (prefs[KEY_FIRST_LAUNCH] ?: System.currentTimeMillis()),
            "activeDaysJson" to (prefs[KEY_ACTIVE_DAYS] ?: "[]")
        )
    }

    suspend fun importAnalyticsMap(data: Map<String, Any>) {
        context.analyticsDataStore.edit { prefs ->
            val newFocus = (data["totalFocusTimeSeconds"] as? Number)?.toLong() ?: 0L
            prefs[KEY_FOCUS_TIME_SECONDS] = maxOf(prefs[KEY_FOCUS_TIME_SECONDS] ?: 0L, newFocus)

            val newMovieViews = (data["movieViewsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_MOVIE_VIEWS] = maxOf(prefs[KEY_MOVIE_VIEWS] ?: 0, newMovieViews)

            val newEpisodeViews = (data["episodeViewsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_EPISODE_VIEWS] = maxOf(prefs[KEY_EPISODE_VIEWS] ?: 0, newEpisodeViews)

            val newPerformerViews = (data["performerViewsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_PERFORMER_VIEWS] = maxOf(prefs[KEY_PERFORMER_VIEWS] ?: 0, newPerformerViews)

            val newDirectorViews = (data["directorViewsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_DIRECTOR_VIEWS] = maxOf(prefs[KEY_DIRECTOR_VIEWS] ?: 0, newDirectorViews)

            val newStudioViews = (data["studioViewsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_STUDIO_VIEWS] = maxOf(prefs[KEY_STUDIO_VIEWS] ?: 0, newStudioViews)

            val newSearches = (data["searchesCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_SEARCHES] = maxOf(prefs[KEY_SEARCHES] ?: 0, newSearches)

            val newRatings = (data["ratingsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_RATINGS] = maxOf(prefs[KEY_RATINGS] ?: 0, newRatings)

            val newTranslations = (data["translationsCount"] as? Number)?.toInt() ?: 0
            prefs[KEY_TRANSLATIONS] = maxOf(prefs[KEY_TRANSLATIONS] ?: 0, newTranslations)

            // Merge active days
            val existingDays = try {
                val arr = JSONArray(prefs[KEY_ACTIVE_DAYS] ?: "[]")
                (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
            } catch (_: Exception) { mutableSetOf<String>() }

            val importedDays = (data["activeDays"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: (data["activeDaysJson"] as? String)?.let { json ->
                    try {
                        val arr = JSONArray(json)
                        (0 until arr.length()).map { arr.getString(it) }
                    } catch (_: Exception) { null }
                } ?: emptyList()
            existingDays.addAll(importedDays)
            val mergedDaysArr = JSONArray()
            existingDays.forEach { mergedDaysArr.put(it) }
            prefs[KEY_ACTIVE_DAYS] = mergedDaysArr.toString()
        }
    }
}
