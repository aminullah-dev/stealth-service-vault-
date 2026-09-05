import SwiftUI
import SafeBeautyCore

struct MyBookingsView: View {
    @Environment(AuthService.self) private var auth
    @State private var repo = BookingsRepository()
    @State private var cancelling: Appointment?
    @State private var reviewing: Appointment?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Group {
                if repo.isLoading && repo.upcoming.isEmpty && repo.past.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if repo.upcoming.isEmpty && repo.past.isEmpty {
                    ContentUnavailableView {
                        // Only said when the read succeeded and there really
                        // are none. An unreadable list says something else.
                        Text(repo.error == nil ? L.noBookingsYet.t : L.couldNotLoad.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else {
                    List {
                        if !repo.upcoming.isEmpty {
                            Section(L.upcoming.t) {
                                ForEach(repo.upcoming) { booking in
                                    BookingRow(booking: booking, canCancel: true) {
                                        cancelling = booking
                                    }
                                }
                            }
                        }
                        if !repo.past.isEmpty {
                            Section(L.pastBookings.t) {
                                ForEach(repo.past) { booking in
                                    BookingRow(
                                        booking: booking, canCancel: false, onCancel: {},
                                        // Offered only where submitReview would
                                        // accept it. A button the server refuses
                                        // teaches her not to trust the buttons.
                                        canReview: ReviewEligibility.canReview(booking),
                                        onReview: { reviewing = booking })
                                }
                            }
                        }
                        if repo.unreadable > 0 {
                            // Surfaced rather than hidden. On Android the
                            // equivalent situation showed an empty screen and
                            // said nothing at all.
                            Section {
                                Text(L.someBookingsUnreadable.t)
                                    .font(Brand.font(12.5))
                                    .foregroundStyle(Color(hex: 0xC0392B))
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.myBookings.t)
            .alert(L.cancelBooking.t, isPresented: .constant(cancelling != nil)) {
                Button(L.keepIt.t, role: .cancel) { cancelling = nil }
                Button(L.cancelBooking.t, role: .destructive) {
                    if let b = cancelling { Task { await cancel(b) } }
                }
            } message: {
                Text(L.cancelWarning.t)
            }
            .sheet(item: $reviewing) { ReviewSheet(booking: $0) }
        }
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { repo.start(customerId: uid) }
        }
    }

    private func cancel(_ booking: Appointment) async {
        cancelling = nil
        do { try await repo.cancel(appointmentId: booking.id) }
        catch { self.error = L.errNetwork.t }
    }
}

struct BookingRow: View {
    let booking: Appointment
    let canCancel: Bool
    let onCancel: () -> Void
    var canReview: Bool = false
    var onReview: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(booking.salonName)
                    .font(Brand.font(15, .bold))
                    .foregroundStyle(Brand.ink)
                Spacer()
                StatusPill(status: booking.status)
            }

            // serviceNames handles both eras: the itemised list when it exists,
            // and the comma-joined string for every booking written before the
            // services field did.
            if !booking.serviceNames.isEmpty {
                Text(booking.serviceNames.joined(separator: "، "))
                    .font(Brand.font(13))
                    .foregroundStyle(Brand.accent)
            }

            HStack(spacing: 5) {
                Image(systemName: "calendar").font(.system(size: 11))
                Text(Self.when(booking.date))
                    .environment(\.layoutDirection, .leftToRight)
            }
            .font(Brand.font(12.5))
            .foregroundStyle(Brand.deep)

            if booking.total > 0 {
                HStack(spacing: 4) {
                    Text(verbatim: "\(booking.total)")
                        .environment(\.layoutDirection, .leftToRight)
                    Text(L.afn.t)
                }
                .font(Brand.font(12.5, .medium))
                .foregroundStyle(Brand.ink.opacity(0.8))
            }

            if !booking.bookingCode.isEmpty {
                // The code the salon asks for at the door.
                Text(booking.bookingCode)
                    .font(.system(size: 12, design: .monospaced))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.gold)
            }

            if canCancel {
                Button(L.cancelBooking.t, role: .destructive, action: onCancel)
                    .font(Brand.font(13, .medium))
                    .padding(.top, 2)
            }
            if canReview {
                Button(L.writeReview.t, action: onReview)
                    .font(Brand.font(13, .medium))
                    .foregroundStyle(Brand.accent)
                    .padding(.top, 2)
            }
        }
        .padding(.vertical, 5)
        .listRowBackground(Color.white)
    }

    private static func when(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = DayGrid.kabul          // the salon's clock, not the phone's
        f.locale = AppLanguage.current.locale
        f.setLocalizedDateFormatFromTemplate("EEE d MMM HH:mm")
        return f.string(from: date)
    }
}

struct StatusPill: View {
    let status: AppointmentStatus

    var body: some View {
        Text(label)
            .font(Brand.font(11, .bold))
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(tint.opacity(0.16), in: Capsule())
            .foregroundStyle(tint)
    }

    private var label: String {
        switch status {
        case .awaitingPayment: L.statusAwaitingPayment.t
        case .pending: L.statusPending.t
        case .confirmed: L.statusConfirmed.t
        case .completed: L.statusCompleted.t
        case .cancelled: L.statusCancelled.t
        // A status this build has never heard of shows its own name rather
        // than blank space, so a backend change is visible instead of silent.
        case .unknown: L.statusUnknown.t
        }
    }

    private var tint: Color {
        switch status {
        case .confirmed, .completed: Brand.deep
        case .awaitingPayment, .pending: Brand.gold
        case .cancelled, .unknown: Color(hex: 0xC0392B)
        }
    }
}
