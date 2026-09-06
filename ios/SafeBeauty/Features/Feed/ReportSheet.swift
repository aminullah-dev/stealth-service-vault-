import SwiftUI

/// Reporting a post, a story, a comment or a review.
///
/// One sheet for all four, because the decision is the same one every time:
/// what is wrong with it, optionally why, and whether she also wants to stop
/// seeing this person entirely.
///
/// Blocking is offered here rather than only in a separate menu because this is
/// the moment she wants it — she is looking at the thing that upset her. Apple
/// asks for both mechanisms; putting them one tap apart is what makes the
/// second one get used.
struct ReportSheet: View {
    let target: ReportTarget
    let targetId: String
    /// The author's app uid, so Block has somebody to block. Empty when the
    /// content carries no author — then only reporting is offered.
    let authorId: String
    let authorKind: String
    let moderation: Moderation

    @Environment(\.dismiss) private var dismiss

    @State private var reason: ReportReason = .harassment
    @State private var note = ""
    @State private var alsoBlock = false
    @State private var working = false
    @State private var error: String?
    @State private var done = false

    private var canBlock: Bool { !authorId.isEmpty }

    var body: some View {
        NavigationStack {
            Group {
                if done { sent } else { form }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.reportTitle.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                        .disabled(working)
                }
            }
        }
    }

    private var sent: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 40)).foregroundStyle(Brand.success)
            Text(L.reportSentTitle.t)
                .font(Brand.font(17, .bold)).foregroundStyle(Brand.ink)
            // The 24-hour commitment, said to her rather than only promised to
            // Apple. It is also the honest answer to "what happens now".
            Text(L.reportSentBody.t)
                .font(Brand.font(14)).foregroundStyle(Brand.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(L.reportWhy.t)
                    .font(Brand.font(12, .semibold)).foregroundStyle(Brand.accent)
                VStack(spacing: 0) {
                    ForEach(ReportReason.allCases) { r in
                        Button { reason = r } label: {
                            HStack {
                                Text(r.label)
                                    .font(Brand.font(15)).foregroundStyle(Brand.ink)
                                Spacer()
                                if reason == r {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(Brand.accent)
                                }
                            }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if r != ReportReason.allCases.last {
                            Divider().background(Brand.petal.opacity(0.5))
                        }
                    }
                }
                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))

                TextField(L.reportNotePlaceholder.t, text: $note, axis: .vertical)
                    .font(Brand.font(15)).foregroundStyle(Brand.ink)
                    .lineLimit(2...5)
                    .padding(13)
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))

                if canBlock {
                    Toggle(isOn: $alsoBlock) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L.reportAlsoBlock.t)
                                .font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                            Text(L.reportAlsoBlockHint.t)
                                .font(Brand.font(12)).foregroundStyle(Brand.textMuted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .tint(Brand.accent)
                    .padding(13)
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
                }

                ErrorBanner(message: error)

                BrandButton(title: .reportSubmit, isLoading: working, isEnabled: !working) {
                    Task { await submit() }
                }
                Spacer(minLength: 20)
            }
            .padding(.horizontal, 24).padding(.top, 16)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func submit() async {
        working = true; defer { working = false }
        error = nil
        do {
            try await moderation.report(target, id: targetId, reason: reason,
                                        note: note.trimmingCharacters(in: .whitespaces))
            // Blocking after the report, and not failing the whole thing if it
            // fails: the report is the part that reaches a human.
            if alsoBlock, canBlock {
                try? await moderation.block(authorId, kind: authorKind)
            }
            done = true
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
