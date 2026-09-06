import SwiftUI
import SafeBeautyCore

/// A salon answering a review in public.
///
/// The rules have always allowed it — `affectedKeys().hasOnly(['providerReply',
/// 'repliedAt'])` — and the web console does it. On an iPhone a salon owner
/// could read what a customer had written about her and say nothing back.
struct ReplyToReviewSheet: View {
    let review: Review
    let repo: ProviderRepository

    @Environment(\.dismiss) private var dismiss

    @State private var text = ""
    @State private var working = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // What she is answering, so the reply is written to the
                    // review rather than into a blank box.
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 4) {
                            ForEach(0..<5) { i in
                                Image(systemName: i < review.rating ? "star.fill" : "star")
                                    .font(.system(size: 12)).foregroundStyle(Brand.gold)
                            }
                        }
                        if !review.comment.isEmpty {
                            Text(review.comment)
                                .font(Brand.font(14)).foregroundStyle(Brand.ink)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(13)
                    .background(.white, in: RoundedRectangle(cornerRadius: 13))

                    TextField(L.replyPlaceholder.t, text: $text, axis: .vertical)
                        .font(Brand.font(15))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(3...8)
                        .padding(13)
                        .background(.white, in: RoundedRectangle(cornerRadius: 13))

                    ErrorBanner(message: error)

                    BrandButton(title: .save, isLoading: working,
                                isEnabled: !text.trimmingCharacters(in: .whitespaces).isEmpty) {
                        Task { await submit() }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 24).padding(.top, 16)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.replyToReview.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(working)
                }
            }
        }
    }

    private func submit() async {
        working = true; defer { working = false }
        error = nil
        do {
            try await repo.reply(to: review,
                                 text: text.trimmingCharacters(in: .whitespaces))
            dismiss()
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
