import Foundation

/// Phone normalisation, which decides who you are.
///
/// The phone number is the login identifier. `authenticateWithPassword` resolves
/// an account by it, `registerAccount` refuses a number that is taken, and
/// password recovery finds you by it. So a number that normalises differently
/// here than on Android is not a formatting inconsistency — it is an account
/// this device cannot log into and a number registration will claim is free
/// when it is not.
///
/// Ported from `PhoneUtils.kt` rule for rule, and `phoneKey` from
/// `functions/lib/phone.js`, which is the third form the server resolves by.
public enum PhoneUtils {

    public static let afghanistanCode = "+93"

    /// Digits, plus a leading `+` if the user typed one.
    ///
    /// Kotlin's `filter { it.isDigit() }` accepts any Unicode digit, which
    /// includes the Eastern Arabic numerals a Dari or Pashto keyboard produces
    /// (۰۱۲۳۴۵۶۷۸۹). `Character.isNumber` in Swift does the same, so a number
    /// typed in Persian digits is cleaned identically — but it would then be
    /// carried through as those same characters, and the server compares
    /// against ASCII. So they are folded to ASCII here, which is what
    /// `wholeNumberValue` gives us.
    private static func clean(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasPlus = trimmed.hasPrefix("+")
        let digits = trimmed.compactMap { ch -> Character? in
            guard ch.isNumber, let v = ch.wholeNumberValue, (0...9).contains(v) else { return nil }
            return Character(String(v))
        }
        let joined = String(digits)
        return hasPlus ? "+" + joined : joined
    }

    /// Canonical Afghan form: "0700123456", "700123456", "0093700123456" → "+93700123456".
    public static func normalizeAfghan(_ raw: String) -> String {
        var c = clean(raw)
        if c.hasPrefix("+93") { return c }
        if c.hasPrefix("0093") { return "+93" + c.dropFirst(4) }
        if c.hasPrefix("93") && c.count >= 11 { return "+" + c }
        if c.hasPrefix("+") { c = String(c.dropFirst()) }
        if c.hasPrefix("0") { c = String(c.dropFirst()) }
        return afghanistanCode + c
    }

    /// A plausible Afghan mobile number: nine local digits.
    public static func isValidAfghan(_ raw: String) -> Bool {
        let local = normalizeAfghan(raw).replacingOccurrences(
            of: afghanistanCode, with: "", options: .anchored)
        return local.count == 9 && local.allSatisfy(\.isASCII) && local.allSatisfy(\.isNumber)
    }

    /// Login-time form, which serves both audiences.
    ///
    /// A number typed with a leading `+` is international and kept — admins may
    /// be in any country. Anything else is read as an Afghan local number.
    public static func normalizeForLogin(_ raw: String) -> String {
        let c = clean(raw)
        return c.hasPrefix("+") ? c : normalizeAfghan(raw)
    }

    /// The last nine digits, which is what the server indexes as `phoneDigits`.
    ///
    /// This is the form that matches a number however an older version of the
    /// app happened to store it — with or without a country code, with or
    /// without a leading zero. Mirrors `phoneKey` in functions/lib/phone.js,
    /// including its refusal to key anything shorter than seven digits: a
    /// four-digit string would collide with half the country.
    public static func phoneKey(_ raw: String) -> String {
        let digits = clean(raw).filter(\.isNumber)
        guard digits.count >= minIdentifyingDigits else { return "" }
        return String(digits.suffix(subscriberDigits))
    }

    public static let subscriberDigits = 9
    public static let minIdentifyingDigits = 7
}
