import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// 瀑布流/画廊列数动态切换按钮 (支持 1 列大图、2 列标准、3 列紧凑)
public struct ColumnSwitchButton: View {
    @EnvironmentObject private var environment: AppEnvironment

    public init() {}

    public var body: some View {
        Menu {
            Button {
                updateColumns(1)
            } label: {
                Label("单列大图", systemImage: "rectangle.grid.1x2")
            }

            Button {
                updateColumns(2)
            } label: {
                Label("标准双列", systemImage: "rectangle.grid.2x2")
            }

            Button {
                updateColumns(3)
            } label: {
                Label("紧凑三列", systemImage: "rectangle.grid.3x2")
            }
        } label: {
            Image(systemName: iconName)
                .font(.system(size: 15))
        } primaryAction: {
            // 轻触即时在 2 列与 3 列之间切换
            let target = environment.layoutColumns == 2 ? 3 : 2
            updateColumns(target)
        }
    }

    private func updateColumns(_ count: Int) {
        withAnimation(.easeInOut(duration: 0.2)) {
            environment.layoutColumns = count
        }
        UserDefaults.standard.set(count, forKey: "gpdb_pref_layout_columns")
        #if canImport(UIKit)
        let impact = UIImpactFeedbackGenerator(style: .light)
        impact.impactOccurred()
        #endif
    }

    private var iconName: String {
        switch environment.layoutColumns {
        case 1: return "rectangle.grid.1x2"
        case 3: return "rectangle.grid.3x2"
        default: return "rectangle.grid.2x2"
        }
    }
}
