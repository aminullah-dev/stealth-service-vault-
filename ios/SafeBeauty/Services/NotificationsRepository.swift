import Foundation
import FirebaseFirestore
import SafeBeautyCore

@MainActor
@Observable
final class NotificationsRepository {
    private(set) var items: [AppNotification] = []
    private(set) var isLoading = false
    private(set) var error: String?

    var unreadCount: Int { items.filter { !$0.isRead }.count }

    private let listener = NotifListenerBox()
    deinit { listener.clear() }

    func start(recipientId: String) {
        guard !recipientId.isEmpty else { return }
        listener.clear()
        isLoading = true

        // recipientId + createdAt DESC is an existing composite index. Bounded
        // at 50: a notification from four months ago is not something anyone
        // scrolls to, and an unbounded read is a bill on every app open.
        let query = Firestore.firestore().collection("notifications")
            .whereField("recipientId", isEqualTo: recipientId)
            .order(by: "createdAt", descending: true)
            .limit(to: 50)

        listener.set(query.addSnapshotListener { [weak self] snapshot, err in
            guard let self else { return }
            Task { @MainActor in
                self.isLoading = false
                if let err { self.error = err.localizedDescription; return }
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                self.items = DocumentDecoding.decodeAll(
                    AppNotification.self, documents: docs,
                    assigningID: { $0.id = $1 }).values
            }
        })
    }

    func stop() { listener.clear() }

    /// Marking read is a direct write, which the rules permit only to the
    /// recipient (`allow update: if resource.data.recipientId == me()`). No
    /// callable needed — this changes nothing but her own view of her own row.
    func markRead(_ notification: AppNotification) async {
        guard !notification.isRead, !notification.id.isEmpty else { return }
        try? await Firestore.firestore()
            .document("notifications/\(notification.id)")
            .updateData(["isRead": true])
    }

    func markAllRead() async {
        // One batch rather than fifty writes. Firestore's limit is 500 and the
        // query is capped at 50, so this never needs splitting.
        let unread = items.filter { !$0.isRead && !$0.id.isEmpty }
        guard !unread.isEmpty else { return }
        let batch = Firestore.firestore().batch()
        for item in unread {
            batch.updateData(["isRead": true],
                             forDocument: Firestore.firestore().document("notifications/\(item.id)"))
        }
        try? await batch.commit()
    }
}

private final class NotifListenerBox: @unchecked Sendable {
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
