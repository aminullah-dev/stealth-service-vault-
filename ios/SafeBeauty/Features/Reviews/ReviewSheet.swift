import SwiftUI
import SafeBeautyCore

/// Leaving a review for a visit that has happened.
struct ReviewSheet: View {
    let booking: Appointment

    @Environment(\.dismiss) private var dismiss
    @Environment(AuthService.self) private var auth

    @State private var rating = 0
    @State private var comment = ""
    @State private var isWorking = false
    @State private var error: String?
    @State private var done = false

    var body: some View {
        NavigationStack {
            ScrollView {
                if done {
                    VStack(spacing: 14) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 40)).foregroundStyle(Brand.deep)
                        Text(L.reviewThanks.t)
                            .font(Brand.font(16, .medium))
                            .foregroundStyle(Brand.ink)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 70).padding(.horizontal, 34)
                } else {
                    VStack(alignment: .leading, spacing: 18) {
                        Text(booking.salonName)
                            .font(Brand.font(18, .bold)).foregroundStyle(Brand.ink)

                        StarPicker(rating: $rating)

                        BrandField(label: .reviewComment, text: $comment)

                        // Said before she writes, not after she has posted.
                        // Her name is stored on the review and every signed-in
                        // user can read it, so leaving one is a small public
                        // statement about herself and she should be deciding
                        // with that in front of her.
                        Text(L.reviewNameWarning(auth.session?.name ?? ""))
                            .font(Brand.font(12))
                            .foregroundStyle(Brand.deep)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Brand.gold.opacity(0.15),
                                        in: RoundedRectangle(cornerRadius: 11))

                        ErrorBanner(message: error)

                        BrandButton(title: .sendReview, isLoading: isWorking,
                                    isEnabled: rating > 0) {
                            Task { await submit() }
                        }
                        .padding(.bottom, 30)
                    }
                    .padding(.horizontal, 22).padding(.top, 16)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.writeReview.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                }
            }
        }
    }

    private func submit() async {
        isWorking = true
        defer { isWorking = false }
        error = nil
        do {
            // imageUrls is deliberately not sent. The server anchors them to
            // this bucket's own download prefix, and photo review is a separate
            // piece of work — sending nothing is correct, sending a URL this
            // app has not uploaded would be refused.
            _ = try await Callables.call("submitReview", [
                "appointmentId": .string(booking.id),
                "salonId": .string(booking.salonId),
                "rating": .int(rating),
                "comment": .string(comment),
            ])
            done = true
        } catch let e as Callables.CallableError {
            error = switch e {
            case .failedPrecondition(let m, _):
                // The server distinguishes "not yet" from "already done", and
                // both are things she can act on differently.
                m.lowercased().contains("already") ? L.errAlreadyReviewed.t : L.errReviewTooEarly.t
            case .permissionDenied: L.errNotYourBooking.t
            default: L.errNetwork.t
            }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}

/// Five taps, sized for a thumb.
struct StarPicker: View {
    @Binding var rating: Int

    var body: some View {
        HStack(spacing: 10) {
            ForEach(1...5, id: \.self) { star in
                Button {
                    rating = star
                } label: {
                    Image(systemName: star <= rating ? "star.fill" : "star")
                        .font(.system(size: 30))
                        .foregroundStyle(star <= rating ? Brand.gold : Brand.petal)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(L.starsLabel(star))
            }
            Spacer()
        }
        // Laid out left-to-right in every language: a star rating reads as a
        // filled-from-one scale everywhere, and mirroring it would put five
        // stars where one belongs.
        .environment(\.layoutDirection, .leftToRight)
    }
}

/// A review as it appears on a salon.
struct ReviewRow: View {
    let review: Review

    @Environment(AuthService.self) private var auth
    @Environment(Moderation.self) private var moderation
    @State private var showReport = false

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 7) {
                HStack(spacing: 2) {
                    ForEach(1...5, id: \.self) { i in
                        Image(systemName: i <= review.rating ? "star.fill" : "star")
                            .font(.system(size: 11))
                            .foregroundStyle(i <= review.rating ? Brand.gold : Brand.petal)
                    }
                }
                .environment(\.layoutDirection, .leftToRight)

                Text(review.customerName)
                    .font(Brand.font(13, .medium))
                    .foregroundStyle(Brand.ink)
                Spacer()
                // A review is another customer's words under her own name, so
                // it needs the same flag the feed has. Not on her own review —
                // there is nothing to report about yourself, and the salon and
                // the admin moderate through their own tools.
                if review.customerId != auth.session?.uid {
                    Button { showReport = true } label: {
                        Image(systemName: "flag")
                            .font(.system(size: 11)).foregroundStyle(Brand.textMuted)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(L.reportAction.t)
                }
            }

            if !review.comment.isEmpty {
                Text(review.comment)
                    .font(Brand.font(13))
                    .foregroundStyle(Brand.ink.opacity(0.8))
            }

            if review.hasReply {
                // The salon's answer, indented under what it answers, so a
                // reply is never mistaken for another customer's review.
                HStack(alignment: .top, spacing: 6) {
                    Image(systemName: "arrow.turn.down.right")
                        .font(.system(size: 10)).foregroundStyle(Brand.accent)
                    Text(review.providerReply)
                        .font(Brand.font(12.5))
                        .foregroundStyle(Brand.accent)
                }
                .padding(.top, 2)
            }
        }
        .padding(.vertical, 4)
        .sheet(isPresented: $showReport) {
            ReportSheet(target: .review, targetId: review.id,
                        authorId: review.customerId, authorKind: "USER",
                        moderation: moderation)
                .appDirection()
        }
    }
}
