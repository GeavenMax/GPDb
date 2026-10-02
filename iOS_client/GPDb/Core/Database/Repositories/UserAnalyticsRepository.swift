import Foundation

/// 用户本地行为与专注探索统计仓储
public final class UserAnalyticsRepository {
    public static let shared = UserAnalyticsRepository()

    private let defaults = UserDefaults.standard
    private let kFocusTime = "gpdb_analytics_focus_time"
    private let kMovieViews = "gpdb_analytics_movie_views"
    private let kEpisodeViews = "gpdb_analytics_episode_views"
    private let kPerformerViews = "gpdb_analytics_performer_views"
    private let kDirectorViews = "gpdb_analytics_director_views"
    private let kStudioViews = "gpdb_analytics_studio_views"
    private let kSearchesCount = "gpdb_analytics_searches"
    private let kActiveDays = "gpdb_analytics_active_days"

    private init() {}

    public func recordFocusTime(seconds: Int) {
        let current = defaults.integer(forKey: kFocusTime)
        defaults.set(current + seconds, forKey: kFocusTime)
        recordTodayActive()
    }

    public func recordMovieView(id: Int64) {
        let current = defaults.integer(forKey: kMovieViews)
        defaults.set(current + 1, forKey: kMovieViews)
        recordTodayActive()
    }

    public func recordEpisodeView() {
        let current = defaults.integer(forKey: kEpisodeViews)
        defaults.set(current + 1, forKey: kEpisodeViews)
        recordTodayActive()
    }

    public func recordPerformerView() {
        let current = defaults.integer(forKey: kPerformerViews)
        defaults.set(current + 1, forKey: kPerformerViews)
        recordTodayActive()
    }

    public func recordSearchView() {
        let current = defaults.integer(forKey: kSearchesCount)
        defaults.set(current + 1, forKey: kSearchesCount)
        recordTodayActive()
    }

    private func recordTodayActive() {
        let today = DateFormatter.localizedString(from: Date(), dateStyle: .short, timeStyle: .none)
        var days = defaults.stringArray(forKey: kActiveDays) ?? []
        if !days.contains(today) {
            days.append(today)
            defaults.set(days, forKey: kActiveDays)
        }
    }

    public func getAnalyticsBackup() -> BackupAnalytics {
        return BackupAnalytics(
            totalFocusTimeSeconds: defaults.integer(forKey: kFocusTime),
            movieViewsCount: defaults.integer(forKey: kMovieViews),
            uniqueMoviesViewed: [],
            episodeViewsCount: defaults.integer(forKey: kEpisodeViews),
            performerViewsCount: defaults.integer(forKey: kPerformerViews),
            directorViewsCount: defaults.integer(forKey: kDirectorViews),
            studioViewsCount: defaults.integer(forKey: kStudioViews),
            searchesCount: defaults.integer(forKey: kSearchesCount),
            favoritesAddedCount: 0,
            ratingsCount: 0,
            activeDays: defaults.stringArray(forKey: kActiveDays) ?? []
        )
    }

    public func restoreAnalytics(_ backup: BackupAnalytics) {
        if let ft = backup.totalFocusTimeSeconds { defaults.set(ft, forKey: kFocusTime) }
        if let mv = backup.movieViewsCount { defaults.set(mv, forKey: kMovieViews) }
        if let ev = backup.episodeViewsCount { defaults.set(ev, forKey: kEpisodeViews) }
        if let pv = backup.performerViewsCount { defaults.set(pv, forKey: kPerformerViews) }
        if let sc = backup.searchesCount { defaults.set(sc, forKey: kSearchesCount) }
        if let ad = backup.activeDays { defaults.set(ad, forKey: kActiveDays) }
    }
}
