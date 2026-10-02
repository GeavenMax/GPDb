import SwiftUI

/// 电影多维筛选与排序底栏抽屉
public struct FilterSheetView: View {
    @Environment(\.dismiss) private var dismiss

    @Binding public var selectedStudio: String?
    @Binding public var selectedYear: Int?
    @Binding public var selectedCategoryTag: String?
    @Binding public var selectedSortBy: String
    public let onApply: () -> Void

    public init(
        selectedStudio: Binding<String?>,
        selectedYear: Binding<Int?>,
        selectedCategoryTag: Binding<String?>,
        selectedSortBy: Binding<String>,
        onApply: @escaping () -> Void
    ) {
        self._selectedStudio = selectedStudio
        self._selectedYear = selectedYear
        self._selectedCategoryTag = selectedCategoryTag
        self._selectedSortBy = selectedSortBy
        self.onApply = onApply
    }

    public var body: some View {
        NavigationStack {
            Form {
                Section("排序方式") {
                    Picker("排序规则", selection: $selectedSortBy) {
                        Text("最新添加 (默认)").tag("id_desc")
                        Text("发行年份 (最新优先)").tag("year_desc")
                        Text("发行年份 (早期优先)").tag("year_asc")
                        Text("片名 A-Z").tag("title_asc")
                    }
                }

                Section("发行年份") {
                    Picker("指定年份", selection: Binding(
                        get: { selectedYear ?? 0 },
                        set: { selectedYear = $0 > 0 ? $0 : nil }
                    )) {
                        Text("不限年份").tag(0)
                        ForEach((1970...2026).reversed(), id: \.self) { yr in
                            Text("\(yr) 年").tag(yr)
                        }
                    }
                }

                Section {
                    Button(role: .destructive) {
                        selectedStudio = nil
                        selectedYear = nil
                        selectedCategoryTag = nil
                        selectedSortBy = "id_desc"
                        onApply()
                        dismiss()
                    } label: {
                        Text("重置全部筛选条件")
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .navigationTitle("筛选与排序")
            .inlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        onApply()
                        dismiss()
                    }
                    .bold()
                }
            }
        }
    }
}
