import Foundation
import Testing
@testable import SafeBeauty

/// The first tests that run against the app target rather than the package
/// underneath it.
///
/// `SafeBeautyCore` holds everything that could be lifted out of the app, and
/// it is tested by `swift test` in a second. What is left behind in the app
/// target is not therefore unimportant — it is the part that could not be
/// lifted, usually because it touches `L`, and `L` is where a failure turns
/// into the sentence a woman actually reads. `message(for:)` is that sentence
/// for every authentication failure in the app, on four screens.
///
/// The switch it wraps is exhaustive with no `default`, so the compiler already
/// refuses a *missing* case. What the compiler cannot see is a case wired to
/// the *wrong* message — which compiles, ships, and is the defect this file
/// exists for. It has happened: `.keychainError` was mapped to
/// `credentialsOutOfSync`, so a phone that could not write to its own keychain
/// told her "Your password is correct but sign-in failed. Please contact
/// support to have it reset." Nothing about the password was wrong, and support
/// cannot repair a keychain. Every assertion below is pinned to a failure that
/// was real.
///
/// Serialized because `AppLanguage.current` is a single UserDefaults key and
/// these tests write it; run in parallel they would read each other's language.
@Suite("Auth error messages", .serialized)
@MainActor
struct SignInMessageTests {

    /// `AppLanguage.storageKey` is private, so the key is repeated here. If it
    /// is ever renamed this restore silently stops restoring — it will not fail
    /// a test, it will only leave the simulator's defaults set to English.
    private static let storageKey = "safebeauty.language"

    /// Every message is language-dependent, so no assertion here means anything
    /// without pinning the language first. Restores the previous value: the
    /// bundle is hosted by the real app and this is the real app's UserDefaults.
    private func inLanguage<T>(_ lang: AppLanguage, _ body: () throws -> T) rethrows -> T {
        let saved = UserDefaults.standard.string(forKey: Self.storageKey)
        defer {
            if let saved { UserDefaults.standard.set(saved, forKey: Self.storageKey) }
            else { UserDefaults.standard.removeObject(forKey: Self.storageKey) }
        }
        AppLanguage.current = lang
        return try body()
    }

    private func message(_ e: AuthService.AuthError, in lang: AppLanguage) -> String {
        inLanguage(lang) { SignInView.message(for: e) }
    }

    /// The nine failures, as one list, so a case added without a thought about
    /// what it should say has somewhere to be noticed.
    private static let everyCase: [AuthService.AuthError] = [
        .wrongPhoneOrPassword,
        .phoneTaken,
        .emailTaken,
        .registeredButNotSignedIn,
        .rateLimited("rate limit payload"),
        .server("server payload"),
        .credentialsOutOfSync,
        .deviceKeychainUnavailable,
        .unexpected(17995),
    ]

    // MARK: The regression this file was written for

