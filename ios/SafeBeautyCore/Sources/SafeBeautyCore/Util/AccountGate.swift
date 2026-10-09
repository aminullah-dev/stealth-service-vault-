import Foundation

/// Where a signed-in account belongs, decided from the three fields that decide it.
///
/// Ported from the Android sign-in gate (`AppNavGraph.kt`, `onAuthSuccess`), which
/// was the only client that held a salon owner to identity verification. iOS
/// checked `status` alone, so a provider whose salon APPLICATION an admin had
/// accepted — `status: APPROVED`, set from the Approvals tab without opening any
/// document — got the full salon app here while Android sent the same account to
/// the KYC screen. Two approvals, two fields: `status` is the application,
/// `kycStatus` is her tazkira and selfie. A provider needs both.
///
/// A pure function so the decision is tested in a second, without a simulator,
/// and so the launch path (a restored session) and the sign-in path cannot drift:
/// both render whatever this returns.
public enum AccountGate {

    public enum Destination: Equatable, Sendable {
        /// Suspended — the reason and the way to support, nothing else.
        case suspended
        /// A provider who may operate: the salon owner's own app.
        case providerApp
        /// A provider whose account is approved and whose identity is not:
        /// the KYC screen — submit, under review, or rejected with the reason.
        case providerVerification
        /// A provider whose salon application has not been decided yet.
        case providerPendingApproval
        /// Any other provider state (a declined application, an unknown value).
        case providerOther
        /// A non-provider account awaiting approval.
        case pendingApproval
        /// A customer. Her identity is checked at booking, not here — Android
        /// gates customers at the Book button, and so does SalonDetailView.
        case customerApp
    }

    public static func destination(role: String, status: String, kycStatus: String) -> Destination {
        if status == "SUSPENDED" { return .suspended }
        if role == "PROVIDER" {
            switch status {
            case "APPROVED":
                return kycStatus == "APPROVED" ? .providerApp : .providerVerification
            case "PENDING":
                return .providerPendingApproval
            default:
                return .providerOther
            }
        }
        if status == "PENDING" { return .pendingApproval }
        return .customerApp
    }

    /// Whether a customer may press Book. The server refuses anything else with
    /// KYC_REQUIRED (createPaymentSession); this keeps her from building a whole
    /// selection only to be told so at the last step.
    public static func customerMayBook(kycStatus: String) -> Bool {
        kycStatus == "APPROVED"
    }

    /// Whether the KYC form is what she should see. submitKyc refuses a PENDING
    /// account (a review is open) and an APPROVED one (nothing to do), so the
    /// form is offered only for NONE and REJECTED — and for any value this build
    /// has never seen, which submitKyc treats as NONE.
    public static func showsKycForm(kycStatus: String) -> Bool {
        kycStatus != "PENDING" && kycStatus != "APPROVED"
    }
}

/// The KYC form's own rules, in Android's order (`KycViewModel.validate`).
///
/// The order is the order the screen reads in, so the first thing she is told is
/// the first thing on the page she left empty.
public enum KycForm {

    public enum Problem: Equatable, Sendable {
        case tazkiraNumberRequired
        case provinceRequired
        case addressRequired
        case tazkiraPhotoRequired
        case selfieRequired
    }

    public static func firstProblem(tazkiraNumber: String, province: String, address: String,
                                    hasTazkiraPhoto: Bool, hasSelfie: Bool) -> Problem? {
        func blank(_ s: String) -> Bool { s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if blank(tazkiraNumber) { return .tazkiraNumberRequired }
        if blank(province) { return .provinceRequired }
        if blank(address) { return .addressRequired }
        if !hasTazkiraPhoto { return .tazkiraPhotoRequired }
        if !hasSelfie { return .selfieRequired }
        return nil
    }
}
