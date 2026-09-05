import Foundation
import FirebaseAuth
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
        case accountSuspended(reason: String)
        case phoneTaken
        case emailTaken
        case rateLimited(String)
        case server(String)

        var errorDescription: String? {
            switch self {
            case .wrongPhoneOrPassword: "wrongPhoneOrPassword"
            case .accountSuspended(let r): "suspended: \(r)"
            case .phoneTaken: "phoneTaken"
            case .emailTaken: "emailTaken"
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

        if result["status"]?.stringValue == "SUSPENDED" {
            // A suspended account can still sign in on purpose — she must be
            // able to read her history and reach support. The app decides what
            // she may DO; it does not pretend the account is gone.
            throw AuthError.accountSuspended(reason: result["rejectionReason"]?.stringValue ?? "")
        }

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
        try? await Auth.auth().signIn(withEmail: firebaseEmail, password: authPassword)

        let newSession = Session(
            uid: result["uid"]?.stringValue ?? "",
            name: name, role: isProvider ? "PROVIDER" : "CUSTOMER",
            status: isProvider ? "PENDING" : "APPROVED", kycStatus: "NONE"
        )
        session = newSession
        persist(newSession, firebaseEmail: firebaseEmail)
    }

    func signOut() {
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
        session = updated
        if let email = Auth.auth().currentUser?.email {
            persist(updated, firebaseEmail: email)
        }
    }
}
