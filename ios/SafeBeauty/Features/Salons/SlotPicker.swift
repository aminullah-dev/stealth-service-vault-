import SwiftUI
import SafeBeautyCore

/// Choosing a day and a time at one salon.
///
/// Extracted from SalonDetailView so rescheduling asks the same question the
/// same way. This file's own comment used to warn that "a second opinion about
/// which slots are free is exactly how a client offers a time the server then
/// refuses" — and a reschedule screen with its own copy of the layout maths
/// would have been precisely that second opinion.
struct SlotPicker: View {
    let salon: Salon
    /// What is being booked, so a two-hour job asks for two hours.
    let serviceNames: [String]
    /// The booking being moved. A booking never conflicts with its own slot,
    /// and without this the time she is already on reads as taken.
    var excluding: String = ""
    /// The chair she picked, "" for "any available". It changes which existing
    /// bookings count as a clash: the server skips one on a different staff id,
    /// so a grid computed for the wrong chair offers times it will refuse.
    var staffId: String = ""

    @Binding var selectedDay: Date
    @Binding var selectedSlot: Int64?
    /// Bumped by the parent to force a re-read — after the booking sheet
    /// closes, so a slot someone else took while she was deciding stops being
    /// offered.
    var reloadToken: Int = 0

    @State private var booked: [Appointment] = []
    @State private var isLoading = false
    /// A failed read is not an empty diary — treating it as one offers every
    /// hour of the day as free.
    @State private var unavailable = false
    @State private var waitlist = WaitlistStore.shared
    @State private var joining = false
    @State private var joinError = false

    @Environment(AuthService.self) private var auth

    /// The day she is looking at, as the start-of-day millis the waitlist keys
    /// on. Not the slot — the whole point is that no slot is free.
    private var dayStartMillis: Int64 {
        Int64(DayGrid.dayStart(selectedDay).timeIntervalSince1970 * 1000)
    }

    /// The next seven days, starting today, in Kabul.
    private var days: [Date] {
        (0..<7).compactMap {
            DayGrid.kabulCalendar.date(byAdding: .day, value: $0, to: Date())
        }
    }

    private var layout: Slots.Layout {
        // The salon's own timings, not empty maps: the client laid every service
        // out as one slot while the server built the span from the stored
        // durations — the narrow direction, which offers times the server then
        // refuses at payment.
        Slots.serviceLayout(
            serviceNames: serviceNames,
            timings: salon.serviceTiming,
            durationPerService: salon.durationPerService,
            slotMinutes: salon.slotDurationMinutes)
    }

    private var availableSlots: [Int64] {
        let layout = self.layout
        return DayGrid.slots(for: selectedDay,
                             hours: salon.workingHours,
                             slotMinutes: salon.slotDurationMinutes,
                             span: layout.span,
                             blockedDates: salon.blockedDates)
            .filter { start in
                !Slots.hasConflict(
                    existing: booked.filter { $0.id != excluding || excluding.isEmpty },
                    requestedStart: start,
                    requestedOffsets: layout.busyOffsets,
                    staffId: staffId, slotMinutes: salon.slotDurationMinutes)
            }
    }

    /// Which of the offered hours qualify right now.
    ///
    /// Computed from the same rule the server uses — LastMinute mirrors
    /// lib/money.js and a differential test pins the two together — but only to
    /// decide what to mark. The charge is still whatever createPaymentSession
    /// says it is, which is also why a slot can qualify here and not at
    /// checkout if she takes long enough to fall out of the window.
    private var discountedSlots: Set<Int64> {
        guard salon.lastMinuteEnabled else { return [] }
        return Set(availableSlots.filter {
            LastMinute.applies(start: $0,
                               enabled: salon.lastMinuteEnabled,
                               percent: salon.lastMinutePercent,
                               windowHours: salon.lastMinuteWindowHours)
        })
    }

