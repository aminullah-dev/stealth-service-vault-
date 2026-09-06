import SwiftUI
import SafeBeautyCore

/// Confirming a booking, and paying for it.
///
/// The prices shown before confirming are the salon's own, and they are
/// labelled as an estimate for a reason: the server applies discounts this
/// screen does not know about — a promo code, referral credit, a salon offer, a
/// last-minute deal, a package bundle. Presenting a total as final and then
/// charging a different one is worse than showing no total at all, so the
/// authoritative figure appears only after `createPaymentSession` answers.
struct BookingSheet: View {
    let salon: Salon
    let serviceNames: [String]
    let startMillis: Int64

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    @State private var booking = BookingService()
    @State private var method = "CASH"
    @State private var promoCode = ""
    @State private var notes = ""
    @State private var quote: BookingService.Quote?
    @State private var error: String?

    private var estimate: Int {
        serviceNames.reduce(0) { $0 + (salon.pricePerService[$1] ?? 0) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let quote {
                        confirmation(quote)
                    } else {
                        form
                    }
                }
                .padding(.horizontal, 22)
                .padding(.top, 16)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(quote == nil ? L.confirmBooking.t : L.booked.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(quote == nil ? L.cancel.t : L.close.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                }
            }
        }
    }

    // MARK: Before

    @ViewBuilder
    private var form: some View {
        summaryCard

        VStack(alignment: .leading, spacing: 10) {
            Text(L.payment.t).font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
            Picker("", selection: $method) {
                Text(L.payCash.t).tag("CASH")
                Text(L.payOnline.t).tag("ONLINE")
            }
            .pickerStyle(.segmented)
        }

        BrandField(label: .promoCode, text: $promoCode)
        BrandField(label: .notesOptional, text: $notes)

        ErrorBanner(message: error)

        BrandButton(title: .confirmBooking, isLoading: booking.isWorking) {
            Task { await submit() }
        }
        .padding(.bottom, 30)
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            row(L.salonName.t, salon.salonName)
            row(L.chooseTime.t, TimeChip.label(startMillis))
            row(L.chooseDay.t, Self.dayLabel(startMillis))
            Divider().overlay(Brand.petal.opacity(0.5))
            ForEach(serviceNames, id: \.self) { name in
                row(name, "\(salon.pricePerService[name] ?? 0)", isNumeric: true)
            }
            Divider().overlay(Brand.petal.opacity(0.5))
            row(L.estimate.t, "\(estimate)", isNumeric: true, isBold: true)
            // Said plainly rather than in fine print. She will see a different
            // number if a discount applies, and being told so first is the
            // difference between a pleasant surprise and a broken promise.
            Text(L.estimateNote.t)
                .font(Brand.font(11.5))
                .foregroundStyle(Brand.accent)
        }
        .padding(15)
        .background(.white, in: RoundedRectangle(cornerRadius: 14))
    }

    // MARK: After

    @ViewBuilder
    private func confirmation(_ quote: BookingService.Quote) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: quote.isCash ? "checkmark.circle.fill" : "creditcard.fill")
                .font(.system(size: 40))
                .foregroundStyle(quote.isCash ? Brand.deep : Brand.accent)

            Text(quote.isCash ? L.bookedCash.t : L.payToConfirm.t)
                .font(Brand.font(16, .medium))
                .foregroundStyle(Brand.ink)

            VStack(alignment: .leading, spacing: 8) {
                if quote.hasDiscount {
                    // The discount is DERIVED from the two numbers either side
                    // of it, not taken from the server's discountAmount.
                    //
                    // Those did not reconcile: discountAmount covers the promo
                    // code, while a salon offer, a last-minute deal and a
                    // service-package bundle are also applied to the total. So
                    // whenever one of those landed, the panel showed a price, a
                    // smaller discount, and a total that did not follow from
                    // them — three numbers a customer can subtract in her head
                    // while deciding whether to pay. Shown this way they always
                    // agree, and the figure is the true total reduction.
                    row(L.listPrice.t, "\(quote.listPrice)", isNumeric: true)
                    row(L.discount.t, "−\(max(0, quote.listPrice - quote.amount))", isNumeric: true)
                }
                row(L.total.t, "\(quote.amount)", isNumeric: true, isBold: true)
            }
            .padding(15)
            .background(.white, in: RoundedRectangle(cornerRadius: 14))

            if !quote.checkoutUrl.isEmpty, let url = URL(string: quote.checkoutUrl) {
                BrandButton(title: .payNow) { openURL(url) }
            }
        }
        .padding(.bottom, 30)
    }

    // MARK: Bits

    private func row(_ label: String, _ value: String,
                     isNumeric: Bool = false, isBold: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(Brand.font(14, isBold ? .bold : .regular))
                .foregroundStyle(Brand.ink.opacity(isBold ? 1 : 0.8))
            Spacer()
            HStack(spacing: 4) {
                Text(value)
                    // Figures read left-to-right in every language.
                    .environment(\.layoutDirection, isNumeric ? .leftToRight : AppLanguage.current.layoutDirection)
                if isNumeric { Text(L.afn.t) }
            }
            .font(Brand.font(14, isBold ? .bold : .medium))
            .foregroundStyle(Brand.ink)
        }
    }

    private static func dayLabel(_ millis: Int64) -> String {
        let f = DateFormatter()
        f.timeZone = DayGrid.kabul
        f.locale = AppLanguage.current.locale
        f.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
        return f.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    private func submit() async {
        error = nil
        do {
            quote = try await booking.book(
                salonId: salon.id, serviceNames: serviceNames,
                startMillis: startMillis, method: method,
                notes: notes, promoCode: promoCode)
        } catch let e as BookingService.BookingError {
            error = switch e {
            case .slotTaken: L.errSlotTaken.t
            case .needsVerification: L.errNeedsVerification.t
            case .notBookable(let m): m
            case .network: L.errNetwork.t
            }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
