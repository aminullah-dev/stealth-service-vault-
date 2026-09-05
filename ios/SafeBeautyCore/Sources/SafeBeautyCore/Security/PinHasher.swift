import Foundation
import CommonCrypto

/// The password derivation, which has to agree with two implementations that
/// already exist and one database full of accounts derived by them.
///
/// `PinHasher.kt` on Android writes every account's `pinHash` and `salt`, and
/// `public/admin/index.html` re-derives the same values in the browser so an
/// admin can sign in. A third implementation that disagrees with either by one
/// byte does not fail loudly — it fails as "wrong password", for every existing
/// user, on a screen that gives them no way to tell the difference.
///
/// The parameters are not free choices. They are read off the accounts already
/// in production:
///
/// - PBKDF2 with HMAC-SHA256
/// - 65,536 iterations
/// - 256-bit output
/// - 16-byte salt, base64 with no line wrapping
/// - the password encoded as **UTF-8**
///
/// That last one is the only genuinely ambiguous parameter, and it is settled
/// rather than assumed: the browser implementation uses
/// `new TextEncoder().encode(...)`, which is UTF-8 by definition, and it signs
/// real admins in against hashes Android wrote. UTF-8 it is — which matters
/// because these passwords can be Dari or Pashto, and a Latin-1 or UTF-16
/// reading of "رمزعبور" produces entirely different bytes.
public enum PinHasher {

    public static let iterations: UInt32 = 65_536
    public static let keyBytes: Int = 32          // 256 bits
    public static let saltBytes: Int = 16

    /// Errors are thrown rather than returned as an empty string.
    ///
    /// A derivation that quietly yields "" would be compared against a stored
    /// hash, fail, and be indistinguishable from a wrong password — the same
    /// silent failure this whole file exists to avoid.
    public enum Failure: Error, Equatable {
        case saltNotBase64
        case derivationFailed(status: Int32)
    }

    /// A fresh random salt, base64-encoded the way the other two write it.
    public static func generateSalt() -> String {
        var bytes = [UInt8](repeating: 0, count: saltBytes)
        // SecRandomCopyBytes is the platform CSPRNG. Its status is checked
        // because a salt that silently came back as sixteen zero bytes would be
        // a real weakness that nothing downstream could detect.
        let status = SecRandomCopyBytes(kSecRandomDefault, saltBytes, &bytes)
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed: \(status)")
        return Data(bytes).base64EncodedString()
    }

    /// PBKDF2(password, salt) → base64, byte-identical to Android and the console.
    public static func hash(_ pin: String, saltBase64: String) throws -> String {
        guard let salt = Data(base64Encoded: saltBase64) else {
            throw Failure.saltNotBase64
        }
        // UTF-8, deliberately. See the note above.
        let password = Array(pin.utf8)
        var derived = [UInt8](repeating: 0, count: keyBytes)

        // An empty salt would make every hash for a given password identical.
        // CCKeyDerivationPBKDF accepts it, so the guard is here.
        let status: Int32 = salt.withUnsafeBytes { saltBuffer in
            let saltPtr = saltBuffer.bindMemory(to: UInt8.self).baseAddress
            return CCKeyDerivationPBKDF(
                CCPBKDFAlgorithm(kCCPBKDF2),
                // The password is passed as bytes reinterpreted as CChar rather
                // than through a String, so no re-encoding can happen between
                // here and the KDF.
                password.withUnsafeBufferPointer { UnsafeRawPointer($0.baseAddress!).assumingMemoryBound(to: CChar.self) },
                password.count,
                saltPtr,
                salt.count,
                CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                iterations,
                &derived,
                keyBytes
            )
        }
        guard status == kCCSuccess else {
            throw Failure.derivationFailed(status: status)
        }
        return Data(derived).base64EncodedString()
    }

    /// The value that is actually the Firebase Auth password.
    ///
    /// Domain-separated from the stored `pinHash` by the "AUTH:" prefix, so the
    /// hash sitting in Firestore is not itself the credential that signs in.
    public static func deriveAuthPassword(_ pin: String, saltBase64: String) throws -> String {
        try hash("AUTH:" + pin, saltBase64: saltBase64)
    }
}
