import Testing
import Foundation
@testable import SafeBeautyCore

/// Cross-implementation parity, which is the only property that matters here.
///
/// These vectors were generated from the derivation already running in
/// production — the same parameters `public/admin/index.html` uses to sign real
/// admins in against hashes `PinHasher.kt` wrote. If Swift disagrees with them,
/// every existing SafeBeauty account fails to log in on iOS and the error the
/// user sees is "wrong password".
///
/// So these are not unit tests of an algorithm. They are the contract with a
/// database of live accounts, and they should fail loudly rather than be
/// updated to match whatever Swift happens to produce.
@Suite("PinHasher parity with Android and the web console")
struct PinHasherTests {

    static let salt = "c2FsdHNhbHRzYWx0c2Fs"
    static let zeroSalt = "AAAAAAAAAAAAAAAAAAAAAA=="

    @Test("hash() matches the reference vectors")
    func hashMatchesReference() throws {
        #expect(try PinHasher.hash("142857", saltBase64: Self.salt)
                == "0pyMrXLNCE9vObESd4aBU6TzZKzkMYoQ2USB7sxcvgU=")
        #expect(try PinHasher.hash("142857", saltBase64: Self.zeroSalt)
                == "B1IKgASI6vzLIzH/opVgCYLWKccrnumoxCMpeo6lb4Q=")
        #expect(try PinHasher.hash("", saltBase64: Self.salt)
                == "Zy0sNHHMZXbjLWjbNkB2xkWD3Ja2OMwhdYr+jyGvWTs=")
    }

    /// The one parameter that was genuinely ambiguous.
    ///
    /// Java's PBEKeySpec takes char[], the browser takes UTF-8 bytes, and Swift
    /// strings are UTF-8 natively — three different starting points that agree
    /// only for ASCII. This product's users type Dari and Pashto, so a password
    /// that is not ASCII is the normal case, not the edge case, and getting the
    /// encoding wrong would work perfectly through every test written in English.
    @Test("a Dari password derives the same bytes — the UTF-8 question, settled")
    func nonAsciiPassword() throws {
        #expect(try PinHasher.hash("رمزعبور", saltBase64: Self.salt)
                == "s5cNEtetfTjnKGGzZwH6g2CFvE4dj2bv32BBNZWQ/5A=")
        #expect(try PinHasher.hash("pässwörd!", saltBase64: Self.salt)
                == "mg4/Pvh1Li7O88n3O+whvuzZAzRnUnrTxl4KVpL/6NU=")
    }

    @Test("deriveAuthPassword() matches, including in Dari")
    func authPasswordMatchesReference() throws {
        #expect(try PinHasher.deriveAuthPassword("142857", saltBase64: Self.salt)
                == "srK0bug0SqF5wWEw8eXgmev4lKFS7HEArhteCPRD9y8=")
        #expect(try PinHasher.deriveAuthPassword("142857", saltBase64: Self.zeroSalt)
                == "vIIU408nBD7q2O62wFjK5Pm3A2FI2rmPaRuwikSI3B4=")
        #expect(try PinHasher.deriveAuthPassword("رمزعبور", saltBase64: Self.salt)
                == "NAc7nrfcDipxAhQhadeaVCu3WRIl5u6KsIeXAuLRRL8=")
    }

    /// The stored hash must not be the credential that signs in.
    @Test("the auth password is domain-separated from the stored hash")
    func authPasswordIsNotTheStoredHash() throws {
        let stored = try PinHasher.hash("142857", saltBase64: Self.salt)
        let auth = try PinHasher.deriveAuthPassword("142857", saltBase64: Self.salt)
        #expect(stored != auth)
        // And it is exactly hash("AUTH:" + pin), not some other construction.
        #expect(try auth == PinHasher.hash("AUTH:142857", saltBase64: Self.salt))
    }

    @Test("a bad salt throws rather than deriving something")
    func badSaltThrows() {
        // "!!!!" is not base64. Returning "" here would be compared against a
        // stored hash, fail, and read to the user as a wrong password.
        #expect(throws: PinHasher.Failure.saltNotBase64) {
            _ = try PinHasher.hash("142857", saltBase64: "!!!!")
        }
    }

    @Test("generated salts are 16 random bytes and do not repeat")
    func saltShape() throws {
        var seen = Set<String>()
        for _ in 0..<50 {
            let s = PinHasher.generateSalt()
            let bytes = try #require(Data(base64Encoded: s))
            #expect(bytes.count == PinHasher.saltBytes)
            #expect(bytes.contains { $0 != 0 }, "an all-zero salt would mean the CSPRNG failed silently")
            seen.insert(s)
        }
        #expect(seen.count == 50, "salts must not repeat")
    }

    @Test("a salt round-trips through derivation")
    func generatedSaltUsable() throws {
        let salt = PinHasher.generateSalt()
        let a = try PinHasher.hash("142857", saltBase64: salt)
        let b = try PinHasher.hash("142857", saltBase64: salt)
        #expect(a == b, "the same password and salt must derive the same hash")
        #expect(try a != PinHasher.hash("142858", saltBase64: salt))
    }
}
