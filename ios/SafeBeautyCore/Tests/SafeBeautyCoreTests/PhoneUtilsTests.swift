import Testing
@testable import SafeBeautyCore

/// The phone number is the login identifier, so these are identity tests.
///
/// A number that normalises differently here than on Android is an account this
/// device cannot sign into, and a number registration will report as free while
/// the server holds it.
@Suite("PhoneUtils parity with Android and the server")
struct PhoneUtilsTests {

    @Test("every way an Afghan number gets typed lands on one canonical form")
    func afghanForms() {
        for input in ["0700123456", "700123456", "+93700123456", "0093700123456",
                      "93700123456", " 0700 123 456 ", "(0700) 123-456"] {
            #expect(PhoneUtils.normalizeAfghan(input) == "+93700123456",
                    "\(input) should normalise to +93700123456")
        }
    }

    @Test("nine local digits is the validity rule")
    func validity() {
        #expect(PhoneUtils.isValidAfghan("0700123456"))
        #expect(PhoneUtils.isValidAfghan("+93700123456"))
        #expect(!PhoneUtils.isValidAfghan("070012345"))      // eight
        #expect(!PhoneUtils.isValidAfghan("07001234567"))    // ten
        #expect(!PhoneUtils.isValidAfghan(""))
    }

    @Test("a leading + means international and is kept")
    func loginKeepsInternational() {
        // Admins may be in any country; normalizeAfghan would mangle their
        // number into +93<their whole number>, and they would never sign in.
        #expect(PhoneUtils.normalizeForLogin("+15551234567") == "+15551234567")
        #expect(PhoneUtils.normalizeForLogin("+1 (555) 123-4567") == "+15551234567")
        // Without the +, it is read as Afghan.
        #expect(PhoneUtils.normalizeForLogin("0700123456") == "+93700123456")
    }

    @Test("phoneKey collapses every stored spelling onto the same nine digits")
    func phoneKeyMatchesServer() {
        // This is what the server indexes as phoneDigits, and it is the reason
        // a number stored by an older build still resolves.
        for input in ["+93700123456", "0700123456", "700123456", "0093700123456"] {
            #expect(PhoneUtils.phoneKey(input) == "700123456")
        }
        // Too short to identify anyone — the server refuses to key it, so a
        // four-digit string cannot collide with half the country.
        #expect(PhoneUtils.phoneKey("12345") == "")
        #expect(PhoneUtils.phoneKey("") == "")
        #expect(PhoneUtils.phoneKey("1234567") == "1234567")   // exactly the minimum
    }

    /// Eastern Arabic numerals, which a Dari or Pashto keyboard can produce.
    ///
    /// Both `Character.isDigit` in Kotlin and `isNumber` in Swift accept these
    /// (they are Unicode category Nd), so both platforms treat "۰۷۰۰۱۲۳۴۵۶" as
    /// a valid phone number. Android then carries the Persian characters
    /// through into the stored value, while the server's `phoneKey` strips to
    /// `\D` and its indexes hold ASCII.
    ///
    /// iOS folds them to ASCII instead. That is a deliberate divergence and the
    /// safer direction — the number matches what the server stores rather than
    /// creating an account nothing else can find — but it IS a divergence, so
    /// it is pinned here rather than left to be discovered.
    @Test("Persian digits fold to ASCII so the server can match them")
    func easternArabicNumerals() {
        #expect(PhoneUtils.normalizeAfghan("۰۷۰۰۱۲۳۴۵۶") == "+93700123456")
        #expect(PhoneUtils.phoneKey("۰۷۰۰۱۲۳۴۵۶") == "700123456")
        #expect(PhoneUtils.isValidAfghan("۰۷۰۰۱۲۳۴۵۶"))
    }

    @Test("normalisation is idempotent")
    func idempotent() {
        // Registration normalises, then the server normalises again. If the
        // second pass moved the value, the two would disagree about the account.
        let once = PhoneUtils.normalizeAfghan("0700123456")
        #expect(PhoneUtils.normalizeAfghan(once) == once)
        let login = PhoneUtils.normalizeForLogin("+15551234567")
        #expect(PhoneUtils.normalizeForLogin(login) == login)
    }
}
