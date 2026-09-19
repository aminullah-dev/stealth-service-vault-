import Foundation
import LocalAuthentication
import Security

/// Face ID / Touch ID sign-in, which Android has had and iOS had not.
///
/// **The design, and why it is not a boolean.** The phone number and password
/// are written to the keychain behind an access control created with
/// `.biometryCurrentSet`, and the read itself is what triggers the prompt — the
/// keychain will not hand the bytes back until the Secure Enclave has just
/// verified a face or a finger. Nothing in this file decides whether the user
/// authenticated; if it did, an attacker who could flip one boolean would be
/// signed in. This mirrors what `BiometricVault.kt` does with a Keystore key
/// bound to a CryptoObject, and for the same stated reason.
///
/// `.biometryCurrentSet` is the counterpart of Android's
/// `setInvalidatedByBiometricEnrollment(true)`: enrolling a new face or finger
/// destroys the item, so someone who takes the phone and adds their own
/// biometric gets nothing. That matters more here than in most apps — the
/// threat this product is built around is a phone in the wrong hands.
///
/// `kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` keeps it off iCloud and off
/// any restored backup, and refuses to exist at all on a phone with no passcode.
enum BiometricVault {
    private static let service = "com.safebeauty.app.biometric"
    private static let account = "credential"

    /// What this phone can actually do, as its own name — Android says
    /// "fingerprint" because that is all it offers; an iPhone that says
    /// "fingerprint" while asking for a face is telling the user something
    /// false about her own device.
    static var biometryName: String? {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics,
                                        error: nil) else { return nil }
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return nil
        }
    }

    static var isAvailable: Bool { biometryName != nil }

    /// True once she has stored a credential. Read without prompting: this only
    /// asks whether the item exists, never for its contents.
    static var isEnabled: Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecUseAuthenticationUI as String: kSecUseAuthenticationUIFail,
            kSecReturnData as String: false,
        ]
        let status = SecItemCopyMatching(query as CFDictionary, nil)
        // `interactionNotAllowed` means the item is there and would have asked.
        // That is the answer, not a failure.
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }

    enum VaultError: Error { case unavailable, cancelled, failed }

    /// Stores the credential behind the biometric. Replaces any previous one.
    static func enable(phone: String, password: String) throws {
        guard isAvailable else { throw VaultError.unavailable }
        var acError: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(
            nil,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            .biometryCurrentSet,
            &acError
        ) else { throw VaultError.failed }

        disable()

        // "phone\npassword" — a phone number never contains a newline, which is
        // the same split Android relies on, so the two platforms agree about
        // what is stored even though nothing shares it between them.
        let secret = Data("\(phone)\n\(password)".utf8)
        let add: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessControl as String: access,
            kSecValueData as String: secret,
        ]
        guard SecItemAdd(add as CFDictionary, nil) == errSecSuccess else {
            throw VaultError.failed
        }
    }

    /// Asks for the face or finger and returns the credential.
    ///
    /// Off the main actor because the keychain blocks this thread while the
    /// system prompt is up.
    static func unlock(reason: String, cancelTitle: String) async throws -> (phone: String, password: String) {
        let context = LAContext()
        context.localizedReason = reason
        context.localizedCancelTitle = cancelTitle
        // No "enter password instead" button inside the system sheet: the sign
        // in form is right behind it and already says that.
        context.localizedFallbackTitle = ""

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecUseAuthenticationContext as String: context,
        ]

        let data: Data = try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .userInitiated).async {
                var item: CFTypeRef?
                let status = SecItemCopyMatching(query as CFDictionary, &item)
                switch status {
                case errSecSuccess:
                    if let data = item as? Data {
                        continuation.resume(returning: data)
                    } else {
                        continuation.resume(throwing: VaultError.failed)
                    }
                case errSecUserCanceled, errSecAuthFailed:
                    continuation.resume(throwing: VaultError.cancelled)
                default:
                    // errSecItemNotFound after a biometric change: the item is
                    // gone because the enrolment changed, which is the whole
                    // point of .biometryCurrentSet. Clean up so the button
                    // stops being offered.
                    disable()
                    continuation.resume(throwing: VaultError.failed)
                }
            }
        }

        let secret = String(decoding: data, as: UTF8.self)
        guard let split = secret.firstIndex(of: "\n") else { throw VaultError.failed }
        return (String(secret[secret.startIndex..<split]),
                String(secret[secret.index(after: split)...]))
    }

    /// Forgets the credential — signing out, turning the setting off, or a
    /// biometric set that no longer matches.
    static func disable() {
        SecItemDelete([
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ] as CFDictionary)
    }
}
