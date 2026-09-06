import Foundation
import SafeBeautyCore

/// Booking and paying, which this app deliberately knows almost nothing about.
///
/// `createPaymentSession` computes the price, applies the promo code, the
/// referral credit, the salon offer, the last-minute deal and the package
/// bundle, caps the stack, takes the commission, and re-checks the slot for a
/// collision — all server-side, all inside one transaction. Every one of those
/// is money, and money in this system is server-authoritative by design.
///
/// So iOS sends what the customer chose and displays what comes back. It does
/// not add the prices up and compare, does not apply a discount it thinks it
/// understands, and does not decide whether a slot is free — the grid it drew
/// is an offer, and this call is the arbiter. A client that computes its own
/// total will eventually disagree with the receipt, and the customer will
/// believe the client.
@MainActor
@Observable
final class BookingService {

    /// What the server says a booking costs, in its own words.
    struct Quote: Sendable, Equatable {
        let paymentId: String
        let appointmentId: String
        /// Empty for cash — nothing to open, the booking is already confirmed.
        let checkoutUrl: String
        let method: String
        /// What she actually pays.
        let amount: Int
        /// Before discounts, so a saving can be shown honestly.
        let listPrice: Int
        let discountAmount: Int

        var hasDiscount: Bool { discountAmount > 0 && listPrice > amount }
        var isCash: Bool { checkoutUrl.isEmpty }
    }

    enum BookingError: LocalizedError {
        case slotTaken
        case needsVerification
        case notBookable(String)
        case network

        var errorDescription: String? {
            switch self {
            case .slotTaken: "slotTaken"
            case .needsVerification: "needsVerification"
            case .notBookable(let m): m
            case .network: "network"
            }
        }
    }

    private(set) var isWorking = false

    func book(
        salonId: String,
        serviceNames: [String],
        startMillis: Int64,
        method: String,
        staffId: String = "",
        notes: String = "",
        promoCode: String = "",
        /// The bundle the basket qualifies for, "" for none. The server finds
        /// it on the salon by id and refuses one whose services are not all
        /// being booked, so sending it is safe even if the basket changed.
        packageId: String = "",
        /// The guest list, empty for an ordinary booking. The server
        /// re-normalises it against what the salon offers and prices the party
        /// from it, so this is a request rather than an assertion.
        party: [Party.Guest] = []
    ) async throws -> Quote {
        isWorking = true
        defer { isWorking = false }

        let response: JSON
        do {
            response = try await Callables.call("createPaymentSession", [
                "salonId": .string(salonId),
                "serviceNames": .strings(serviceNames),
                // Milliseconds, matching what the grid produced and what the
                // server stores. Sending seconds would book 1970.
                "appointmentDate": .int(Int(startMillis)),
                "method": .string(method == "CASH" ? "CASH" : "ONLINE"),
                "staffId": .string(staffId),
                "notes": .string(notes),
                "promoCode": .string(promoCode.trimmingCharacters(in: .whitespaces).uppercased()),
                "packageId": .string(packageId),
                "party": .array(party.map {
                    .object(["name": .string($0.name), "services": .strings($0.services)])
                }),
            ])
        } catch let e as Callables.CallableError {
            switch e {
            case .failedPrecondition(let message, let reason):
                // The server distinguishes these, and so should she. "Verify
                // your identity" and "someone just took that time" lead to
                // completely different next actions.
                //
                // Translated from the server's `reason` code, not from its
                // message. Every refusal except the KYC one used to reach her
                // as the English sentence the server wrote for a developer —
                // in an app she is using in Dari or Pashto.
                switch reason {
                case "SLOT_TAKEN":         throw BookingError.slotTaken
                case "SALON_CLOSED":       throw BookingError.notBookable(L.errSalonClosed.t)
                case "SALON_UNAVAILABLE":  throw BookingError.notBookable(L.errSalonUnavailable.t)
                case "AFTER_CLOSING":      throw BookingError.notBookable(L.errAfterClosing.t)
                case "STAFF_UNAVAILABLE":  throw BookingError.notBookable(L.errStaffUnavailable.t)
                case "PROMO_LIMIT":        throw BookingError.notBookable(L.errPromoLimit.t)
                case "FREE_USE_CASH":      throw BookingError.notBookable(L.errFreeUseCash.t)
                default:
                    if message.lowercased().contains("verify") {
                        throw BookingError.needsVerification
                    }
                    // A reason this build has not heard of still says something
                    // she can act on, rather than a server sentence in English.
                    throw BookingError.notBookable(L.errNotBookable.t)
                }
            case .alreadyExists:
                // Kept for exhaustiveness only. createPaymentSession signals a
                // lost slot as failed-precondition with reason SLOT_TAKEN,
                // handled above — this case never fired, which is why the
                // translated L.errSlotTaken could never be shown.
                throw BookingError.slotTaken
            default:
                throw BookingError.network
            }
        }

        guard let paymentId = response["paymentId"]?.stringValue else {
            throw BookingError.network
        }

        return Quote(
            paymentId: paymentId,
            appointmentId: response["appointmentId"]?.stringValue ?? "",
            checkoutUrl: response["checkoutUrl"]?.stringValue ?? "",
            method: response["method"]?.stringValue ?? method,
            // Read from the response, never recomputed. If these ever disagree
            // with what the app showed, the response is right.
            amount: response["amount"]?.intValue ?? 0,
            listPrice: response["listPrice"]?.intValue ?? 0,
            discountAmount: response["discountAmount"]?.intValue ?? 0
        )
    }
}
