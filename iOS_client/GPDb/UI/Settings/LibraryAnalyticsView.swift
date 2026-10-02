import SwiftUI

public struct LibraryAnalyticsView: View {
    private let analyticsRepo = UserAnalyticsRepository.shared
    @State private var backup: BackupAnalytics? = nil
    @State private var showResetConfirm: Bool = false

    public init() {}

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // 1. 顶部专注时间核心指标卡
                focusHeroCard

                // 2. 核心指标矩阵网格
                metricsGrid

                // 3. 活跃探索记录
                activeDaysSection

                // 4. 本地隐私保护说明与重置选项
                privacyAndResetSection
            }
            .padding()
        }
        .navigationTitle("影库探索洞察与统计")
        .inlineNavigationTitle()
        .onAppear {
            loadData()
        }
        .alert("确定要清空统计记录吗？", isPresented: $showResetConfirm) {
            Button("取消", role: .cancel) {}
            Button("清空记录", role: .destructive) {
                resetData()
            }
        } message: {
            Text("此操作将重置本机的累计专注时长、浏览次数与活跃天数记录，已收藏的内容不会受到影响。")
        }
    }

    private func loadData() {
        backup = analyticsRepo.getAnalyticsBackup()
    }

    private func resetData() {
        let clean = BackupAnalytics(
            totalFocusTimeSeconds: 0,
            movieViewsCount: 0,
            uniqueMoviesViewed: [],
            episodeViewsCount: 0,
            performerViewsCount: 0,
            directorViewsCount: 0,
            studioViewsCount: 0,
            searchesCount: 0,
            favoritesAddedCount: 0,
            ratingsCount: 0,
            activeDays: []
        )
        analyticsRepo.restoreAnalytics(clean)
        loadData()
    }

    // MARK: - 子视图组件

    private var focusHeroCard: some View {
        let totalSeconds = backup?.totalFocusTimeSeconds ?? 0
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60

        return VStack(spacing: 12) {
            HStack {
                Label("累计专注探索时长", systemImage: "clock.badge.checkmark")
                    .font(.subheadline.bold())
                    .foregroundStyle(.tint)
                Spacer()
                Text("深度专注")
                    .font(.caption2.bold())
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.accentColor.opacity(0.12), in: Capsule())
                    .foregroundStyle(.tint)
            }

            HStack(alignment: .lastTextBaseline, spacing: 6) {
                if hours > 0 {
                    Text("\(hours)")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                    Text("小时")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                }
                Text("\(minutes)")
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                Text("分钟")
                    .font(.headline)
                    .foregroundStyle(.secondary)

                Spacer()
            }

            Text("在浏览与沉浸探索影视数据库时自动记录，反映您的离线档案阅览投入。")
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(18)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }

    private var metricsGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
            MetricTile(
                title: "长片探索",
                value: "\(backup?.movieViewsCount ?? 0) 部",
                subtitle: "电影档案阅览",
                icon: "film.stack",
                color: .blue
            )
            MetricTile(
                title: "场景分集",
                value: "\(backup?.episodeViewsCount ?? 0) 个",
                subtitle: "分集片段探索",
                icon: "play.rectangle.on.rectangle",
                color: .indigo
            )
            MetricTile(
                title: "演职人员",
                value: "\(backup?.performerViewsCount ?? 0) 位",
                subtitle: "影人档案查看",
                icon: "person.2.crop.reset",
                color: .purple
            )
            MetricTile(
                title: "检索搜索",
                value: "\(backup?.searchesCount ?? 0) 次",
                subtitle: "本地全库搜索",
                icon: "magnifyingglass",
                color: .orange
            )
        }
    }

    private var activeDaysSection: some View {
        let days = backup?.activeDays ?? []

        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("活跃天数统计", systemImage: "calendar")
                    .font(.subheadline.bold())
                Spacer()
                Text("累计 \(days.count) 天")
                    .font(.subheadline.bold())
                    .foregroundStyle(.tint)
            }

            if days.isEmpty {
                Text("今日是初次开启探索，继续保持！")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 6)
            } else {
                Text("最近活跃日期：")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                // 展示最近 5 个活跃日期
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(days.suffix(10).reversed(), id: \.self) { day in
                            Text(day)
                                .font(.caption2.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.secondary.opacity(0.12), in: Capsule())
                        }
                    }
                }
            }
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var privacyAndResetSection: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "lock.shield")
                    .font(.title3)
                    .foregroundStyle(.green)
                Text("全部统计数据存储于本地沙盒中，严格遵循离线隐私承诺，绝不向任何外部服务器上传。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.secondary.opacity(0.06), in: RoundedRectangle(cornerRadius: 14))

            Button(role: .destructive) {
                showResetConfirm = true
            } label: {
                Label("重置所有统计与专注数据", systemImage: "arrow.counterclockwise")
                    .font(.subheadline)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.red.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

private struct MetricTile: View {
    let title: String
    let value: String
    let subtitle: String
    let icon: String
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundStyle(color)
                Spacer()
                Text(title)
                    .font(.caption2.bold())
                    .foregroundStyle(.secondary)
            }

            Text(value)
                .font(.title2.bold())
                .padding(.top, 4)

            Text(subtitle)
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
}
