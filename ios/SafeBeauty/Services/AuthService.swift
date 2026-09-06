import Foundation
import FirebaseAuth
import FirebaseCrashlytics
import FirebaseFirestore
import SafeBeautyCore

/// Sign-in and registration, which are two very different shapes and should be.
///
/// **Registration is one server call.** `registerAccount` creates the
/// credential, the profile and the salon in a single invocation and rolls back
/// on the server if any of it fails. iOS does not get its own version of that
/// sequence, and deliberately so: the Android app used to do it in three calls
/// from the handset, the third being a compensating delete that needed the
/// connection whose loss was the reason it was running, and 99 of the first 117
/// accounts ended up with a credential and no profile. There is nothing to
/// re-litigate on a second platform.
///
/// **Sign-in is two.** `authenticateWithPassword` resolves the account by phone
/// and returns its salt only after the password hash matches; the device then
/// derives the auth password from that salt and signs into Firebase Auth. The
/// stored hash never leaves the server, and the salt is not handed out to
/// someone who failed the check.
@MainActor
@Observable
final class AuthService {
    static let shared = AuthService()

    private(set) var session: Session?
    private(set) var isWorking = false

    struct Session: Equatable, Sendable, Codable {
        let uid: String            // the app-level uid, not the Firebase Auth one
        let name: String
        let role: String
        let status: String
        let kycStatus: String
    }

    enum AuthError: LocalizedError, Equatable {
        case wrongPhoneOrPassword
        case phoneTaken
        case emailTaken
        case rateLimited(String)
        case server(String)
        /// The account exists on the server and only the device-side sign-in
        /// failed. Distinct from `.server` because the recovery is different
        /// and the difference matters: she must NOT register again — that
        /// would come back as `.phoneTaken` and read as a contradiction — she
        /// signs in with the credentials she just chose.
        case registeredButNotSignedIn

        var errorDescription: String? {
            switch self {
            case .wrongPhoneOrPassword: "wrongPhoneOrPassword"
            case .phoneTaken: "phoneTaken"
            case .emailTaken: "emailTaken"
            case .registeredButNotSignedIn: "registeredButNotSignedIn"
            case .rateLimited(let m), .server(let m): m
            }
        }
    }

    private init() { restore() }

    // MARK: Persistence

    /// Firebase Auth persists its own session across launches; this did not,
    /// so the app came back authenticated and showed the sign-in screen anyway.
    /// Found by relaunching the app rather than by reading it.
    ///
    /// The app-level identity cannot be re-derived on the client: uid_map is
    /// `read: if false` and the users collection is not client-listable, both
    /// deliberately. So the session is stored here and validated on restore
    /// against the credential Firebase kept — if the two disagree, the stored
    /// one is discarded rather than trusted.
    private static let storeKey = "safebeauty.session"

    private func restore() {
        guard let email = Auth.auth().currentUser?.email?.lowercased(),
              let raw = UserDefaults.standard.data(forKey: Self.storeKey),
              let stored = try? JSONDecoder().decode(StoredSession.self, from: raw)
        else { return }

        // A stored session belonging to a different account than the one
        // Firebase is holding is stale — a previous user of this phone, or a
        // sign-out that only half happened. Signing her in as someone else
        // would be the worst possible outcome on a shared device.
        guard stored.firebaseEmail.lowercased() == email else {
            UserDefaults.standard.removeObject(forKey: Self.storeKey)
            return
        }
        session = stored.session
        PushService.shared.bind(uid: stored.session.uid)
    }

    private func persist(_ session: Session, firebaseEmail: String) {
        let stored = StoredSession(session: session, firebaseEmail: firebaseEmail)
        if let data = try? JSONEncoder().encode(stored) {
            UserDefaults.standard.set(data, forKey: Self.storeKey)
        }
    }

