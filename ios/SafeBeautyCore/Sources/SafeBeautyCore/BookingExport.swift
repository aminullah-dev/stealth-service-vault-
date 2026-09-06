import Foundation

/// Her booking history as a CSV file she can keep.
///
/// Pure, so the quoting can be tested — which matters more than it looks.
/// Android's version writes `"$date,${appt.serviceName},${appt.salonName}"`
/// with no escaping at all, so one salon named "Zohra, Kabul" silently shifts
/// every column after it and the file opens wrong in every spreadsheet. A
/// service name typed with a comma does the same. That is not a hypothetical
/// in a product where salons name themselves.
///
/// Dates are Kabul's, not the phone's. A woman travelling with her phone on
/// another timezone would otherwise get an export whose times do not match the
/// appointments she actually has — the same reason `WorkingHours` pins the
/// zone rather than trusting the device.
public enum BookingExport {

    public static let filename = "my-appointments.csv"

    /// RFC 4180: a field containing a comma, a quote or a newline is wrapped in
    /// quotes, and quotes inside it are doubled.
    static func escape(_ field: String) -> String {
        guard field.contains(",") || field.contains("\"") || field.contains("\n")
                || field.contains("\r")
        else { return field }
        return "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    public static func csv(_ appointments: [Appointment]) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Asia/Kabul")
        formatter.dateFormat = "yyyy-MM-dd HH:mm"

        // The same four columns Android writes, in the same order, so one
        // household with two phones does not end up with two shapes of file.
        var out = "Date,Service,Salon,Status\n"
        for appointment in appointments {
            let date = formatter.string(
                from: Date(timeIntervalSince1970: Double(appointment.appointmentDate) / 1000))
            out += [date, appointment.serviceName, appointment.salonName,
                    appointment.status.rawValue]
                .map(escape).joined(separator: ",")
            out += "\n"
        }
        return out
    }
}
