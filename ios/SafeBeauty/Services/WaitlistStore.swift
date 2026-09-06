import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// Her place in the queue for days that are already full.
///
/// Android has offered this since the beginning and the rules have always
/// allowed it. On iPhone a fully-booked day was a dead end: the times list said
/// "no times left" and that was the whole answer, so the only way to find out
/// whether a place opened was to keep re-opening the app.
@MainActor
@Observable
final class WaitlistStore {
    static let shared = WaitlistStore()

    private(set) var entries: [WaitlistEntry] = []
    private var listener: ListenerRegistration?
    private var uid = ""

    private init() {}

    /// The live ones, newest day first. EXPIRED rows are history.
    var live: [WaitlistEntry] {
        entries.filter(\.isLive).sorted { $0.requestedDate < $1.requestedDate }
    }

    func bind(uid: String) {
        guard uid != self.uid else { return }
        self.uid = uid
        listener?.remove(); listener = nil
        entries = []
        guard !uid.isEmpty else { return }
        listener = Firestore.firestore().collection("waitlist")
            .whereField("customerId", isEqualTo: uid)
            // Bounded, and filtered to what she can still act on. Android
            // learned this one by downloading every entry a long-standing
            // customer had ever joined.
            .whereField("status", in: ["WAITING", "SLOT_AVAILABLE"])
            .limit(to: 50)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self else { return }
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                self.entries = DocumentDecoding.decodeAll(
                    WaitlistEntry.self, documents: docs, assigningID: { $0.id = $1 }).values
            }
    }

    func unbind() {
        listener?.remove(); listener = nil
        entries = []
        uid = ""
    }

    /// Whether she is already waiting on that salon and day, so the button can
    /// say so rather than letting her queue twice.
    func isWaiting(salonId: String, dayStart: Int64) -> Bool {
        live.contains { $0.salonId == salonId && $0.requestedDate == dayStart }
    }

    func join(salon: Salon, dayStart: Int64, customerName: String) async throws {
        guard !uid.isEmpty else { return }
        var entry = WaitlistEntry()
        entry.salonId = salon.id
        entry.salonName = salon.salonName
        entry.customerId = uid
        entry.customerName = customerName
        entry.requestedDate = dayStart
        entry.status = "WAITING"
        entry.createdAt = Int64(Date().timeIntervalSince1970 * 1000)
        try await Firestore.firestore().collection("waitlist").addDocument(data: [
            "salonId": entry.salonId, "salonName": entry.salonName,
            "customerId": entry.customerId, "customerName": entry.customerName,
            "requestedDate": entry.requestedDate, "status": entry.status,
            "createdAt": entry.createdAt,
        ])
    }

    /// Leaving the queue entirely. Deleting is hers to do; the rules allow it
    /// on her own rows.
    func leave(_ entry: WaitlistEntry) async throws {
        try await Firestore.firestore().document("waitlist/\(entry.id)").delete()
    }

    /// Turning down an offered slot. EXPIRED rather than deleted, so the salon
    /// can see the offer was made and pass it on — and the rules admit exactly
    /// this one field change from her.
    func dismiss(_ entry: WaitlistEntry) async throws {
        try await Firestore.firestore().document("waitlist/\(entry.id)")
            .updateData(["status": "EXPIRED"])
    }
}
