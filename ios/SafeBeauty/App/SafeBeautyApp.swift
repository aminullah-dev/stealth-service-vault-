import SwiftUI
import FirebaseCore
import FirebaseMessaging
import UIKit

/// SwiftUI has no hook for the APNs device token, so this exists to hand it to
/// Firebase Messaging. Without that hand-off the FCM token never resolves and
/// every push is dropped silently — which is what was happening, since nothing
/// registered at all.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ app: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        Messaging.messaging().apnsToken = token
    }

    func application(_ app: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Simulators without a paired push environment land here. Not fatal:
        // the app works, it just cannot be told anything.
        print("push registration failed: \(error.localizedDescription)")
    }
}

@main
struct SafeBeautyApp: App {
    // Firebase is configured before any view exists, in an initialiser rather
    // than in .onAppear: a Firestore listener attached by a view that renders
    // first would fail against an unconfigured app, and the failure looks like
    // an empty screen rather than an error.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        FirebaseApp.configure()
        PushService.shared.start()
    }

    var body: some Scene {
        WindowGroup {
            // RootView owns direction and locale so a language change
            // re-renders everything under it. Setting them here as well would
            // fix them at launch and leave the picker cosmetic.
            RootView()
        }
    }
}
