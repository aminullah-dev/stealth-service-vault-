import Foundation
import Testing
@testable import SafeBeautyCore

@Suite("Booking export")
struct BookingExportTests {

    private func appointment(service: String, salon: String,
                             at millis: Int64 = 0) -> Appointment {
        var a = Appointment()
        a.serviceName = service
        a.salonName = salon
        a.appointmentDate = millis
        a.status = .confirmed
        return a
    }

    @Test("a comma in a name does not shift the columns")
    func quotesCommas() {
        // Android writes these raw, so a salon that names itself with a comma
        // silently moves every column after it. Salons name themselves.
        let out = BookingExport.csv([appointment(service: "مو", salon: "Zohra, Kabul")])
        #expect(out.contains("\"Zohra, Kabul\""))
        // Four fields, still: header and row have the same comma count once
        // the quoted one is accounted for.
        let row = out.split(separator: "\n")[1]
        #expect(row.hasSuffix(",CONFIRMED"))
    }

    @Test("a quote inside a field is doubled, not dropped")
    func doublesQuotes() {
        let out = BookingExport.csv([appointment(service: "\"special\"", salon: "S")])
        #expect(out.contains("\"\"\"special\"\"\""))
    }

    @Test("a newline in a field cannot break the row apart")
    func quotesNewlines() {
        let out = BookingExport.csv([appointment(service: "line1\nline2", salon: "S")])
        // Header, then one record — the embedded newline is inside quotes, so
        // the record spans two physical lines but is still one record.
        #expect(out.contains("\"line1\nline2\""))
    }

    @Test("an ordinary field is not quoted, so the file stays readable")
    func leavesPlainFieldsAlone() {
        #expect(BookingExport.escape("مو") == "مو")
        #expect(BookingExport.escape("") == "")
    }

    @Test("times are Kabul's, not the phone's")
    func kabulTime() {
        // 2026-09-06T00:00:00Z. Kabul is UTC+4:30, so this is 04:30 the same
        // day — a phone set to UTC would write 00:00 and disagree with the
        // appointment she actually has.
        let out = BookingExport.csv([appointment(service: "s", salon: "x",
                                                 at: 1_788_652_800_000)])
        #expect(out.contains("2026-09-06 04:30"))
    }

    @Test("no bookings is a header and nothing else, not an empty file")
    func emptyStillHasHeader() {
        #expect(BookingExport.csv([]) == "Date,Service,Salon,Status\n")
    }
}