    private var isClosedToday: Bool {
        let weekday = DayGrid.weekday(of: selectedDay)
        return !(salon.workingHours.first { $0.dayOfWeek == weekday }?.isOpen ?? false)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 10) {
                Text(L.chooseDay.t).font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                // Full-bleed, with the inset moved onto the scroll CONTENT.
                // Inside the page's padding the first chip — today — was clipped
                // by the viewport edge, so the one day she is most likely to
                // want was the one she could not read.
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 9) {
                        ForEach(days, id: \.timeIntervalSince1970) { day in
                            DayChip(day: day,
                                    isSelected: DayGrid.kabulCalendar.isDate(
                                        day, inSameDayAs: selectedDay)) {
                                selectedDay = day
                                selectedSlot = nil
                                Task { await load() }
                            }
                        }
                    }
                }
                .contentMargins(.horizontal, 22, for: .scrollContent)
                .padding(.horizontal, -22)
                .defaultScrollAnchor(.leading)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text(L.chooseTime.t).font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                if isLoading {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, alignment: .center)
                } else if unavailable {
                    // Three reasons, not two. "Could not load" is not "fully
                    // booked", and it is certainly not a free day.
                    Text(L.couldNotLoad.t)
                        .font(Brand.font(14))
                        .foregroundStyle(Color(hex: 0xC0392B))
                } else if availableSlots.isEmpty {
                    // A salon that is shut that day is not a salon that is fully
                    // booked, and telling her the wrong one wastes her time on
                    // the other six days.
                    Text(isClosedToday ? L.closedThatDay.t : L.noTimesLeft.t)
                        .font(Brand.font(14))
                        .foregroundStyle(Brand.accent)

                    // Full is not the same as closed, and only one of the two
                    // can still turn into an appointment. A fully-booked day was
                    // a dead end here: the sentence above was the entire answer,
                    // so the only way to learn a place had opened was to keep
                    // reopening the app.
                    if !isClosedToday {
                        if waitlist.isWaiting(salonId: salon.id, dayStart: dayStartMillis) {
                            Text(L.waitlistJoined.t)
                                .font(Brand.font(13))
                                .foregroundStyle(Brand.deep)
                        } else {
                            Button {
                                Task { await join() }
                            } label: {
                                HStack(spacing: 6) {
                                    if joining { ProgressView().tint(Brand.accent) }
                                    Text(L.joinWaitlist.t).font(Brand.font(14, .medium))
                                }
                                .foregroundStyle(Brand.deep)
                            }
                            .buttonStyle(.plain)
                            .disabled(joining)
                        }
                        if joinError { ErrorBanner(message: L.errNetwork.t) }
                    }
                } else {
                    // The salon's own standing offer on chairs about to go
                    // empty. It has been in createPaymentSession the whole time
                    // and iOS decoded none of the three fields, so a salon
                    // running one had it seen by nobody here — Shaghayeq Ha has
                    // had 8% inside four hours switched on all along.
                    if discountedSlots.count > 0 {
                        Text(L.lastMinuteOff(salon.lastMinutePercent))
                            .font(Brand.font(12.5, .medium))
                            .foregroundStyle(Brand.deep)
                    }
                    FlowLayout(spacing: 8) {
                        ForEach(availableSlots, id: \.self) { slot in
                            HStack(spacing: 0) {
                                TimeChip(millis: slot, isSelected: selectedSlot == slot) {
                                    selectedSlot = slot
                                }
                                // A mark, not a figure. The exact saving is the
                                // server's to compute at checkout, and a number
                                // shown here would be a second opinion about
                                // money — the one she would believe.
                                if discountedSlots.contains(slot) {
                                    Image(systemName: "bolt.fill")
                                        .font(.system(size: 10))
                                        .foregroundStyle(Brand.gold)
                                        .padding(.leading, -6)
                                        .accessibilityHidden(true)
                                }
                            }
                        }
                    }
                }
            }
        }
        .task { await load() }
        // The services can change under it — she adds one on the salon page —
        // and the span changes with them, so what fits changes too.
        .onChange(of: serviceNames) { _, _ in selectedSlot = nil }
        .onChange(of: staffId) { _, _ in selectedSlot = nil }
        .onChange(of: reloadToken) { _, _ in Task { await load() } }
    }

    private func join() async {
        guard let session = auth.session else { return }
        joining = true; defer { joining = false }
        joinError = false
        do {
            try await waitlist.join(salon: salon, dayStart: dayStartMillis,
                                    customerName: session.name)
        } catch {
            joinError = true
        }
    }

    /// Asks the server what is taken, rather than reading appointments directly.
    ///
    /// `getBookedSlots` returns only {time, staffId, isParty, id} — no customer
    /// names, no phone numbers. Reading the appointments collection would need
    /// permissions a customer does not have and should not have: who else is
    /// booked at this salon today is not her business.
    private func load() async {
        isLoading = true
        defer { isLoading = false }

        let start = DayGrid.dayStart(selectedDay)
        let end = start.addingTimeInterval(24 * 3600)
        // Not `try?`. A swallowed failure left `booked` empty, and an empty
        // booked list means "nothing is taken" — so a full salon renders as a
        // whole day of free times and she picks one the server refuses.
        let response: JSON?
        do {
            response = try await Callables.call("getBookedSlots", [
                "salonId": .string(salon.id),
                "dayStart": .int(Int(start.timeIntervalSince1970 * 1000)),
                "dayEnd": .int(Int(end.timeIntervalSince1970 * 1000)),
            ])
            unavailable = false
        } catch {
            unavailable = true
            booked = []
            return
        }

        // Each entry becomes a one-slot appointment: the server has already
        // expanded multi-slot bookings into individual busy times, so nothing
        // here needs to re-expand them.
        booked = (response?["booked"]?.arrayValue ?? []).compactMap { entry in
            guard let time = entry["time"]?.intValue else { return nil }
            var a = Appointment()
            a.id = entry["id"]?.stringValue ?? ""
            a.appointmentDate = Int64(time)
            a.staffId = entry["staffId"]?.stringValue ?? ""
            a.isParty = entry["isParty"]?.boolValue ?? false
            a.slotsCount = 1
            a.status = .confirmed
            return a
        }
    }
}