    /// Deliberately not the Keychain. Nothing here is a credential — it is a
    /// name, a role and an id the server re-authorises on every call. The
    /// password material never touches disk at all.
    private struct StoredSession: Codable {
        let session: Session
        let firebaseEmail: String
    }

    // MARK: - Sign in

    func signIn(phone rawPhone: String, password: String) async throws {
        isWorking = true
        defer { isWorking = false }

        // Normalised the same way Android and the server normalise it. An
        // un-normalised number resolves to no account, and the error the user
        // would see is "wrong password".
        let phone = PhoneUtils.normalizeForLogin(rawPhone)

        let result: JSON
        do {
            result = try await Callables.call(
                "authenticateWithPassword", ["phone": .string(phone), "password": .string(password)])
        } catch let e as Callables.CallableError {
            if case .rateLimited(let m) = e { throw AuthError.rateLimited(m) }
            throw AuthError.server(e.localizedDescription)
        }

        // "INVALID" covers both a number with no account and a wrong password,
        // deliberately: telling them apart tells an attacker which phone
        // numbers are registered.
        guard result["mode"]?.stringValue == "REAL",
              let salt = result["salt"]?.stringValue,
              let firebaseEmail = result["firebaseEmail"]?.stringValue,
              !salt.isEmpty, !firebaseEmail.isEmpty
        else { throw AuthError.wrongPhoneOrPassword }

        // A suspended account signs in. It used to throw here, two lines above a
        // comment saying the opposite — and the message it threw told her to
        // contact support, which lives inside the app it was refusing her. The
        // server permits the sign-in for exactly this reason and Android has
        // never blocked it; the callables refuse what she may DO.

        let authPassword = try PinHasher.deriveAuthPassword(password, saltBase64: salt)
        try await Auth.auth().signIn(withEmail: firebaseEmail, password: authPassword)

        let uid = result["uid"]?.stringValue ?? ""
        // The bridge between the Firebase Auth uid and the app-level uid. The
        // rules resolve me() through it, so a session without it can read
        // almost nothing — worth a retry, not worth failing the sign-in over,
        // since the server repopulates it on the next callable anyway.
        _ = try? await Callables.call("syncUidMap", ["appUid": .string(uid)])

        let newSession = Session(
            uid: uid,
            name: result["name"]?.stringValue ?? "",
            role: result["role"]?.stringValue ?? "CUSTOMER",
            status: result["status"]?.stringValue ?? "",
            kycStatus: result["kycStatus"]?.stringValue ?? "NONE"
        )
        session = newSession
        persist(newSession, firebaseEmail: firebaseEmail)
        // Where to send her notifications, and in which language. Asked for
        // here rather than at launch: a permission sheet makes sense once she
        // has an account that things can happen to.
        PushService.shared.bind(uid: uid)
        Task { await PushService.shared.requestAuthorisation() }
    }

    // MARK: - Change password

