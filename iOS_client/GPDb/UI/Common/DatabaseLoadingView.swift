import SwiftUI

/// GPDb 殿堂级离线数据库装载与启动动画视图
public struct DatabaseLoadingView: View {
    @EnvironmentObject private var environment: AppEnvironment

    // 经典 24K 金与钛金渐变
    private let goldGradient = LinearGradient(
        colors: [Color(hex: "#fef08a"), Color(hex: "#fbbf24"), Color(hex: "#d97706"), Color(hex: "#78350f")],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
    private let titaniumGradient = LinearGradient(
        colors: [Color(hex: "#64748b"), Color(hex: "#cbd5e1"), Color(hex: "#f8fafc"), Color(hex: "#94a3b8")],
        startPoint: .bottomLeading,
        endPoint: .topTrailing
    )

    public init() {}

    public var body: some View {
        ZStack {
            // 1. 深邃午夜宇宙星空背景
            LinearGradient(
                colors: [
                    Color(hex: "#090d1a"),
                    Color(hex: "#060913"),
                    Color(hex: "#020408")
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // 2. 核心星云氛围微光
            RadialGradient(
                colors: [
                    Color(hex: "#1e3a8a").opacity(0.35),
                    Color(hex: "#0e1726").opacity(0.15),
                    Color.clear
                ],
                center: .center,
                startRadius: 20,
                endRadius: 260
            )
            .ignoresSafeArea()

            VStack(spacing: 32) {
                Spacer()

                // 3. 动态呼吸双火星微光徽标 (独立高刷新 TimelineView 驱动，永不因状态刷新卡顿)
                LuminousTwinMarsBadge(goldGradient: goldGradient, titaniumGradient: titaniumGradient)

                // 4. 品牌标题与副标题
                VStack(spacing: 8) {
                    Text("GPDb")
                        .font(.system(size: 36, weight: .black, design: .rounded))
                        .foregroundStyle(
                            LinearGradient(
                                colors: [Color(hex: "#fef9c3"), Color(hex: "#fbbf24"), Color(hex: "#f59e0b")],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .shadow(color: Color.amber.opacity(0.3), radius: 10, y: 2)

                    Text("离线影视档案库")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .kerning(4)
                }

                Spacer()

                // 5. 交互装载状态与极细流光药丸胶囊进度条
                VStack(spacing: 16) {
                    // 独立流光脉冲细进度条 (采用 DisplayLink 级 TimelineView，彻底消除父视图重刷打断动画的 Bug)
                    ShimmerPillCapsule()

                    // 动态状态文本
                    VStack(spacing: 6) {
                        Text(environment.mountingProgressText)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(Color.white.opacity(0.92))
                            .animation(.easeInOut(duration: 0.25), value: environment.mountingProgressText)

                        Text(environment.mountingSubText)
                            .font(.system(size: 10))
                            .foregroundStyle(Color.white.opacity(0.65))
                            .kerning(1)
                            .animation(.easeInOut(duration: 0.25), value: environment.mountingSubText)
                    }
                }
                .padding(.bottom, 48)
            }
            .padding(.horizontal, 24)
        }
    }
}

/// 独立流光脉冲细进度条胶囊 (TimelineView 驱动，无 @State 动画事务依赖，杜绝停滞卡死)
private struct ShimmerPillCapsule: View {
    var body: some View {
        TimelineView(.animation) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let cycle: Double = 1.4
            let progress = (sin(time * (2.0 * .pi / cycle)) + 1.0) / 2.0

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 200, height: 4)

                Capsule()
                    .fill(
                        LinearGradient(
                            colors: [
                                Color.clear,
                                Color(hex: "#38bdf8").opacity(0.8),
                                Color(hex: "#fbbf24"),
                                Color.clear
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .frame(width: 80, height: 4)
                    .offset(x: CGFloat(progress) * (200 - 80))
            }
            .clipShape(Capsule())
        }
    }
}

/// 独立动态双火星微光徽标
private struct LuminousTwinMarsBadge: View {
    let goldGradient: LinearGradient
    let titaniumGradient: LinearGradient

    var body: some View {
        TimelineView(.animation) { timeline in
            let time = timeline.date.timeIntervalSinceReferenceDate
            let rotate = (time.truncatingRemainder(dividingBy: 4.0) / 4.0) * 360.0
            let pulseProgress = (sin(time * (2.0 * .pi / 1.8)) + 1.0) / 2.0
            let pulseScale = 0.96 + 0.08 * pulseProgress

            ZStack {
                // 外围静止微弱刻度环
                Circle()
                    .stroke(
                        LinearGradient(
                            colors: [Color.white.opacity(0.08), Color.white.opacity(0.02)],
                            startPoint: .top,
                            endPoint: .bottom
                        ),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 6])
                    )
                    .frame(width: 170, height: 170)

                // 旋转轨道微光粒子线
                Circle()
                    .trim(from: 0.1, to: 0.35)
                    .stroke(
                        LinearGradient(
                            colors: [Color.amber.opacity(0.6), Color.cyan.opacity(0.1)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        style: StrokeStyle(lineWidth: 2, lineCap: .round)
                    )
                    .frame(width: 156, height: 156)
                    .rotationEffect(.degrees(rotate))

                // 核心双火星交错符号
                TwinMarsLogo(goldGradient: goldGradient, titaniumGradient: titaniumGradient)
                    .frame(width: 100, height: 100)
                    .scaleEffect(pulseScale)
                    .shadow(color: Color(hex: "#38bdf8").opacity(0.3), radius: 16, x: 0, y: 0)
                    .shadow(color: Color.amber.opacity(0.3), radius: 24, x: 0, y: 8)
            }
            .frame(width: 180, height: 180)
        }
    }
}

/// 纯 SwiftUI 矢量绘制的 Scheme A 双火星交错几何徽章
private struct TwinMarsLogo: View {
    let goldGradient: LinearGradient
    let titaniumGradient: LinearGradient

    var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width
            let h = proxy.size.height
            let scale = min(w, h) / 100.0

            ZStack {
                // 1. 钛金符号 (左下方)
                MarsSymbolShape()
                    .stroke(titaniumGradient, style: StrokeStyle(lineWidth: 5.5 * scale, lineCap: .round, lineJoin: .round))
                    .frame(width: 52 * scale, height: 52 * scale)
                    .position(x: 38 * scale, y: 58 * scale)

                // 2. 24K 金符号 (右上方)
                MarsSymbolShape()
                    .stroke(goldGradient, style: StrokeStyle(lineWidth: 5.5 * scale, lineCap: .round, lineJoin: .round))
                    .frame(width: 52 * scale, height: 52 * scale)
                    .position(x: 62 * scale, y: 38 * scale)

                // 3. 中心交织编织片 (形成立体缠绕结效果)
                Path { p in
                    p.addArc(
                        center: CGPoint(x: 38 * scale, y: 58 * scale),
                        radius: 17 * scale,
                        startAngle: .degrees(-70),
                        endAngle: .degrees(-20),
                        clockwise: false
                    )
                }
                .stroke(titaniumGradient, style: StrokeStyle(lineWidth: 5.5 * scale, lineCap: .round))

                // 4. 中心光学聚焦点
                Circle()
                    .fill(Color.white)
                    .frame(width: 4 * scale, height: 4 * scale)
                    .position(x: 50 * scale, y: 48 * scale)
                    .shadow(color: Color(hex: "#38bdf8"), radius: 6 * scale)
            }
        }
    }
}

/// 单个火星符号（圆环 + 45° 东北向箭头）
private struct MarsSymbolShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX - rect.width * 0.12, y: rect.midY + rect.height * 0.12)
        let radius = rect.width * 0.32

        // 圆环
        path.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))

        // 箭头主轴
        let startX = center.x + radius * 0.707
        let startY = center.y - radius * 0.707
        let endX = rect.maxX - 2
        let endY = rect.minY + 2
        path.move(to: CGPoint(x: startX, y: startY))
        path.addLine(to: CGPoint(x: endX, y: endY))

        // 箭头两侧翼
        let wingLen = radius * 0.6
        path.move(to: CGPoint(x: endX - wingLen, y: endY))
        path.addLine(to: CGPoint(x: endX, y: endY))
        path.addLine(to: CGPoint(x: endX, y: endY + wingLen))

        return path
    }
}
