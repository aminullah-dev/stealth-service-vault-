import SwiftUI
import SafeBeautyCore

/// Wallet top-ups, gift cards and tips — three HesabPay flows iOS had none of.
///
/// All three callables were deployed and never called from this platform, so an
/// iPhone customer could see a wallet balance she could not add to, and a salon
/// she could not tip. They return the same shape as `createPaymentSession` —
/// `{paymentId, checkoutUrl, amount}` — and the webhook does the crediting, so
/// nothing here moves money; it opens a page and stops.

/// The parts all three share: an amount, a rule said before she is refused, and
/// a checkout URL to open.
private struct MoneyForm<Extra: View>: View {
    let title: L
    let rule: L
    let callable: String
    /// Fields beyond the amount, if the flow has any.
    @ViewBuilder let extra: Extra
    /// Anything the caller must add to the request.
    let payload: () -> [String: JSON]
    /// Whether those extra fields are filled in.
    let extraIsValid: () -> Bool
    let range: ClosedRange<Int>

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var amount = ""
    @State private var isWorking = false
    @State private var error: String?
    @State private var opened = false
    @State private var watcher = PaymentWatcher()

    private var value: Int { Int(amount) ?? 0 }
    private var canSubmit: Bool {
        range.contains(value) && extraIsValid() && !isWorking
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    extra
                    BrandField(label: .amountAfn, text: $amount, isPhone: true)
                    // Said before she submits, not after the server turns her
                    // down. The bounds here are the server's own.
                    Text(rule.t)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

                    if opened {
                        // Three states, not one. "The page is open" was the only
                        // thing this ever said, including after the money had
                        // already arrived.
                        switch watcher.outcome {
                        case .waiting:
                            ErrorBanner(message: L.openingCheckout.t, tone: .notice)
                            HStack(spacing: 6) {
                                ProgressView().tint(Brand.accent)
                                Text(L.paymentWaiting.t)
                                    .font(Brand.font(13)).foregroundStyle(Brand.accent)
                            }
                        case .paid:
                            ErrorBanner(message: L.paymentConfirmed.t, tone: .notice)
                        case .failed:
                            ErrorBanner(message: L.paymentFailed.t)
                        }
                    }
                    ErrorBanner(message: error)

                    if !opened || watcher.outcome == .failed {
                        BrandButton(title: .payNowShort, isLoading: isWorking,
                                    isEnabled: canSubmit) {
                            Task { await submit() }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(title.t)
            .navigationBarTitleDisplayMode(.inline)
            .onDisappear { watcher.stop() }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(opened ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(isWorking)
                }
            }
        }
    }

    private func submit() async {
        isWorking = true; defer { isWorking = false }
        error = nil
        var data = payload()
        data["amount"] = .int(value)
        do {
            let response = try await Callables.call(callable, data)
            guard let raw = response["checkoutUrl"]?.stringValue,
                  let url = URL(string: raw), !raw.isEmpty else {
                // A session with no URL is not a success to celebrate quietly.
                error = L.errNetwork.t
                return
            }
            // Watch before opening. The webhook can settle a fast payment
            // before she is back in the app, and a listener armed afterwards
            // would still see it — but arming first means there is no window
            // where the answer exists and nothing is looking.
            if let paymentId = response["paymentId"]?.stringValue {
                watcher.watch(paymentId: paymentId)
            }
            openURL(url)
            // Kept open rather than dismissed. She has to come back from
            // HesabPay, and a sheet that vanished as the browser opened leaves
            // her with no idea whether anything was started.
            opened = true
        } catch let e as Callables.CallableError {
            if case .failedPrecondition(let m, _) = e { error = m }
            else if case .rateLimited(let m) = e { error = m }
            else { error = L.errNetwork.t }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}

/// Adding credit to her own wallet, which the profile showed and could not fill.
struct TopUpSheet: View {
    var body: some View {
        MoneyForm(title: .topUpTitle, rule: .topUpRule,
                  callable: "createWalletTopUp",
                  extra: {
                      Text(L.walletAutoApplies.t)
                          .font(Brand.font(13)).foregroundStyle(Brand.ink.opacity(0.75))
                  },
                  payload: { [:] },
                  extraIsValid: { true },
                  range: 50...50_000)
    }
}

/// Buying credit for someone else, by phone number.
struct GiftCardSheet: View {
    @State private var phone = ""
    @State private var message = ""

    var body: some View {
        MoneyForm(title: .giftCard, rule: .giftRule,
                  callable: "createGiftCardSession",
                  extra: {
                      BrandField(label: .giftTo, text: $phone, isPhone: true)
                      BrandField(label: .giftMessage, text: $message)
                  },
                  payload: {
                      // Normalised here, the way every other phone this app
                      // sends is. The server looks the recipient up by the
                      // canonical +93 form, and "0700…" resolves to nobody.
                      ["recipientPhone": .string(PhoneUtils.normalizeAfghan(phone)),
                       "message": .string(message.trimmingCharacters(in: .whitespaces))]
                  },
                  extraIsValid: { PhoneUtils.isValidAfghan(phone) },
                  range: 50...50_000)
    }
}

/// Tipping the salon after a visit. The whole amount goes to the provider —
/// no commission — which is why it is worth offering at all.
struct TipSheet: View {
    let booking: Appointment

    var body: some View {
        MoneyForm(title: .tipTitle, rule: .tipRule,
                  callable: "createTipSession",
                  extra: {
                      VStack(alignment: .leading, spacing: 3) {
                          Text(booking.salonName)
                              .font(Brand.font(16, .bold)).foregroundStyle(Brand.ink)
                          Text(TimeChip.label(booking.appointmentDate))
                              .font(Brand.font(13)).foregroundStyle(Brand.accent)
                      }
                  },
                  payload: { ["appointmentId": .string(booking.id)] },
                  extraIsValid: { true },
                  range: 10...20_000)
    }
}
