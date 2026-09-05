import Testing
import Foundation
@testable import SafeBeautyCore

/// Decoding tests written against the shape production actually stores, copied
/// from a real appointment document rather than invented.
///
/// The `services` field is the reason this file exists. On Android it was
/// declared `List<String>`, the server writes `[{name, price}]`, and the
/// resulting throw came out of a snapshot listener on the main thread — so a
/// screen that had passed 122 tests and shipped in a signed bundle blanked for
/// every customer with one booking.
@Suite("Appointment decoding against the real document shape")
struct AppointmentDecodingTests {

    /// Field for field as `functions` writes it.
    ///
    /// Computed rather than stored: `[String: Any]` is not Sendable, so a
    /// static constant of it is a shared-mutable-state error under Swift 6
    /// strict concurrency. A fresh dictionary per call also means one test
    /// mutating the fixture cannot affect another.
    static var production: [String: Any] { [
        "bookingCode": "SB-DJ3NFP",
        "customerId": "bfc0d936-fae0-4aa6-a198-5ad5d3572d73",
        "customerName": "Ahmad Ahmadi",
        "customerPhone": "+93775885999",
        "salonId": "rwbjNnfWDU1xeBT5Y2qP",
        "salonName": "Shaghayeq Ha",
        "serviceName": "mo",
        "services": [["name": "mo", "price": 80]],
        "slotsCount": 1,
        "busyOffsets": [0],
        "isParty": false,
        "party": [],
        "partySize": 0,
        "staffId": "",
        "staffName": "",
        "appointmentDate": 1_787_684_400_000,
        "paymentMethod": "ONLINE",
        "createdAt": 1_787_542_954_417,
        "notes": "",
        "reminderSent": false,
        "customerReported": false,
        "customerRatingSum": 0,
        "customerRatingCount": 0,
        "noShowCount": 0,
        "status": "CANCELLED",
    ] }

    @Test("the exact production document decodes, services included")
    func decodesProductionShape() throws {
        let a = try DocumentDecoding.decode(Appointment.self, from: Self.production)
        #expect(a.bookingCode == "SB-DJ3NFP")
        #expect(a.salonName == "Shaghayeq Ha")
        #expect(a.status == .cancelled)
        // The field that caused the outage — modelled, not skipped.
        #expect(a.services == [BookedService(name: "mo", price: 80)])
        #expect(a.total == 80)
        #expect(a.appointmentDate == 1_787_684_400_000)
    }

    @Test("a booking written before `services` existed still shows its services")
    func fallsBackToServiceName() throws {
        var old = Self.production
        old.removeValue(forKey: "services")
        old["serviceName"] = "ناخن، ارایش"
        let a = try DocumentDecoding.decode(Appointment.self, from: old)
        #expect(a.services.isEmpty)
        // Split on the Arabic comma, which is what the data actually contains.
        #expect(a.serviceNames == ["ناخن", "ارایش"])
    }

    @Test("an unknown status does not throw")
    func unknownStatus() throws {
        // A status added on the server must not blank the bookings screen of
        // every phone running an older build.
        var future = Self.production
        future["status"] = "RESCHEDULED_BY_SALON"
        let a = try DocumentDecoding.decode(Appointment.self, from: future)
        #expect(a.status == .unknown)
        #expect(a.bookingCode == "SB-DJ3NFP", "the rest of the document still reads")
    }

    @Test("null fields fall back to defaults instead of failing the document")
    func nullsAreSurvivable() throws {
        var withNulls = Self.production
        withNulls["notes"] = NSNull()
        withNulls["staffName"] = NSNull()
        let a = try DocumentDecoding.decode(Appointment.self, from: withNulls)
        #expect(a.notes == "")
        #expect(a.staffName == "")
    }

    /// The property the Android crash was really about.
    @Test("one unreadable document does not take the others down")
    func oneBadDocumentIsIsolated() {
        let good = (id: "a1", data: Self.production)
        var broken = Self.production
        broken["appointmentDate"] = ["not": "a number"]   // wrong type, will throw
        let bad = (id: "a2", data: broken)

        let result = DocumentDecoding.decodeAll(
            Appointment.self,
            documents: [good, bad, good],
            assigningID: { $0.id = $1 }
        )

        #expect(result.values.count == 2, "the readable bookings still render")
        #expect(result.failures.count == 1)
        #expect(result.failures.first?.documentID == "a2")
        #expect(!result.isCompletelyBroken)
        // On Android this same situation produced zero bookings and no error
        // anyone could see, because the throw escaped the listener.
    }

    @Test("a snapshot where everything is broken says so rather than looking empty")
    func totalFailureIsDistinguishable() {
        var broken = Self.production
        broken["appointmentDate"] = ["not": "a number"]
        let result = DocumentDecoding.decodeAll(
            Appointment.self, documents: [(id: "x", data: broken)]
        )
        // "No bookings" and "we could not read your bookings" are different
        // sentences, and a customer deserves the true one.
        #expect(result.isCompletelyBroken)
    }

    @Test("isLive marks the statuses worth showing and reminding about")
    func liveStatuses() {
        #expect(AppointmentStatus.pending.isLive)
        #expect(AppointmentStatus.confirmed.isLive)
        #expect(AppointmentStatus.awaitingPayment.isLive)
        #expect(!AppointmentStatus.completed.isLive)
        #expect(!AppointmentStatus.cancelled.isLive)
        #expect(!AppointmentStatus.unknown.isLive)
    }
}
