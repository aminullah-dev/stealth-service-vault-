import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// The customer's own bookings.
///
/// This is the screen that blanked on Android. `services` is stored as
/// [{name, price}] and the model declared List<String>; the resulting throw
/// escaped a snapshot listener on the main thread, and every customer with one
/// booking lost the screen entirely. The model here handles that shape and the
/// decoding is per-document, so the equivalent bad row costs one row.
@MainActor
@Observable
final class BookingsRepository {
    private(set) var upcoming: [Appointment] = []
    private(set) var past: [Appointment] = []
    private(set) var isLoading = false
    private(set) var error: String?
    /// Rows Firestore returned that could not be decoded. Shown rather than
    /// swallowed — a customer whose booking is unreadable deserves to know one
    /// exists rather than to be told she has none.
    private(set) var unreadable = 0

    private let listener = BookingsListenerBox()

    deinit { listener.clear() }

    func start(customerId: String) {
        guard !customerId.isEmpty else { return }
        listener.clear()
        isLoading = true
        error = nil

        // customerId + appointmentDate DESC is an existing composite index.
        // Ordering by appointmentDate also means a document without one is
        // dropped by Firestore before it ever reaches the decoder — which is
        // consistent with the model, since a booking with no time is not a
        // booking and Appointment refuses to decode one.
        let query = Firestore.firestore().collection("appointments")
            .whereField("customerId", isEqualTo: customerId)
            .order(by: "appointmentDate", descending: true)
            .limit(to: 100)

        listener.set(query.addSnapshotListener { [weak self] snapshot, err in
            guard let self else { return }
            Task { @MainActor in
                self.isLoading = false
                if let err { self.error = err.localizedDescription; return }

                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                let result = DocumentDecoding.decodeAll(
                    Appointment.self, documents: docs, assigningID: { $0.id = $1 })
                self.unreadable = result.failures.count

                // Split by whether she still has somewhere to be, not by status
                // alone: a CONFIRMED booking whose time has passed belongs in
                // history even though nothing has marked it complete yet.
                let now = Date()
                self.upcoming = result.values
                    .filter { $0.status.isLive && $0.date > now }
                    .sorted { $0.appointmentDate < $1.appointmentDate }
                self.past = result.values
                    .filter { !($0.status.isLive && $0.date > now) }
            }
        })
    }

    func stop() { listener.clear() }

    /// Cancelling goes through the callable, which handles the refund trail.
    /// A client-side status write would leave the money behind.
    func cancel(appointmentId: String) async throws {
        _ = try await Callables.call("cancelAppointment",
                                     ["appointmentId": .string(appointmentId)])
    }
}

/// Same reasoning as SalonRepository's: deinit is nonisolated and cannot touch
/// a @MainActor property, so a listener stored directly on the class could
/// never be detached.
private final class BookingsListenerBox: @unchecked Sendable {
    private let lock = NSLock()
    private var registration: ListenerRegistration?

    func set(_ new: ListenerRegistration) {
        lock.lock(); defer { lock.unlock() }
        registration?.remove(); registration = new
    }
    func clear() {
        lock.lock(); defer { lock.unlock() }
        registration?.remove(); registration = nil
    }
}
