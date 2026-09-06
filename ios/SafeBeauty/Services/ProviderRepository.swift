import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// Everything the salon owner's side of the app reads.
///
/// One repository rather than one per tab: the six screens all answer questions
/// about the same salon and the same diary, and six listeners on overlapping
/// data is six times the cost for one salon's worth of information.
///
/// The salon is found by `providerId`, not stored on the session — a provider
/// registers before her salon exists, and the id only appears once an admin
/// approves her.
@MainActor
@Observable
final class ProviderRepository {
    private(set) var salon: Salon?
    private(set) var appointments: [Appointment] = []
    private(set) var reviews: [Review] = []
    private(set) var owed = 0
    private(set) var isLoading = true
    /// The salon details registration parked on her user document when it could
    /// not finish. Their presence is the difference between "waiting for an
    /// admin" and "one tap away from existing".
    private(set) var pendingSalonName = ""
    private(set) var pendingSalonDistrict = ""
    private(set) var pendingSalonServices: [String] = []

    var canFinishSalonSetup: Bool {
        salon == nil && !pendingSalonName.isEmpty && !pendingSalonDistrict.isEmpty
    }
    /// A failed read is not an empty diary. Told apart, because "no requests"
    /// and "could not load" send a salon owner to two different places.
    private(set) var loadFailed = false

    private var listeners: [ListenerRegistration] = []
    private var uid = ""

    /// Waiting on her, and the only tab with a number on it.
    var pending: [Appointment] {
        appointments.filter { $0.status == .pending }
            .sorted { $0.appointmentDate < $1.appointmentDate }
    }

