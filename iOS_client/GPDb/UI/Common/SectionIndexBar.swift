import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

/// iOS 风格右侧字母悬浮检索滑块，带轻触反馈
public struct SectionIndexBar: View {
    public let letters: [String]
    public let onSelect: (String) -> Void

    @State private var selectedLetter: String? = nil

    public init(letters: [String] = (65...90).map { String(UnicodeScalar($0)) } + ["#"], onSelect: @escaping (String) -> Void) {
        self.letters = letters
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(spacing: 2) {
            ForEach(letters, id: \.self) { letter in
                Text(letter)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(selectedLetter == letter ? Color.accentColor : Color.secondary)
                    .frame(width: 18, height: 14)
                    .onTapGesture {
                        selectedLetter = letter
                        #if canImport(UIKit)
                        let generator = UISelectionFeedbackGenerator()
                        generator.selectionChanged()
                        #endif
                        onSelect(letter)
                    }
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 2)
        .background(.ultraThinMaterial, in: Capsule())
    }
}
