import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Rating one closed support conversation, once.
///
/// A direct write to `support_tickets/{uid}/history/{id}`, which the rules
/// allow only while `rating` is still 0 and only for `rating` (Int 1…5),
/// `ratingComment` (≤ 500) and `ratedAt` (Int). Both values are Swift `Int`s
/// so they are stored as Firestore integers; a Double would be refused by
/// `is int`.
///
/// Used in two places — the empty current conversation, right after a close,
/// and the bottom of a past conversation's transcript — so the control is one
/// view and behaves the same in both.
struct SupportRatingCard: View {
    let conversation: SupportConversation
    let title: L
    /// Called as the write starts. The listener applies the write locally at
    /// once, which makes the row no longer rateable — a caller that decides
    /// visibility from `canBeRated` alone would pull the card out from under
    /// its own spinner and its thank-you.
    var onSubmitting: () -> Void = {}
    var onRated: () -> Void = {}
    /// Present only where hiding the card makes sense; the transcript has no
    /// "not now" because the card there is already out of the way.
    var onNotNow: (() -> Void)?

    @Environment(AuthService.self) private var auth
    @State private var rating = 0
    @State private var comment = ""
    @State private var isWorking = false
    @State private var error: String?
    @State private var done = false

    var body: some View {
        VStack(spacing: 14) {
            if done {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 34)).foregroundStyle(Brand.gold)
                    .accessibilityHidden(true)
                Text(L.ratingThanks.t)
                    .font(Brand.font(15, .medium))
                    .foregroundStyle(Brand.ink)
                    .multilineTextAlignment(.center)
            } else {
                Text(title.t)
                    .font(Brand.font(16, .medium))
                    .foregroundStyle(Brand.ink)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)

                StarPicker(rating: $rating, centered: true)

                TextField(L.rateConversationCommentPlaceholder.t, text: $comment, axis: .vertical)
                    .font(Brand.font(14))
                    .foregroundStyle(Brand.ink)
                    .lineLimit(1...4)
                    .multilineTextAlignment(.leading)
                    .padding(.horizontal, 13).padding(.vertical, 10)
                    .background(Brand.chipInactive, in: RoundedRectangle(cornerRadius: 12))

                ErrorBanner(message: error)

                BrandButton(title: .submitRating, isLoading: isWorking, isEnabled: rating > 0) {
                    Task { await submit() }
                }

                if let onNotNow {
                    Button(L.notNow.t, action: onNotNow)
                        .font(Brand.font(14))
                        .foregroundStyle(Brand.textMuted)
                        .frame(minHeight: 44)
                        .buttonStyle(.borderless)
                        .disabled(isWorking)
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 18))
        .animation(.easeInOut(duration: 0.2), value: done)
    }

    private func submit() async {
        guard rating > 0, !conversation.id.isEmpty,
              let uid = auth.session?.uid, !uid.isEmpty else { return }
        isWorking = true
        error = nil
        onSubmitting()
        defer { isWorking = false }
        do {
            try await Firestore.firestore()
                .collection("support_tickets").document(uid)
                .collection("history").document(conversation.id)
                .updateData([
                    "rating": Int(rating),
                    "ratingComment": SupportConversation.clampComment(comment),
                    "ratedAt": Int(Date().timeIntervalSince1970 * 1000),
                ])
            done = true
            onRated()
        } catch {
            // Refused (already rated from another device) or never reached the
            // server. Either way the card stays, with what she chose intact.
            self.error = L.ratingNotSaved.t
        }
    }
}

/// A rating as a row of small stars, or "Not rated".
struct SupportRatingStars: View {
    let rating: Int

    var body: some View {
        if rating > 0 {
            HStack(spacing: 2) {
                ForEach(1...5, id: \.self) { i in
                    Image(systemName: i <= rating ? "star.fill" : "star")
                        .font(.system(size: 11))
                        .foregroundStyle(i <= rating ? Brand.gold : Brand.petal)
                }
            }
            // A scale filled from one, in every language — see StarPicker.
            .environment(\.layoutDirection, .leftToRight)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L.starsLabel(rating))
        } else {
            Text(L.notRated.t)
                .font(Brand.font(12))
                .foregroundStyle(Brand.textMuted)
        }
    }
}