    /// Confirmed and still ahead — her actual diary.
    var upcoming: [Appointment] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return appointments
            .filter { $0.status == .confirmed && $0.appointmentDate >= now }
            .sorted { $0.appointmentDate < $1.appointmentDate }
    }

    var past: [Appointment] {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return appointments
            .filter { $0.status != .pending && ($0.appointmentDate < now || $0.status == .completed) }
            .sorted { $0.appointmentDate > $1.appointmentDate }
    }

    func start(providerId: String) {
        guard providerId != uid else { return }
        stop()
        uid = providerId
        guard !providerId.isEmpty else { return }
        let db = Firestore.firestore()

        // Her balance. Written by the webhook, read-only here — the ledger is
        // not something a client is allowed to disagree with.
        listeners.append(db.document("provider_balances/\(providerId)")
            .addSnapshotListener { [weak self] snap, _ in
                let d = snap?.data() ?? [:]
                self?.owed = (d["owedAmount"] as? Int)
                    ?? Int((d["owedAmount"] as? Double) ?? 0)
            })

        // Read once, not watched: these only change when registration writes
        // them or createProviderSalon clears them, and the second case is
        // followed by a reload anyway.
        Task { [weak self] in
            let snap = try? await db.document("users/\(providerId)").getDocument()
            guard let self, let d = snap?.data() else { return }
            self.pendingSalonName = (d["pendingSalonName"] as? String) ?? ""
            self.pendingSalonDistrict = (d["pendingSalonDistrict"] as? String) ?? ""
            self.pendingSalonServices = (d["pendingSalonServices"] as? [String]) ?? []
        }

        listeners.append(db.collection("salons")
            .whereField("providerId", isEqualTo: providerId)
            .limit(to: 1)
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                self.isLoading = false
                if error != nil { self.loadFailed = true; return }
                guard let doc = snap?.documents.first else {
                    // No salon yet is a real state, not a failure: registration
                    // parks the details and an admin creates the salon on
                    // approval.
                    self.salon = nil
                    return
                }
                self.loadFailed = false
                var decoded = try? DocumentDecoding.decode(Salon.self, from: doc.data())
                decoded?.id = doc.documentID
                self.salon = decoded
                self.watchSalon(doc.documentID)
            })
    }

    /// The diary and the reviews, which can only be queried once the salon id
    /// is known.
    private var salonListeners: [ListenerRegistration] = []
    private var watchedSalon = ""

    private func watchSalon(_ salonId: String) {
        guard salonId != watchedSalon else { return }
        watchedSalon = salonId
        salonListeners.forEach { $0.remove() }
        salonListeners = []
        let db = Firestore.firestore()

        // Bounded, and ordered server-side. An unordered limit is not "the
        // newest 300" — Firestore takes the first 300 by document id, which is
        // arbitrary, so a busy salon would silently stop seeing new bookings.
        salonListeners.append(db.collection("appointments")
            .whereField("salonId", isEqualTo: salonId)
            .order(by: "appointmentDate", descending: true)
            .limit(to: 300)
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if error != nil { self.loadFailed = true; return }
                self.loadFailed = false
                let docs = (snap?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                self.appointments = DocumentDecoding.decodeAll(
                    Appointment.self, documents: docs, assigningID: { $0.id = $1 }).values
            })

        salonListeners.append(db.collection("reviews")
            .whereField("salonId", isEqualTo: salonId)
            .limit(to: 100)
            .addSnapshotListener { [weak self] snap, _ in
                guard let self else { return }
                let docs = (snap?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                self.reviews = DocumentDecoding.decodeAll(
                    Review.self, documents: docs, assigningID: { $0.id = $1 }).values
                    .sorted { $0.createdAt > $1.createdAt }
            })
    }

    func stop() {
        (listeners + salonListeners).forEach { $0.remove() }
        listeners = []; salonListeners = []
        watchedSalon = ""; uid = ""
        salon = nil; appointments = []; reviews = []; owed = 0
        pendingSalonName = ""; pendingSalonDistrict = ""; pendingSalonServices = []
        isLoading = true; loadFailed = false
    }

    /// Finishes a salon that registration started and could not complete.
    ///
    /// A dropped connection at the one moment registration creates the salon
    /// used to end the story on iOS: the details were parked on her user
    /// document, nothing read them, and the app told her to wait for an admin
    /// who had nothing to approve. The callable is idempotent — a retry racing
    /// another retry returns the same salon rather than making a second one.
    func finishSalonSetup() async throws {
        _ = try await Callables.call("createProviderSalon", [
            "salonName": .string(pendingSalonName),
            "district": .string(pendingSalonDistrict),
            "services": .strings(pendingSalonServices),
        ])
        pendingSalonName = ""; pendingSalonDistrict = ""; pendingSalonServices = []
    }

    /// The salon's own note on a customer, after a visit.
    ///
    /// Never shown to her: it feeds the platform's own view of a customer who
    /// books and does not come, which is a real cost to a salon that held a
    /// chair for her.
    func report(_ booking: Appointment, rating: Int, noShow: Bool,
                flagged: Bool, comment: String) async throws {
        _ = try await Callables.call("reportCustomer", [
            "appointmentId": .string(booking.id),
            "rating": .int(rating),
            "noShow": .bool(noShow),
            "flagged": .bool(flagged),
            "comment": .string(comment),
        ])
    }

    /// Saves what a salon owner may change about her own salon.
    ///
    /// A merge, not a set. firestore.rules evaluates `request.resource.data` as
    /// the MERGED document, and the rule freezes ten fields by comparing them to
    /// their current values — rating, confirmedCount, categories, districtKey,
    /// reliability and the rest. A merge leaves every one of them equal by not
    /// mentioning them; a full set would have to reproduce all ten exactly or be
    /// refused, and would silently drop any the client does not know about.
    ///
    /// districtKey and categories are deliberately absent even though the
    /// district and the services are being written: deriveSalonFields re-derives
    /// them, and a salon that could write them directly could file itself under
    /// every category and every neighbourhood.
    func saveSalon(name: String, district: String, areaKey: String,
                   services: [String], prices: [String: Int],
                   hours: [WorkingHours], blockedDates: [String],
                   isAvailable: Bool) async throws {
        guard let id = salon?.id, !id.isEmpty else { return }
        try await Firestore.firestore().document("salons/\(id)").updateData([
            "salonName": name,
            "district": district,
            "areaKey": areaKey,
            "services": services,
            "pricePerService": prices,
            "workingHours": hours.map {
                ["dayOfWeek": $0.dayOfWeek, "isOpen": $0.isOpen,
                 "openHour": $0.openHour, "openMinute": $0.openMinute,
                 "closeHour": $0.closeHour, "closeMinute": $0.closeMinute]
            },
            // Kabul-local "yyyy-MM-dd", the same key DayGrid filters on and the
            // same one rescheduleAppointment checks server-side.
            "blockedDates": blockedDates,
            "isAvailable": isAvailable,
        ])
    }

    /// Accepting a booking. The server checks she owns the salon and that the
    /// slot is still free, so a stale list cannot double-book a chair.
    func confirm(_ appointment: Appointment) async throws {
        _ = try await Callables.call("confirmAppointment",
                                     ["appointmentId": .string(appointment.id)])
    }

    /// Turning one down. Refunds a paid booking server-side — which is why it
    /// is a callable and not a status write.
    func decline(_ appointment: Appointment) async throws {
        _ = try await Callables.call("providerDeclineAppointment",
                                     ["appointmentId": .string(appointment.id)])
    }
}
