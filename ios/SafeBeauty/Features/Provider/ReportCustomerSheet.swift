import SwiftUI
import SafeBeautyCore

/// The salon's own note on a customer, after a visit.
///
/// `reportCustomer` is PROVIDER-only and was deployed with no caller on iOS, so
/// a salon owner here had no way to record that a customer booked a chair and
/// did not come — which is a real cost to her, and the only signal the platform
/// has for a customer who does it repeatedly.
///
/// Never shown to the customer. That is worth saying on the screen: a salon
/// owner who thinks her note will be read by the person it is about writes a
/// different note, or none.
struct ReportCustomerSheet: View {
    let booking: Appointment
    let repo: ProviderRepository

    @Environment(\.dismiss) private var dismiss

    @State private var rating = 5
    @State private var noShow = false
    @State private var flagged = false
    @State private var comment = ""
    @State private var working = false
    @State private var error: String?
    @State private var done = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(ProviderBookingRow.identify(booking))
                            .font(Brand.font(16, .bold)).foregroundStyle(Brand.ink)
                        Text(TimeChip.dateLabel(booking.appointmentDate))
                            .font(Brand.font(13)).foregroundStyle(Brand.accent)
                    }

                    // Stars first, because most visits are fine and this is the
                    // only field she will touch on those.
                    HStack(spacing: 8) {
                        ForEach(1...5, id: \.self) { star in
                            Button { rating = star } label: {
                                Image(systemName: star <= rating ? "star.fill" : "star")
                                    .font(.system(size: 26))
                                    .foregroundStyle(Brand.gold)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text(verbatim: "\(star)"))
                        }
                    }

                    Toggle(L.customerNoShow.t, isOn: $noShow)
                        .font(Brand.font(14.5, .medium))
                        .foregroundStyle(Brand.ink).tint(Brand.accent)
                    Toggle(L.customerFlag.t, isOn: $flagged)
                        .font(Brand.font(14.5, .medium))
                        .foregroundStyle(Brand.ink).tint(Brand.accent)

                    BrandField(label: .commentOptional, text: $comment)

                    Text(L.rateCustomerNote.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

                    if done { ErrorBanner(message: L.rateCustomerDone.t, tone: .notice) }
                    ErrorBanner(message: error)

                    if !done {
                        BrandButton(title: .save, isLoading: working) {
                            Task { await submit() }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.rateCustomer.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(working)
                }
            }
        }
    }

    private func submit() async {
        working = true; defer { working = false }
        error = nil
        do {
            try await repo.report(booking, rating: rating, noShow: noShow,
                                  flagged: flagged,
                                  comment: comment.trimmingCharacters(in: .whitespaces))
            done = true
        } catch let e as Callables.CallableError {
            error = e.localized ?? L.errNetwork.t
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
