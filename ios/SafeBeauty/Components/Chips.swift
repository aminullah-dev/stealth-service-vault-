import SwiftUI
import SafeBeautyCore

/// A wrapping row of chips.
///
/// SwiftUI has no built-in flow layout, and an HStack in a ScrollView pushes a
/// long Dari service name off the edge instead of wrapping it. Written with the
/// Layout protocol so it mirrors correctly: in a right-to-left context the rows
/// fill from the right, which a hand-rolled version using x offsets would not.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > width, x > 0 {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: width, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.width, x > 0 {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            // Placed by leading edge, so the Layout protocol mirrors it for us.
            view.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y),
                       anchor: .topLeading, proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

private struct ChipBackground: ViewModifier {
    let isSelected: Bool
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 13)
            .padding(.vertical, 9)
            .background(isSelected ? AnyShapeStyle(Brand.gradient)
                                   : AnyShapeStyle(Color.white))
            .foregroundStyle(isSelected ? Color.white : Brand.ink)
            .clipShape(Capsule())
            .overlay(Capsule().strokeBorder(
                isSelected ? .clear : Brand.petal.opacity(0.6), lineWidth: 1))
    }
}

struct ServiceChip: View {
    let name: String
    let price: Int?
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Text(name).font(Brand.font(14, .medium))
                if let price, price > 0 {
                    Text(verbatim: "\(price)")
                        .font(Brand.font(13))
                        .environment(\.layoutDirection, .leftToRight)
                        .opacity(0.8)
                }
            }
            .modifier(ChipBackground(isSelected: isSelected))
        }
        .buttonStyle(.plain)
    }
}

struct DayChip: View {
    let day: Date
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text(Self.weekdayName(day)).font(Brand.font(12))
                Text(Self.dayNumber(day))
                    .font(Brand.font(16, .bold))
                    .environment(\.layoutDirection, .leftToRight)
            }
            .frame(width: 52)
            .modifier(ChipBackground(isSelected: isSelected))
        }
        .buttonStyle(.plain)
    }

    // Formatted in the chosen language and in Kabul, so "today" means the day
    // it is there rather than wherever the phone happens to be.
    private static func formatter(_ format: String) -> DateFormatter {
        let f = DateFormatter()
        f.timeZone = DayGrid.kabul
        f.locale = AppLanguage.current.locale
        f.setLocalizedDateFormatFromTemplate(format)
        return f
    }
    static func weekdayName(_ d: Date) -> String { formatter("EEE").string(from: d) }
    static func dayNumber(_ d: Date) -> String { formatter("d").string(from: d) }
}

struct TimeChip: View {
    let millis: Int64
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(Self.label(millis))
                .font(Brand.font(14, .medium))
                .environment(\.layoutDirection, .leftToRight)
                .modifier(ChipBackground(isSelected: isSelected))
        }
        .buttonStyle(.plain)
    }

    static func label(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.timeZone = DayGrid.kabul
        f.locale = AppLanguage.current.locale
        f.setLocalizedDateFormatFromTemplate("HH:mm")
        return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }
}