    /// Rotates her password through the server, which owns the ordering.
    ///
    /// iOS had no path to this at all — the callable was built, deployed, and
    /// had no caller on this platform, so a woman using the iPhone app could
    /// never change her password.
    ///
    /// Reauthentication is the security boundary, not `currentPinHash`:
    /// firestore.rules lets her read her own document including the stored
    /// hash, so sending that back proves only that she is signed in. The
    /// forced token refresh is not optional either — reauthenticating updates
    /// auth_time on the account, but the callable SDK sends the cached ID
    /// token, and changePassword refuses a stale one.
    func changePassword(current: String, new newPassword: String) async throws {
        isWorking = true
        defer { isWorking = false }

        guard let uid = session?.uid, !uid.isEmpty else { throw AuthError.server("no session") }
        let snap = try await Firestore.firestore().document("users/\(uid)").getDocument()
        guard let d = snap.data(),
              let salt = d["salt"] as? String, !salt.isEmpty,
              let firebaseEmail = d["firebaseEmail"] as? String, !firebaseEmail.isEmpty
        else { throw AuthError.server("account has no password set") }

        let currentPinHash  = try PinHasher.hash(current, saltBase64: salt)
        let oldAuthPassword = try PinHasher.deriveAuthPassword(current, saltBase64: salt)
        let newSalt         = PinHasher.generateSalt()
        let newPinHash      = try PinHasher.hash(newPassword, saltBase64: newSalt)
        let newAuthPassword = try PinHasher.deriveAuthPassword(newPassword, saltBase64: newSalt)

        do {
            try await Auth.auth().signIn(withEmail: firebaseEmail, password: oldAuthPassword)
            _ = try await Auth.auth().currentUser?.getIDTokenResult(forcingRefresh: true)
        } catch {
            // Wrong current password, told apart from a server failure so she
            // is not asked to check her internet over a typo.
            throw AuthError.wrongPhoneOrPassword
        }

        do {
            _ = try await Callables.call("changePassword", [
                "currentPinHash":  .string(currentPinHash),
                "newSalt":         .string(newSalt),
                "newPinHash":      .string(newPinHash),
                "newAuthPassword": .string(newAuthPassword),
            ])
        } catch let e as Callables.CallableError {
            if case .rateLimited(let m) = e { throw AuthError.rateLimited(m) }
            throw AuthError.server(e.localizedDescription)
        }

        // The credential on this device is the old one now. Refreshed here so
        // she stays signed in rather than being dropped at some later moment
        // when the token behind her session stops matching the password.
        _ = try? await Auth.auth().signIn(withEmail: firebaseEmail, password: newAuthPassword)
    }

    /// Her display name. A direct write, as on Android — `name` is not frozen
    /// in the rules and there is no callable for it.
    func updateName(_ raw: String) async throws {
        let name = raw.trimmingCharacters(in: .whitespaces)
        guard let uid = session?.uid, !uid.isEmpty, !name.isEmpty else { return }
        try await Firestore.firestore().document("users/\(uid)").updateData(["name": name])
        if let s = session {
            let updated = Session(uid: s.uid, name: name, role: s.role,
                                  status: s.status, kycStatus: s.kycStatus)
            session = updated
            if let email = Auth.auth().currentUser?.email { persist(updated, firebaseEmail: email) }
        }
    }

    // MARK: - Forgot password

    /// What happened, because the three outcomes need three different sentences.
    enum ResetOutcome { case sent(String), noEmail, notFound }

    /// Mirrors Android's ForgotPinViewModel exactly, including its refusal.
    ///
    /// The reset link lands on the hosted /reset page, which re-derives the
    /// PBKDF2 material and syncs pinHash + salt — so there is no SetNewPin
    /// screen to build here, and deliberately so: a second implementation of
    /// that derivation is a second place for it to drift.
    func sendPasswordReset(phone rawPhone: String) async throws -> ResetOutcome {
        isWorking = true
        defer { isWorking = false }

        let phone = PhoneUtils.normalizeForLogin(rawPhone)
        let result: JSON
        do {
            result = try await Callables.call("lookupAccountByPhone", ["phone": .string(phone)])
        } catch let e as Callables.CallableError {
            if case .rateLimited(let m) = e { throw AuthError.rateLimited(m) }
            throw AuthError.server(e.localizedDescription)
        }

        guard result["found"]?.boolValue == true else { return .notFound }
        let firebaseEmail = result["firebaseEmail"]?.stringValue ?? ""
        let email = result["email"]?.stringValue ?? ""

        // An account registered without an email has no address to send to.
        // Its Firebase credential is a synthetic @sb.app one, and mailing that
        // reaches nobody — she needs support, not a link.
        guard !email.isEmpty, !firebaseEmail.hasSuffix("@sb.app") else { return .noEmail }

        try await Auth.auth().sendPasswordReset(withEmail: firebaseEmail)
        return .sent(email)
    }

    // MARK: - Register

