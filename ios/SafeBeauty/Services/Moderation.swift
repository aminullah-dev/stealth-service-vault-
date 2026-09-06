import Foundation
import FirebaseFirestore
import Observation

/// What can be reported, matching `functions/lib/moderation.js` exactly.
///
/// The server refuses anything not on its own list, so a value added here and
/// not there fails at the call rather than silently reporting the wrong thing.
enum ReportTarget: String, Sendable {
    case post = "POST"
    case story = "STORY"
    case comment = "COMMENT"
    case review = "REVIEW"
}

/// The reasons the server accepts, in the order they are offered.
enum ReportReason: String, CaseIterable, Identifiable, Sendable {
    case harassment = "HARASSMENT"
    case nudity = "NUDITY"
    case hate = "HATE"
    case scam = "SCAM"
    case spam = "SPAM"
    case other = "OTHER"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .harassment: L.reportHarassment.t
        case .nudity: L.reportNudity.t
        case .hate: L.reportHate.t
        case .scam: L.reportScam.t
        case .spam: L.reportSpam.t
        case .other: L.reportOther.t
        }
    }
}

/// Reporting content, and not seeing a person again.
///
/// Apple's Guideline 1.2 asks three things of an app that carries other
/// people's words and photographs: a way to report them, a way to block the
/// person, and a commitment to act within a day. This app had none of the
/// three. The third is the admin console's queue; these are the first two.
///
/// Blocks are a local preference written straight to Firestore — `blocks/{me}_{them}`,
/// which the rules let only the blocker write and only the blocker read. They
/// change nobody else's view, so no callable is involved. Reports go through
/// `reportContent` because the report has to name the content's author, and
/// that is a fact a client must not be able to invent.
@MainActor
@Observable
final class Moderation {
    /// App-uids this customer has blocked. Read once at sign-in and kept live.
    private(set) var blocked: Set<String> = []

    private var uid: String = ""
    private let listener = ModerationListenerBox()

    deinit { listener.clear() }

    func start(uid: String) {
        guard !uid.isEmpty else { return }
        self.uid = uid
        listener.clear()
        // A listener rather than a one-shot read: she blocks someone from a
        // comment thread and the feed behind it has to stop showing them
        // without a relaunch.
        listener.set(
            Firestore.firestore().collection("blocks")
                .whereField("blockerId", isEqualTo: uid)
                .order(by: "createdAt", descending: true)
                .limit(to: 500)
                .addSnapshotListener { [weak self] snap, _ in
                    let ids = (snap?.documents ?? []).compactMap { $0.data()["blockedId"] as? String }
                    Task { @MainActor in self?.blocked = Set(ids) }
                })
    }

    func stop() { listener.clear(); blocked = [] }

    /// True when nothing this person wrote should be shown.
    ///
    /// An empty id is never blocked: a document missing its author field would
    /// otherwise match the empty string and disappear for everyone.
    func isBlocked(_ authorId: String) -> Bool {
        !authorId.isEmpty && blocked.contains(authorId)
    }

    func block(_ authorId: String, kind: String) async throws {
        guard !authorId.isEmpty, authorId != uid else { return }
        try await Firestore.firestore().document("blocks/\(uid)_\(authorId)").setData([
            "blockerId": uid,
            "blockedId": authorId,
            "blockedKind": kind,
            "createdAt": Int(Date().timeIntervalSince1970 * 1000),
        ])
    }

    func unblock(_ authorId: String) async throws {
        guard !authorId.isEmpty else { return }
        try await Firestore.firestore().document("blocks/\(uid)_\(authorId)").delete()
    }

    func report(_ target: ReportTarget, id: String,
                reason: ReportReason, note: String) async throws {
        _ = try await Callables.call("reportContent", [
            "targetType": .string(target.rawValue),
            "targetId": .string(id),
            "reason": .string(reason.rawValue),
            "note": .string(note),
        ])
    }
}

/// Same reason SalonRepository has one: `deinit` is nonisolated and cannot
/// touch a @MainActor property, and a Firestore listener that outlives its
/// owner keeps billing and keeps firing.
private final class ModerationListenerBox: @unchecked Sendable {
    private let lock = NSLock()
    private var registration: ListenerRegistration?

    func set(_ r: ListenerRegistration) {
        lock.lock(); defer { lock.unlock() }
        registration?.remove()
        registration = r
    }

    func clear() {
        lock.lock(); defer { lock.unlock() }
        registration?.remove()
        registration = nil
    }
}
