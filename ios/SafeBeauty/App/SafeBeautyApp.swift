import SwiftUI
import FirebaseCore
import FirebaseMessaging
import UIKit
#if DEBUG
import FirebaseAuth
import FirebaseFirestore
import FirebaseFunctions
#endif

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
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-useFirestoreEmulator") {
            ScreenshotBackend.configure()
        } else {
            FirebaseApp.configure()
        }
        #else
        FirebaseApp.configure()
        #endif
        PushService.shared.start()
        // Touched before any view exists so the UIKit layout direction is set
        // first: UIView.appearance is read at view creation, and a menu built
        // before it would open the wrong way round. See LanguageStore.
        _ = LanguageStore.shared
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

#if DEBUG
/// App Store screenshots: the real app showing the invented demo world
/// (marketing/demo/), served by a local Firestore emulator. DEBUG builds only,
/// and only with `-useFirestoreEmulator`. How to run it:
/// marketing/app-store-ios/README.md.
///
/// Why a made-up `demo-` project rather than the production plist plus
/// `useEmulator` on Firestore alone: `ensureBrowsingSession` signs in
/// anonymously at launch, and with the production plist that creates a real
/// anonymous user in production Auth on every launch — the simulator then fails
/// to keep it (securityd -34018), but the server-side account already exists.
/// Crashlytics and Installations would report to production too. With options
/// that name no real project, nothing in this mode can reach `safebeauty`,
/// whether or not the redirects below are complete.
private enum ScreenshotBackend {
    static func configure() {
        let options = FirebaseOptions(googleAppID: "1:000000000000:ios:0000000000000000",
                                      gcmSenderID: "000000000000")
        options.projectID = "demo-screens"   // the emulator's project id
        // Shaped like a key (39 characters, leading "A") because Installations
        // throws at launch on anything else; it belongs to nobody.
        options.apiKey = "A" + String(repeating: "0", count: 38)
        // Storage.storage() traps on a nil bucket.
        options.storageBucket = "demo-screens.appspot.com"
        FirebaseApp.configure(options: options)

        let settings = Firestore.firestore().settings
        settings.host = "127.0.0.1:8080"
        settings.isSSLEnabled = false
        settings.cacheSettings = MemoryCacheSettings()   // nothing outlives the launch
        Firestore.firestore().settings = settings

        // Nothing listens on these ports: the anonymous sign-in and any callable
        // fail on this Mac instead of leaving it.
        Auth.auth().useEmulator(withHost: "127.0.0.1", port: 9099)
        Functions.functions(region: "us-central1").useEmulator(withHost: "127.0.0.1", port: 5001)
    }
}
#endif