    /// One call. The password itself never travels — PinHasher runs here and
    /// what goes over the wire is the salt, the stored hash and the derived
    /// auth password, exactly the three values Android sends.
    func register(
        name: String, phone rawPhone: String, email: String,
        password: String, isProvider: Bool,
        salonName: String = "", district: String = "", services: [String] = [],
        referredBy: String = ""
    ) async throws {
        isWorking = true
        defer { isWorking = false }

        let salt = PinHasher.generateSalt()
        let pinHash = try PinHasher.hash(password, saltBase64: salt)
        let authPassword = try PinHasher.deriveAuthPassword(password, saltBase64: salt)
        let phone = PhoneUtils.normalizeAfghan(rawPhone)

        let payload: [String: JSON] = [
            "name": .string(name.trimmingCharacters(in: .whitespaces)),
            "phone": .string(phone),
            "email": .string(email.trimmingCharacters(in: .whitespaces)),
            "role": .string(isProvider ? "PROVIDER" : "CUSTOMER"),
            "salt": .string(salt),
            "pinHash": .string(pinHash),
            "authPassword": .string(authPassword),
            "referredBy": .string(referredBy.trimmingCharacters(in: .whitespaces).uppercased()),
            "salonName": .string(isProvider ? salonName.trimmingCharacters(in: .whitespaces) : ""),
            "district": .string(isProvider ? district.trimmingCharacters(in: .whitespaces) : ""),
            "services": .strings(isProvider ? services : []),
        ]

        let result: JSON
        do {
            result = try await Callables.call("registerAccount", payload)
        } catch let e as Callables.CallableError {
            switch e {
            case .alreadyExists(let field):
                // Which field collided is the whole point. Someone whose email
                // is taken but whose phone is free will otherwise go and change
                // the number that was fine.
                throw field == "email" ? AuthError.emailTaken : AuthError.phoneTaken
            case .rateLimited(let m): throw AuthError.rateLimited(m)
            default: throw AuthError.server(e.localizedDescription)
            }
        }

        guard let firebaseEmail = result["firebaseEmail"]?.stringValue, !firebaseEmail.isEmpty else {
            throw AuthError.server("registerAccount returned no firebaseEmail")
        }

        // Signing in is the only step left on the device, and the only one that
        // is safe to fail: the account is already complete on the server, so a
        // dropped connection here means she opens the app and logs in normally
        // rather than being stranded half-registered.
        //
        // Safe to fail, but NOT safe to ignore. This was `try?`, and the two
        // lines below then ran anyway — setting `session` and persisting it
        // while `Auth.auth().currentUser` was nil. That is the worst of both:
        // the app shows her the signed-in UI, every rule check fails
        // `isSignedIn()`, every callable is unauthenticated, so nothing loads
        // and there is no visible reason why. `restore()` guards the persisted
        // copy against exactly this mismatch on the next launch; the live
        // session had no such guard. She is told instead.
        do {
            try await Auth.auth().signIn(withEmail: firebaseEmail, password: authPassword)
        } catch {
            // Recorded, not merely thrown. Android reports this same failure as
            // `register:sign-in`; without it, the one path that strands a brand
            // new account is the one path with no telemetry.
            Crashlytics.crashlytics().record(error: error)
            // And leave the device holding no credential. She is about to be
            // handed to the sign-in screen, where a stale Firebase user from
            // some earlier account would otherwise still be live behind it.
            try? Auth.auth().signOut()
            throw AuthError.registeredButNotSignedIn
        }

        let uid = result["uid"]?.stringValue ?? ""

        let newSession = Session(
            uid: uid,
            name: name, role: isProvider ? "PROVIDER" : "CUSTOMER",
            status: isProvider ? "PENDING" : "APPROVED", kycStatus: "NONE"
        )
        session = newSession
        persist(newSession, firebaseEmail: firebaseEmail)
        PushService.shared.bind(uid: uid)
        Task { await PushService.shared.requestAuthorisation() }

        // The bridge, written LAST on purpose. Her account exists and she is
        // signed in; nothing below should be able to hold the registration
        // sheet open — and the sheet's escape routes are deliberately disabled
        // while this call is in flight, so a callable sitting on its 70-second
        // default timeout would trap her behind a call whose result she is not
        // waiting for.
        await syncBridge(appUid: uid)
    }

