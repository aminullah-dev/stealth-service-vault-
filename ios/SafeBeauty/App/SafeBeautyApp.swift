import SwiftUI
import FirebaseCore

@main
struct SafeBeautyApp: App {
    // Firebase is configured before any view exists, in an initialiser rather
    // than in .onAppear: a Firestore listener attached by a view that renders
    // first would fail against an unconfigured app, and the failure looks like
    // an empty screen rather than an error.
    init() {
        FirebaseApp.configure()
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
