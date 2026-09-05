import Foundation

/// The slot grid, ported from `functions/lib/slots.js` rule for rule.
///
/// This is the one piece of client logic where disagreeing with the server does
/// not produce a wrong screen — it produces two women in the same chair. The
/// server re-checks every booking with `hasSlotConflict` and rejects a
/// collision, so a client that computes a *wider* grid than the server merely
/// hides bookable times; a client that computes a *narrower* one offers times
/// that will be refused at payment, after she has chosen. Neither is
/// acceptable, so this matches exactly and is tested against vectors generated
/// from the server's own implementation.
public enum Slots {

    public struct Layout: Equatable, Sendable {
        /// How many slots of wall-clock the appointment spans.
        public let span: Int
        /// Which of those the chair is actually occupied for.
        public let busyOffsets: [Int]
    }

    /// Where a service's time goes: occupied, then unattended, then occupied.
    public struct Timing: Sendable, Equatable {
        public let activeBefore: Int   // minutes the stylist is with her
        public let processing: Int     // minutes the chair is free — dye developing
        public let activeAfter: Int

        public init(activeBefore: Int = 0, processing: Int = 0, activeAfter: Int = 0) {
            self.activeBefore = activeBefore
            self.processing = processing
            self.activeAfter = activeAfter
        }
    }

    /// Lay a list of services out across the grid.
    ///
    /// The asymmetry between `ceil` and `floor` is the whole point and is not a
    /// rounding preference. Active time is rounded UP, because a stylist who
    /// needs 35 minutes of a 30-minute slot occupies two. Processing time is
    /// rounded DOWN, because those minutes are when the chair is free and
    /// rounding them up would blank out a slot somebody else could have had.
    ///
    /// The guard that follows matters just as much: when a service is pure
    /// processing — dye that develops for an hour with no attended time — both
    /// counts round to zero and the booking would occupy the chair for no slots
    /// at all, so it could be double-booked against itself. One slot is always
    /// claimed.
    public static func serviceLayout(
        serviceNames: [String],
        timings: [String: Timing] = [:],
        durationPerService: [String: Int] = [:],
        slotMinutes: Int
    ) -> Layout {
        let step = max(1, slotMinutes)
        guard !serviceNames.isEmpty else { return Layout(span: 1, busyOffsets: [0]) }

        var busy: [Int] = []
        var cursor = 0

        for name in serviceNames {
            var beforeSlots: Int
            var procSlots: Int
            var afterSlots: Int

            let t = timings[name]
            let before = max(0, t?.activeBefore ?? 0)
            let proc = max(0, t?.processing ?? 0)
            let after = max(0, t?.activeAfter ?? 0)

            if before + proc + after > 0 {
                beforeSlots = Int((Double(before) / Double(step)).rounded(.up))
                procSlots = Int((Double(proc) / Double(step)).rounded(.down))
                afterSlots = Int((Double(after) / Double(step)).rounded(.up))
                // Pure processing would otherwise block nothing at all.
                if beforeSlots + afterSlots == 0 { beforeSlots = 1 }
            } else {
                // No timing breakdown: the whole duration is attended.
                let d = durationPerService[name]
                let minutes = (d.map { $0 > 0 } ?? false) ? d! : step
                beforeSlots = Int((Double(minutes) / Double(step)).rounded(.up))
                procSlots = 0
                afterSlots = 0
            }

            for i in 0..<max(0, beforeSlots) { busy.append(cursor + i) }
            cursor += beforeSlots + procSlots
            for i in 0..<max(0, afterSlots) { busy.append(cursor + i) }
            cursor += afterSlots
        }

        return Layout(span: max(1, cursor), busyOffsets: busy.isEmpty ? [0] : busy)
    }

    /// Which offsets an existing booking occupies.
    ///
    /// A booking with no usable `busyOffsets` blocks its whole span. That is
    /// the safe direction: an older booking written before the field existed
    /// must not be treated as blocking nothing, or it can be booked over.
    public static func busyOffsets(stored: [Int]?, span: Int) -> [Int] {
        guard let stored, !stored.isEmpty else { return Array(0..<span) }
        let clean = stored.filter { $0 >= 0 && $0 < span }
        return clean.isEmpty ? Array(0..<span) : clean
    }

    /// The wall-clock instants an existing booking occupies, in milliseconds.
    public static func slotsFor(
        startMillis: Int64,
        slotsCount: Int?,
        servicesCount: Int = 0,
        storedBusyOffsets: [Int]? = nil,
        slotMinutes: Int
    ) -> [Int64] {
        let step = Int64(max(1, slotMinutes)) * 60_000
        // slotsCount first, then how many services there are, then one. An
        // older booking has neither and still occupies its slot.
        let span = max(1, (slotsCount ?? 0) > 0 ? slotsCount! : (servicesCount > 0 ? servicesCount : 1))
        return busyOffsets(stored: storedBusyOffsets, span: span)
            .map { startMillis + Int64($0) * step }
    }

    /// Whether a requested booking collides with anything already there.
    ///
    /// Two rules that are easy to get subtly wrong:
    ///
    /// A party booking takes the whole salon, so it conflicts with every chair
    /// rather than only its own. That is checked from BOTH sides — an existing
    /// party blocks a new single booking, and a new party is blocked by any
    /// existing booking.
    ///
    /// A cancelled appointment never conflicts. It still exists as a document,
    /// and treating it as occupied would slowly make a busy salon unbookable.
    public static func hasConflict(
        existing: [Appointment],
        requestedStart: Int64,
        requestedOffsets: [Int],
        staffId: String,
        slotMinutes: Int,
        excludeId: String? = nil,
        requestIsParty: Bool = false
    ) -> Bool {
        let step = Int64(max(1, slotMinutes)) * 60_000
        let offsets = requestedOffsets.filter { $0 >= 0 }
        let wanted = Set((offsets.isEmpty ? [0] : offsets).map { requestedStart + Int64($0) * step })

        for appointment in existing {
            if appointment.status == .cancelled { continue }
            if let excludeId, appointment.id == excludeId { continue }

            let wholeSalon = requestIsParty || appointment.isParty
            if !wholeSalon && appointment.staffId != staffId { continue }

            let taken = slotsFor(
                startMillis: appointment.appointmentDate,
                slotsCount: appointment.slotsCount,
                servicesCount: appointment.services.count,
                storedBusyOffsets: appointment.busyOffsets.isEmpty ? nil : appointment.busyOffsets,
                slotMinutes: slotMinutes
            )
            if taken.contains(where: wanted.contains) { return true }
        }
        return false
    }
}