    /// Set when a `syncUidMap` write has not been confirmed for this session.
    ///
    /// Registration is the one path with no later login to repair the bridge —
    /// that is the whole reason the server's own "login will retry" never
    /// happened here — so the retry has to live on this side. `refresh()`
    /// re-attempts it on the next foreground.
    private var bridgePending = false

    /// Writes `uid_map/{authUid} -> appUid`, which the rules resolve `me()`
    /// through. A session without it can read almost nothing, and the failure
    /// is indistinguishable from an empty account: every personal read is
    /// denied and no screen says why. Not fatal enough to undo a completed
    /// registration, but not something to discard either.
    private func syncBridge(appUid: String) async {
        guard !appUid.isEmpty else { return }
        do {
            _ = try await Callables.call("syncUidMap", ["appUid": .string(appUid)])
            bridgePending = false
        } catch {
            Crashlytics.crashlytics().record(error: error)
            bridgePending = true
        }
    }

    func signOut() {
        // Before the session goes: a token left behind would send the next
        // person to hold this phone somebody else's bookings.
        PushService.shared.unbind()
        bridgePending = false
        try? Auth.auth().signOut()
        // Cleared before the in-memory copy, so a crash between the two lines
        // cannot leave a session on disk that outlives the credential.
        UserDefaults.standard.removeObject(forKey: Self.storeKey)
        session = nil
    }

    /// Re-read her own profile after something the server changed.
    ///
    /// kycStatus flips when an admin reviews her documents, and status flips
    /// when a provider is approved. Without this the app keeps telling an
    /// already-verified customer to verify herself until she signs out and back
    /// in — which is a thing she has no reason to think of doing.
    ///
    /// Read straight from Firestore rather than through a callable, because
    /// `allow get: if isSignedIn() && ownsDoc(uid)` already permits exactly
    /// this: her own document and nobody else's.
    func refresh() async {
        guard let current = session, !current.uid.isEmpty else { return }

        // The same invariant restore() enforces at launch, enforced again while
        // the app is running — this is the only code that runs on every
        // foreground. Firebase can drop currentUser mid-session: an admin
        // deletes the account, or the refresh token is revoked. Without this,
        // `session` stays set, the app keeps rendering the signed-in UI, and
        // every read fails isSignedIn() with nothing on screen to say why.
        // Sending her to sign-in is the recoverable state; a blank app is not.
        guard Auth.auth().currentUser != nil else { signOut(); return }

        // The retry the server delegated to a login that registration never
        // performs. Cheap when it is not needed, and this is the only code that
        // runs again on its own.
        if bridgePending { await syncBridge(appUid: current.uid) }

        guard let snapshot = try? await Firestore.firestore()
                .document("users/\(current.uid)").getDocument(),
              let data = snapshot.data()
        else { return }

        let updated = Session(
            uid: current.uid,
            name: data["name"] as? String ?? current.name,
            role: data["role"] as? String ?? current.role,
            status: data["status"] as? String ?? current.status,
            kycStatus: data["kycStatus"] as? String ?? current.kycStatus
        )
        guard updated != current else { return }

        // Re-established after the await, not only before it. The read above
        // suspends, and a sign-out that lands during it would otherwise be
        // undone by the line below — restoring the signed-in UI for someone who
        // no longer holds a credential, which is precisely the state the guard
        // at the top of this function exists to prevent.
        guard Auth.auth().currentUser != nil, session?.uid == current.uid else { return }
        session = updated
        if let email = Auth.auth().currentUser?.email {
            persist(updated, firebaseEmail: email)
        }
    }
}
