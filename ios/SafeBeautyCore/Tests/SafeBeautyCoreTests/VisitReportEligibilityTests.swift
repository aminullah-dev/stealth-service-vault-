import Foundation
import Testing
@testable import SafeBeautyCore

@Suite("VisitReportEligibility mirrors canReportVisit in visitreport.js")
struct VisitReportEligibilityTests {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func appointment(status: AppointmentStatus = .completed,
                             hoursAgo: Double = 2,
                             reported: Bool = false) -> Appointment {
        var a = Appointment()
        a.status = status
        a.appointmentDate = Int64((now.timeIntervalSince1970 - hoursAgo * 3600) * 1000)
        a.visitReported = reported
        return a
    }

    @Test("a visit that has happened can be reported")
    func happyPath() {
        #expect(VisitReportEligibility.canReport(appointment(), now: now))
    }

    @Test("CONFIRMED counts, because she may be outside a locked salon right now")
    func confirmedCounts() {
        // completePastAppointments only flips it two hours after the start.
        // Making her wait for that is making her wait for a cron job.
        #expect(VisitReportEligibility.canReport(
            appointment(status: .confirmed, hoursAgo: 0.1), now: now))
    }

    @Test("a booking never accepted, or already cancelled, is not this")
    func otherStatuses() {
        for status: AppointmentStatus in [.pending, .cancelled, .awaitingPayment] {
            #expect(!VisitReportEligibility.canReport(appointment(status: status), now: now),
                    "\(status) should not be reportable")
        }
    }

    @Test("not before it was due")
    func notBeforeTheAppointment() {
        #expect(!VisitReportEligibility.canReport(appointment(hoursAgo: -1), now: now))
        // The appointed moment itself is allowed.
        #expect(VisitReportEligibility.canReport(appointment(hoursAgo: 0), now: now))
    }

    @Test("and not after the window the server enforces")
    func window() {
        let days = Double(VisitReportEligibility.windowDays)
        #expect(VisitReportEligibility.canReport(appointment(hoursAgo: days * 24), now: now))
        #expect(!VisitReportEligibility.canReport(appointment(hoursAgo: days * 24 + 1), now: now))
    }

    @Test("once, so the button is not offered on a visit already reported")
    func onlyOnce() {
        // The same defect the reviewed flag exists for: without it the button
        // came back and the server refused it, which teaches her not to trust
        // the buttons.
        #expect(!VisitReportEligibility.canReport(appointment(reported: true), now: now))
    }

    @Test("the window here is the window there")
    func windowMatchesServer() {
        // REPORT_WINDOW_DAYS in functions/lib/visitreport.js. If one moves the
        // other must, or the app offers a button the server has stopped
        // accepting — or hides one it would still take.
        #expect(VisitReportEligibility.windowDays == 14)
    }
}
