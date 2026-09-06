import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// The salons she has hearted.
///
/// iOS had no favourites at all — not the heart on a card, not the tab Android
/// gives them. A customer who found a salon she liked had to find it again by
/// name every time.
///
/// Firestore is the source of truth here, where Android keeps a local Room
/// table and mirrors it up. Same documents either way — `favorites/{uid}_{salonId}`
/// with `{customerId, salonId, createdAt}`, which is what the rules bind and
/// what `pushOfferToFavoriters` reads to tell her when a salon she likes posts
/// an offer. Reading it directly means her favourites follow her to a new phone,
/// and means there is no second copy to fall out of step with the one the server
/// notifies from.
@MainActor
@Observable
final class FavoritesStore {
    static let shared = FavoritesStore()

    private(set) var salonIds: Set<String> = []
    private var listener: ListenerRegistration?
    private var uid = ""

    private init() {}

    func bind(uid: String) {
        guard uid != self.uid else { return }
        self.uid = uid
        listener?.remove(); listener = nil
        salonIds = []
        guard !uid.isEmpty else { return }
        listener = Firestore.firestore().collection("favorites")
            .whereField("customerId", isEqualTo: uid)
            // Bounded, like every other listener in this app. Nobody hearts two
            // hundred salons in a city with two, and an unbounded listener on a
            // collection that only grows costs more every month she stays.
            .limit(to: 200)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self else { return }
                self.salonIds = Set((snapshot?.documents ?? []).compactMap {
                    $0.data()["salonId"] as? String
                })
            }
    }

    /// On sign-out. Left bound, the next person on a shared phone sees hearts
    /// that are not hers.
    func unbind() {
        listener?.remove(); listener = nil
        salonIds = []
        uid = ""
    }

    func contains(_ salonId: String) -> Bool { salonIds.contains(salonId) }

    /// Optimistic, because a heart that waits for the network reads as a tap
    /// that did not register, and she taps again.
    func toggle(_ salonId: String) {
        guard !uid.isEmpty, !salonId.isEmpty else { return }
        let ref = Firestore.firestore().document("favorites/\(uid)_\(salonId)")
        if salonIds.contains(salonId) {
            salonIds.remove(salonId)
            ref.delete { [weak self] error in
                // Put it back if the server refused, so the screen never claims
                // a change that did not happen.
                if error != nil { self?.salonIds.insert(salonId) }
            }
        } else {
            salonIds.insert(salonId)
            ref.setData([
                "customerId": uid,
                "salonId": salonId,
                "createdAt": Int(Date().timeIntervalSince1970 * 1000),
            ]) { [weak self] error in
                if error != nil { self?.salonIds.remove(salonId) }
            }
        }
    }
}
