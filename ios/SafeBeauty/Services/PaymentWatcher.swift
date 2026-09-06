import Foundation
import FirebaseFirestore
import SafeBeautyCore

/// Watches one HesabPay payment until the webhook settles it.
///
/// Every checkout on iOS ended the same way: the browser opened, she paid, she
/// came back, and the sheet still said "the payment page is open". Nothing on
/// this platform ever looked at the payment document — `paymentId` was returned
/// by every one of the four callables and read by nothing.
///
/// Android watches it (`observePaymentStatus`), and it matters most at exactly
/// this moment: she has just handed over money and the app is the only thing
/// that can tell her it arrived.
///
/// A listener rather than a poll. The rules let her read her own payment, the
/// webhook writes the status, and a snapshot listener costs one read when it
/// changes instead of one every few seconds while she waits.
@MainActor
@Observable
final class PaymentWatcher {
    enum Outcome { case waiting, paid, failed }

    private(set) var outcome: Outcome = .waiting
    private var listener: ListenerRegistration?

    // No deinit: a MainActor property cannot be touched from the nonisolated
    // context deinit runs in. The sheet calls stop() when it goes away, and the
    // listener is on a single document either way.

    func watch(paymentId: String) {
        guard !paymentId.isEmpty else { return }
        listener?.remove()
        outcome = .waiting
        listener = Firestore.firestore().document("payments/\(paymentId)")
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let self else { return }
                // A read failure is not a failed payment. Staying on "waiting"
                // is the honest answer — telling her it failed when the network
                // dropped would send her to pay a second time.
                let status = (snapshot?.data()?["status"] as? String) ?? ""
                switch status {
                case "PAID": self.outcome = .paid; self.stop()
                case "FAILED": self.outcome = .failed; self.stop()
                default: break
                }
            }
    }

    func stop() {
        listener?.remove()
        listener = nil
    }
}