    @Test("a keychain the phone cannot write is not a password to reset")
    func keychainIsNotACredentialProblem() {
        // FIRAuthErrorDomain 17995. The server verified the password and the
        // sign-in request succeeded; only the device-side store failed. The
        // message this used to give sent her to have the one thing that is
        // definitely correct replaced, for a fault support cannot reach.
        for lang in AppLanguage.allCases {
            let keychain = message(.deviceKeychainUnavailable, in: lang)
            #expect(keychain != message(.credentialsOutOfSync, in: lang),
                    "\(lang.rawValue): a keychain fault reads as a credentials problem again")
            #expect(keychain != message(.wrongPhoneOrPassword, in: lang),
                    "\(lang.rawValue): a keychain fault reads as a wrong password")
            #expect(keychain != message(.server("x"), in: lang),
                    "\(lang.rawValue): a keychain fault reads as check-your-internet")
        }
    }

    @Test("the keychain message tells her to restart the phone")
    func keychainSaysRestart() {
        // The one recovery that works on a real phone, and the reason this case
        // is distinct from `.credentialsOutOfSync` at all. Asserted on the
        // English text because that is the copy a reviewer can check; the other
        // two are pinned by `errDeviceKeychain` carrying all three.
        let en = message(.deviceKeychainUnavailable, in: .english).lowercased()
        #expect(en.contains("restart"))
        #expect(en.contains("device") || en.contains("phone"))
        #expect(!en.contains("support"),
                "the keychain message sends her to support again")
        #expect(!en.contains("reset"),
                "the keychain message offers a password reset again")
    }

    // MARK: The server's own text is not a user-facing message

    @Test("a rate-limit payload never reaches the screen")
    func rateLimitPayloadNotShown() {
        // `.rateLimited` and `.server` both carry the server's string, which is
        // written in English for a log. A Dari user read one of those as her
        // error message once already; the mapping discards them on purpose and
        // this is what says so.
        let payload = "RATE_LIMIT_EXCEEDED for ip 10.0.0.1"
        for lang in AppLanguage.allCases {
            let shown = message(.rateLimited(payload), in: lang)
            #expect(!shown.contains(payload))
            #expect(!shown.contains("10.0.0.1"))
        }
    }

    @Test("a server payload never reaches the screen")
    func serverPayloadNotShown() {
        let payload = "FIRESTORE_INTERNAL: deadline exceeded at shard 4"
        for lang in AppLanguage.allCases {
            let shown = message(.server(payload), in: lang)
            #expect(!shown.contains(payload))
            #expect(!shown.contains("shard"))
        }
    }

    // MARK: The unexpected code is the only thing support can act on

    @Test("an unexpected failure names its number, in Latin digits")
    func unexpectedNamesCode() {
        // The number is the entire point of the case: it is what support looks
        // up. Latin digits in all three languages on purpose — a Persian-digit
        // ۱۷۹۹۵ is not searchable and does not match the console.
        for lang in AppLanguage.allCases {
            let shown = message(.unexpected(17995), in: lang)
            #expect(shown.contains("17995"),
                    "\(lang.rawValue): the code is missing or not in Latin digits")
        }
        // A different code produces a different sentence — i.e. the number is
        // interpolated rather than baked into the copy.
        #expect(message(.unexpected(17995), in: .dari)
                != message(.unexpected(17020), in: .dari))
    }

    // MARK: Every failure says something different, because every fix differs

    @Test("no two failures give the same message")
    func allCasesDistinct() {
        // Each of these has a different recovery: sign in again, sign in
        // instead of registering, wait, restart the phone, call support, check
        // the connection. Two of them sharing a sentence means one of them is
        // sending her to do the wrong thing.
        for lang in AppLanguage.allCases {
            let shown = Self.everyCase.map { message($0, in: lang) }
            #expect(Set(shown).count == shown.count,
                    "\(lang.rawValue): two AuthError cases produce the same message")
        }
    }

    @Test("every failure has a non-empty message in all three languages")
    func allCasesTranslated() {
        for lang in AppLanguage.allCases {
            for e in Self.everyCase {
                #expect(!message(e, in: lang).isEmpty,
                        "\(lang.rawValue): \(e) has no message")
            }
        }
    }

    @Test("Dari and Pashto are not the English string")
    func notEnglishFallback() {
        // `L` requires all three at the initialiser, so a *missing* translation
        // cannot compile. What it cannot catch is an English sentence pasted
        // into the fa or ps slot, which is what this looks for.
        for e in Self.everyCase {
            let en = message(e, in: .english)
            #expect(message(e, in: .dari) != en, "\(e) is English in Dari")
            #expect(message(e, in: .pashto) != en, "\(e) is English in Pashto")
        }
    }

    // MARK: The message that stops her registering twice

    @Test("an existing account tells her to sign in, not that something failed")
    func registeredButNotSignedIn() {
        // If this reads as a failure she registers again and gets `.phoneTaken`,
        // which reads as a contradiction. Both exits closed on an account that
        // exists.
        let en = message(.registeredButNotSignedIn, in: .english).lowercased()
        #expect(en.contains("sign in"))
        #expect(en != message(.phoneTaken, in: .english).lowercased())
    }
}
