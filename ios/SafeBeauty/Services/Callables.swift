import Foundation
import FirebaseFunctions

/// The one place this app talks to the backend.
///
/// SafeBeauty is server-authoritative: appointment status, money, KYC, payouts
/// and now registration all go through callables, and `firestore.rules` leaves
/// clients no direct write path for any of them. So this file is small on
/// purpose — the app's job is to call the right function with the right
/// arguments, not to reimplement a decision the server already owns.
///
/// Every function is in `us-central1`. Not naming the region gives you
/// `us-central1` by default today and a confusing 404 the day someone adds a
/// second region, so it is stated.
/// What a callable argument is allowed to be.
///
/// Firebase callables speak JSON, so this is the whole vocabulary. Modelling it
/// explicitly rather than using `Any` makes the payloads Sendable — required
/// under Swift 6 — and makes a wrong type a compile error instead of a
/// server-side `invalid-argument` discovered by a user.
enum JSON: Sendable, ExpressibleByStringLiteral, ExpressibleByIntegerLiteral,
           ExpressibleByBooleanLiteral, ExpressibleByArrayLiteral {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)
    case array([JSON])
    case object([String: JSON])
    case null

    init(stringLiteral value: String) { self = .string(value) }
    init(integerLiteral value: Int) { self = .int(value) }
    init(booleanLiteral value: Bool) { self = .bool(value) }
    init(arrayLiteral elements: JSON...) { self = .array(elements) }

    static func strings(_ values: [String]) -> JSON { .array(values.map(JSON.string)) }

    /// Read a value the SDK handed back. NSNumber does not distinguish an Int
    /// from a Bool, so bools are identified by their objCType before the
    /// numeric cases — otherwise `false` decodes as `0` and a flag silently
    /// becomes a count.
    init(any value: Any?) {
        switch value {
        case nil, is NSNull: self = .null
        case let v as String: self = .string(v)
        case let v as NSNumber:
            if CFGetTypeID(v) == CFBooleanGetTypeID() { self = .bool(v.boolValue) }
            else if v === NSNumber(value: v.intValue) { self = .int(v.intValue) }
            else { self = .double(v.doubleValue) }
        case let v as [Any]: self = .array(v.map(JSON.init(any:)))
        case let v as [String: Any]: self = .object(v.mapValues(JSON.init(any:)))
        default: self = .null
        }
    }

    subscript(key: String) -> JSON? {
        if case .object(let o) = self { return o[key] }
        return nil
    }

    var stringValue: String? { if case .string(let v) = self { return v }; return nil }
    var intValue: Int? {
        switch self {
        case .int(let v): v
        case .double(let v): Int(v)
        default: nil
        }
    }
    var boolValue: Bool? { if case .bool(let v) = self { return v }; return nil }
    var arrayValue: [JSON]? { if case .array(let v) = self { return v }; return nil }

    var anyValue: Any {
        switch self {
        case .string(let v): v
        case .int(let v): v
        case .double(let v): v
        case .bool(let v): v
        case .array(let v): v.map(\.anyValue)
        case .object(let v): v.mapValues(\.anyValue)
        case .null: NSNull()
        }
    }
}

/// Deliberately NOT @MainActor.
///
/// A network client bound to the main actor forces every payload to cross an
/// isolation boundary at the `await`, which under Swift 6 is a data race on
/// `[String: Any]` — and the tempting fix is a cast that silences the compiler
/// rather than the design change that removes the crossing. There is no UI
/// state here and nothing to serialise; `Functions` is thread-safe and the
/// callable is built per call.
enum Callables {

    /// A callable's failure, translated once here rather than at every call site.
    ///
    /// The server distinguishes cases the user can act on — a taken phone
    /// number, a taken email — from ones they cannot, and it carries which in
    /// `details.field`. Collapsing them into "something went wrong" is what
    /// sent people to change the one field that was fine.
    enum CallableError: LocalizedError {
        case alreadyExists(field: String?)
        case permissionDenied
        case unauthenticated
        case rateLimited(message: String)
        case failedPrecondition(message: String)
        case other(message: String)

        var errorDescription: String? {
            switch self {
            case .alreadyExists(let f): "already-exists(\(f ?? "unknown"))"
            case .permissionDenied: "permission-denied"
            case .unauthenticated: "unauthenticated"
            case .rateLimited(let m), .failedPrecondition(let m), .other(let m): m
            }
        }
    }

    /// Call a Cloud Function and hand back its response.
    ///
    /// The payload is a Sendable tree and only becomes `[String: Any]` inside
    /// the continuation, where it never crosses an isolation boundary. The
    /// completion-handler API is used rather than the async one for exactly
    /// that reason — it keeps the dictionary in one domain from construction to
    /// the wire.
    @discardableResult
    static func call(
        _ name: String, _ payload: [String: JSON] = [:]
    ) async throws -> JSON {
        try await withCheckedThrowingContinuation { continuation in
            let functions = Functions.functions(region: region)
            functions.httpsCallable(name).call(payload.mapValues(\.anyValue)) { result, error in
                if let error {
                    continuation.resume(throwing: translate(error))
                } else {
                    // Converted inside the closure: [String: Any] is not
                    // Sendable, and resuming a continuation with one sends it
                    // across an isolation boundary just as passing it in does.
                    continuation.resume(returning: JSON(any: result?.data))
                }
            }
        }
    }

    /// Every function is in `us-central1`. Not naming it gives you that by
    /// default today and a confusing 404 the day someone adds a second region.
    private static let region = "us-central1"


    static func translate(_ error: Error) -> CallableError {
        let ns = error as NSError
        guard ns.domain == FunctionsErrorDomain,
              let code = FunctionsErrorCode(rawValue: ns.code) else {
            return .other(message: ns.localizedDescription)
        }
        let message = ns.localizedDescription
        // details.field is how registerAccount says WHICH value collided.
        let field = (ns.userInfo[FunctionsErrorDetailsKey] as? [String: Any])?["field"] as? String

        return switch code {
        case .alreadyExists: .alreadyExists(field: field)
        case .permissionDenied: .permissionDenied
        case .unauthenticated: .unauthenticated
        case .resourceExhausted: .rateLimited(message: message)
        case .failedPrecondition: .failedPrecondition(message: message)
        default: .other(message: message)
        }
    }
}
