import SwiftUI
import SafeBeautyCore

struct MyBookingsView: View {
    @Environment(AuthService.self) private var auth
    @State private var repo = BookingsRepository()
    @State private var cancelling: Appointment?
    @State private var reviewing: Appointment?
    @State private var rescheduling: Appointment?
    @State private var tipping: Appointment?
    @State private var waitlist = WaitlistStore.shared
    @State private var error: String?
    @State private var reportingVisit: Appointment?

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
                        Label {
                            Text(repo.error == nil ? L.noBookingsYet.t : L.couldNotLoad.t)
                                .font(Brand.font(17, .medium))
                                .foregroundStyle(repo.error == nil ? Brand.ink : Color(hex: 0xC0392B))
                        } icon: {
                            Image(systemName: repo.error == nil ? "calendar" : "exclamationmark.triangle")
                                .foregroundStyle(repo.error == nil ? Brand.accent : Color(hex: 0xC0392B))
                        }
                    } description: {
                        // Only on the genuinely-empty branch: telling someone
                        // whose list failed to load to go and book something
                        // answers a question she did not ask.
                        if repo.error == nil {
                            Text(L.noBookingsHint.t)
                                .font(Brand.font(13.5))
                                .foregroundStyle(Brand.accent)
                        }
                    }
                } else {
                    List {
                        if !repo.upcoming.isEmpty {
                            Section(L.upcoming.t) {
                                ForEach(repo.upcoming) { booking in
                                    // cancelAppointment refuses anything that is
                                    // not PENDING or CONFIRMED, so an
                                    // AWAITING_PAYMENT row — an abandoned
                                    // checkout — was offering a button the
                                    // server always turned down.
                                    BookingRow(
                                        booking: booking,
                                        canCancel: booking.status == .pending
                                                || booking.status == .confirmed,
                                        onCancel: { cancelling = booking },
                                        // rescheduleAppointment accepts exactly
                                        // the same two statuses cancel does, so
                                        // the two buttons appear and disappear
                                        // together.
                                        canReschedule: booking.status == .pending
                                                    || booking.status == .confirmed,
                                        onReschedule: { rescheduling = booking })
                                }
                            }
                        }
                        // Between the upcoming bookings and the past ones,
                        // because that is what it is: a booking she does not
                        // have yet. Android shows the same rows in the same
                        // sheet.
                        if !waitlist.live.isEmpty {
                            Section(L.waitlist.t) {
                                ForEach(waitlist.live) { entry in
                                    WaitlistRow(entry: entry)
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
                                        onReview: { reviewing = booking },
                                        // Only on a visit that actually
                                        // happened. createTipSession refuses
                                        // anything else, and the whole tip goes
                                        // to the salon — no commission.
                                        canTip: booking.status == .completed,
                                        onTip: { tipping = booking },
                                        // The same window the server enforces,
                                        // so a button she can press is one the
                                        // server will accept.
                                        canReportVisit: VisitReportEligibility.canReport(booking),
                                        onReportVisit: { reportingVisit = booking })
                                }
                            }
                        }
                        if let error {
                            // cancel() has always written this and nothing has
                            // ever rendered it, so a refused cancellation left
                            // the row exactly as it was and said nothing at all.
                            Section { ErrorBanner(message: error) }
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
            .sheet(item: $reviewing) { ReviewSheet(booking: $0).appDirection() }
            .sheet(item: $tipping) { TipSheet(booking: $0).appDirection() }
            .sheet(item: $reportingVisit) { ReportVisitSheet(appointment: $0).appDirection() }
            .sheet(item: $rescheduling) { booking in
                // The list is a live snapshot, so the moved booking redraws on
                // its own; onMoved only has to close the sheet's own state.
                RescheduleSheet(booking: booking, onMoved: {}).appDirection()
            }
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
    var canReschedule = false
    var onReschedule: () -> Void = {}
    var canReview: Bool = false
    var onReview: () -> Void = {}
    var canTip = false
    var onTip: () -> Void = {}
    var canReportVisit = false
    var onReportVisit: () -> Void = {}

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

            if canReschedule || canCancel {
                HStack(spacing: 16) {
                    // Moving it comes before cancelling it. Most people who open
                    // this row want a different hour, not to lose the booking,
                    // and putting the destructive one first invites the wrong
                    // tap.
                    // .borderless on both, which is not cosmetic. A List row
                    // with a single Button lets the whole row trigger it; add a
                    // second and SwiftUI stops routing the tap to either, so
                    // both buttons go dead. That is what happened the moment
                    // «تغییر زمان» was added beside «لغو رزرو».
                    if canReschedule {
                        Button(L.reschedule.t, action: onReschedule)
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.accent)
                            .buttonStyle(.borderless)
                    }
                    if canCancel {
                        Button(L.cancelBooking.t, role: .destructive, action: onCancel)
                            .font(Brand.font(13, .medium))
                            .buttonStyle(.borderless)
                    }
                }
                .padding(.top, 2)
            }
            if canReview || canTip || canReportVisit {
                HStack(spacing: 16) {
                    if canReview {
                        Button(L.writeReview.t, action: onReview)
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.accent)
                            .buttonStyle(.borderless)
                    }
                    if canTip {
                        Button(L.tip.t, action: onTip)
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.deep)
                            .buttonStyle(.borderless)
                    }
                    // Quiet and last. Most visits are fine, and a complaint
                    // button competing with "leave a review" invites the wrong
                    // one. But it is on the row, not buried in support, because
                    // the salon's own button to report HER is on its row.
                    if canReportVisit {
                        Button(L.reportVisitAction.t, action: onReportVisit)
                            .font(Brand.font(12.5))
                            .foregroundStyle(Brand.textMuted)
                            .buttonStyle(.borderless)
                    }
                }
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

/// One waiting place, with the two things she can do about it.
///
/// SLOT_AVAILABLE is the row that matters: the salon has offered her a place
/// and it goes to the next person if she does nothing, so it does not look like
/// the ones that are merely waiting.
struct WaitlistRow: View {
    let entry: WaitlistEntry

    @State private var waitlist = WaitlistStore.shared
    @State private var working = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(entry.salonName)
                    .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                Spacer(minLength: 0)
                Text(entry.isOffered ? L.waitlistOffered.t : L.waitlistWaiting.t)
                    .font(Brand.font(11.5, .medium))
                    .foregroundStyle(entry.isOffered ? .white : Brand.accent)
                    .padding(.horizontal, 9).padding(.vertical, 4)
                    .background(entry.isOffered ? AnyShapeStyle(Brand.gradient)
                                                : AnyShapeStyle(Brand.petal.opacity(0.45)),
                                in: Capsule())
            }
            Text("\(DayChip.weekdayName(entry.date)) \(DayChip.dayNumber(entry.date))")
                .font(Brand.font(13)).foregroundStyle(Brand.accent)

            HStack(spacing: 16) {
                // Dismiss writes EXPIRED so the salon can pass the place on;
                // leaving deletes the row outright. Two different intentions and
                // the rules admit both, narrowly.
                Button(entry.isOffered ? L.dismiss.t : L.leaveWaitlist.t) {
                    Task {
                        working = true; defer { working = false }
                        if entry.isOffered { try? await waitlist.dismiss(entry) }
                        else { try? await waitlist.leave(entry) }
                    }
                }
                .font(Brand.font(13, .medium))
                .foregroundStyle(Brand.accent)
                .buttonStyle(.borderless)
                .disabled(working)
            }
            .padding(.top, 2)
        }
        .padding(.vertical, 5)
        .listRowBackground(Color.white)
    }
}
