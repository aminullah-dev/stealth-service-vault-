import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// Reading salons, with the two things a Firestore query gets wrong quietly.
///
/// **orderBy drops documents that lack the field.** Not sorts them last —
/// omits them. `sortRating` is written by the discovery derivation, so a salon
/// the derivation has never touched simply is not in the results, and the
/// screen looks correct while being short. This repository sorts on the client
/// for exactly that reason: the catalogue is small, and a missing salon is a
/// worse outcome than an unindexed sort.
///
/// **An equality never matches an empty field.** Filtering by city silently
/// excluded half the live catalogue until 2026-09-04, because an ambiguous
/// district produced no district key and the city was derived from that key.
/// That is fixed on the server; the client still treats "no city filter" as the
/// default so a salon whose city is somehow blank is visible rather than lost.
@MainActor
@Observable
final class SalonRepository {
    private(set) var salons: [Salon] = []
    private(set) var isLoading = false
    private(set) var error: String?
    /// Documents the app could read from Firestore but could not decode.
    /// Surfaced rather than swallowed — see DocumentDecoding.
    private(set) var unreadable: Int = 0

    /// The listener lives in a box that is not actor-isolated.
    ///
    /// `deinit` is nonisolated and cannot touch a @MainActor property, so a
    /// listener stored directly on this class cannot be detached when the
    /// object goes away — and a Firestore listener that outlives its owner
    /// keeps billing and keeps firing into a view that is gone. The box exists
    /// so cleanup is possible from deinit; `remove()` is documented as safe to
    /// call from any thread.
    private let listener = ListenerBox()

    deinit { listener.clear() }

    /// Live updates rather than a one-shot read: a salon that goes unavailable
    /// while someone is looking at the list should stop being bookable then,
    /// not at the next pull-to-refresh.
    func start(city: String? = nil) {
        listener.clear()
        isLoading = true
        error = nil

        var query: Query = Firestore.firestore().collection("salons")
        if let city, !city.isEmpty {
            query = query.whereField("city", isEqualTo: city)
        }
        // Bounded. An unbounded collection read is a bill and a stall on a slow
        // connection, and nobody scrolls past this many salons.
        query = query.limit(to: 200)

        listener.set(query.addSnapshotListener { [weak self] snapshot, err in
            guard let self else { return }
            Task { @MainActor in
                self.isLoading = false
                if let err {
                    self.error = err.localizedDescription
                    return
                }
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                let result = DocumentDecoding.decodeAll(
                    Salon.self, documents: docs, assigningID: { $0.id = $1 })

                self.unreadable = result.failures.count
                // Sorted here, not by orderBy — see the note on this type.
                self.salons = result.values
                    .filter(\.isAvailable)
                    .sorted {
                        if $0.sortRating != $1.sortRating { return $0.sortRating > $1.sortRating }
                        return $0.salonName < $1.salonName
                    }

                // "No salons" and "we could not read the salons" are different
                // sentences and the screen must be able to tell them apart.
                if result.isCompletelyBroken {
                    self.error = "decode"
                }
            }
        })
    }

    func stop() { listener.clear() }
}

/// Holds a Firestore listener outside any actor, so it can be detached from
/// deinit. Guarded by a lock because `set` and `clear` can race a deinit on
/// another thread, and the cost of losing that race is a listener nothing owns.
private final class ListenerBox: @unchecked Sendable {
    private let lock = NSLock()
    private var registration: ListenerRegistration?

    func set(_ new: ListenerRegistration) {
        lock.lock(); defer { lock.unlock() }
        registration?.remove()
        registration = new
    }

    func clear() {
        lock.lock(); defer { lock.unlock() }
        registration?.remove()
        registration = nil
    }
}
