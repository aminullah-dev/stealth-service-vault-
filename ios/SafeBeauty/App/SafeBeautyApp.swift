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
            RootView()
                // Dari is the primary language and it is right-to-left. Setting
                // it here rather than per-view means a screen added later is
                // RTL by default instead of by remembering — which is the
                // failure mode the Android admin console actually hit, where a
                // logical padding and a physical offset disagreed and the
                // buttons landed on top of the text.
                .environment(\.layoutDirection, AppLanguage.current.layoutDirection)
                .environment(\.locale, AppLanguage.current.locale)
        }
    }
}
