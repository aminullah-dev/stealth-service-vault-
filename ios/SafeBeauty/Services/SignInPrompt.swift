import SwiftUI

/// Lets any screen she is browsing without an account ask for the sign-in
/// sheet, without each one owning its own `@State` and its own `.sheet`.
///
/// Booking, favouriting a salon, messaging one, and the notifications bell all
/// need a real account, and each used to be reachable only because the app
/// forced sign-in before showing anything at all. Now that browsing works
/// without one (see `AuthService.ensureBrowsingSession`), every one of those
/// four calls `request()` instead of duplicating "present SignInView" four
/// separate times with four separate chances to get the sheet binding wrong.
@MainActor
@Observable
final class SignInPrompt {
    var isPresented = false
    func request() { isPresented = true }
}
