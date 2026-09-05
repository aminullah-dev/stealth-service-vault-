import Foundation

/// Turning Firestore's `[String: Any]` into models, without letting one bad
/// document take a screen down with it.
///
/// This exists because of a specific outage. On Android a single field whose
/// stored type disagreed with the declared one made `CustomClassMapper` throw
/// **inside a snapshot listener on the main thread** — so one malformed
/// document did not degrade one row, it removed the whole bookings screen for
/// every customer and the whole queue for every salon owner. The document was
/// fine; the listener was not survivable.
///
/// The rule here is that a decode failure is data about one document, never an
/// event that escapes into the caller. `decodeAll` returns what it could read
/// and hands back what it could not, so a caller can render four bookings and
/// report that a fifth is unreadable — which is a bad row, not a blank screen.
public enum DocumentDecoding {

    public struct Failure: Sendable {
        public let documentID: String
        public let reason: String
    }

    public struct Result<T: Sendable>: Sendable {
        public let values: [T]
        public let failures: [Failure]
        public var isCompletelyBroken: Bool { values.isEmpty && !failures.isEmpty }
    }

    /// A JSON round-trip, which is what makes Codable usable against Firestore.
    ///
    /// Firestore hands back NSNumber, NSNull and Timestamp rather than Swift
    /// types, and JSONSerialization normalises the first two. Timestamps are
    /// converted by the caller before this point — this layer is deliberately
    /// free of any Firebase import so the models stay testable with `swift test`
    /// and no SDK, no network and no simulator.
    public static func decode<T: Decodable>(
        _ type: T.Type, from raw: [String: Any]
    ) throws -> T {
        let data = try JSONSerialization.data(withJSONObject: sanitize(raw))
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Decode a whole snapshot, keeping what parses and reporting what does not.
    public static func decodeAll<T: Decodable & Sendable>(
        _ type: T.Type,
        documents: [(id: String, data: [String: Any])],
        assigningID: (inout T, String) -> Void = { _, _ in }
    ) -> Result<T> {
        var values: [T] = []
        var failures: [Failure] = []
        for doc in documents {
            do {
                var value = try decode(T.self, from: doc.data)
                assigningID(&value, doc.id)
                values.append(value)
            } catch {
                failures.append(Failure(documentID: doc.id, reason: String(describing: error)))
            }
        }
        return Result(values: values, failures: failures)
    }

    /// Replace what JSONSerialization refuses, rather than letting it throw.
    ///
    /// NSNull becomes absent so a Codable default applies; a non-finite Double
    /// (which Firestore can store) becomes 0 rather than making the whole
    /// document unserialisable and taking its siblings' screen with it.
    private static func sanitize(_ value: Any) -> Any {
        switch value {
        case let dict as [String: Any]:
            var out: [String: Any] = [:]
            for (k, v) in dict where !(v is NSNull) { out[k] = sanitize(v) }
            return out
        case let array as [Any]:
            return array.filter { !($0 is NSNull) }.map(sanitize)
        case let number as NSNumber:
            let d = number.doubleValue
            return d.isFinite ? number : NSNumber(value: 0)
        default:
            return value
        }
    }
}
