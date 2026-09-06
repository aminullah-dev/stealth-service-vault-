import Foundation
import FirebaseMessaging
import FirebaseFirestore
import UserNotifications
import UIKit

/// Push notifications, which the app had none of.
///
/// The server has always sent them — `pushOnNotificationCreated` fires on every
/// notification document and reads `users/{uid}.fcmToken` — and iOS never
/// registered, so it received none. That is not a missing nicety on this
/// product: a customer learns her booking was confirmed by being told, and
/// without a push she finds out only if she happens to reopen the app. The
/// Notifications tab was showing rows nobody knew were there.
///
/// Two fields matter on her user document and this writes both:
///
///   `fcmToken` — where to send. Android writes it at sign-in the same way.
///   `lang`     — which language to send in. The server reads it
///                (domains/notifications.js) and falls back to Dari, so an iOS
///                user reading Pashto received Dari pushes no matter what she
///                had chosen in the app.
@MainActor
final class PushService: NSObject {
    static let shared = PushService()

    /// The app-level uid to write against, set once she is signed in. Held
    /// rather than read from AuthService because the token can arrive before
    /// or after sign-in, and both orders have to end with it written.
    private var appUid = ""

    private override init() { super.init() }

    /// Called once at launch, before any view.
    func start() {
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
    }

    /// Asks, then registers. Deliberately not at launch: the first thing a new
    /// user sees should not be a permission sheet for notifications about
    /// bookings she has not made yet. Called after sign-in, where the request
    /// has an obvious reason behind it.
    func requestAuthorisation() async {
        let granted = (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        guard granted else { return }
        UIApplication.shared.registerForRemoteNotifications()
    }

    /// Binds the signed-in account and writes whatever we already know.
    func bind(uid: String) {
        guard !uid.isEmpty else { return }
        appUid = uid
        writeLanguage()
        if let token = Messaging.messaging().fcmToken { write(token: token) }
    }

    /// On sign-out. A token left pointing at a shared phone would send the next
    /// person's notifications about someone else's bookings.
    func unbind() {
        let uid = appUid
        appUid = ""
        guard !uid.isEmpty else { return }
        Firestore.firestore().document("users/\(uid)").updateData(["fcmToken": ""]) { _ in }
        Messaging.messaging().deleteToken { _ in }
    }

    /// Her chosen language, so the server sends in it. Called on bind and
    /// whenever she changes the picker.
    func writeLanguage() {
        guard !appUid.isEmpty else { return }
        Firestore.firestore().document("users/\(appUid)")
            .updateData(["lang": AppLanguage.current.rawValue]) { _ in }
    }

    private func write(token: String) {
        guard !appUid.isEmpty else { return }
        // Best effort, and not worth surfacing: a failure here costs a push,
        // not a booking, and the next sign-in writes it again.
        Firestore.firestore().document("users/\(appUid)")
            .updateData(["fcmToken": token]) { _ in }
    }
}

extension PushService: MessagingDelegate {
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken token: String?) {
        guard let token else { return }
        Task { @MainActor in self.write(token: token) }
    }
}

extension PushService: UNUserNotificationCenterDelegate {
    /// Shown even while the app is open. She may be looking at the salon list
    /// when the salon confirms; suppressing it there would mean the one moment
    /// she is definitely holding the phone is the one moment she is not told.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }
}
