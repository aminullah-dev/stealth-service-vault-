import SwiftUI
import SafeBeautyCore

/// The bookings waiting on her answer — the one screen a salon cannot operate
/// without.
///
/// A PENDING booking is a customer holding a slot and waiting. Android puts it
/// first and badges it; on iOS there was no way to see one at all, so an owner
/// with an iPhone learned about a request only if she opened a laptop.
struct ProviderRequestsView: View {
    let repo: ProviderRepository

    @State private var declining: Appointment?
    @State private var working: String?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Group {
                if repo.isLoading && repo.salon == nil {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if repo.salon == nil {
                    NoSalonYet(repo: repo)
                } else if repo.loadFailed && repo.appointments.isEmpty {
                    // Not the same as "no requests", and telling her the wrong
                    // one means she stops checking.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t).font(Brand.font(16, .medium))
                    }
                } else if repo.pending.isEmpty {
                    ContentUnavailableView {
                        Text(L.noRequests.t)
                            .font(Brand.font(16, .medium)).foregroundStyle(Brand.ink)
                    }
                } else {
                    List {
                        Section(L.awaitingYou.t) {
                            ForEach(repo.pending) { booking in
                                ProviderBookingRow(
                                    booking: booking, showsActions: true,
                                    onAccept: { Task { await act(booking, accept: true) } },
                                    onDecline: { declining = booking },
                                    isWorking: working == booking.id)
                            }
                        }
                        if let error {
                            Section { ErrorBanner(message: error) }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabRequests.t)
            .confirmationDialog(L.decline.t, isPresented: .constant(declining != nil),
                                titleVisibility: .visible) {
                Button(L.decline.t, role: .destructive) {
                    if let booking = declining {
                        declining = nil
                        Task { await act(booking, accept: false) }
                    }
                }
                Button(L.cancel.t, role: .cancel) { declining = nil }
            } message: {
                // Says what it costs her before she taps it: a paid booking is
                // refunded in full, which is money leaving her salon.
                Text(L.declineConfirm.t)
            }
        }
    }

    private func act(_ booking: Appointment, accept: Bool) async {
        working = booking.id
        defer { working = nil }
        error = nil
        do {
            if accept { try await repo.confirm(booking) } else { try await repo.decline(booking) }
        } catch let e as Callables.CallableError {
            // The server refuses a slot taken since the list was drawn, and
            // saying so is the whole point — she would otherwise think the
            // button was broken.
            if case .failedPrecondition(let m, _) = e { error = m }
            else { error = L.errNetwork.t }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
