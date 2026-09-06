import SwiftUI
import SafeBeautyCore

/// The salon's offers, which only the web console could post.
///
/// `pushOfferToFavoriters` has been deployed the whole time: it fires when an
/// offer document is created and notifies every customer who favourited this
/// salon. From an iPhone there was no way to reach it — the one piece of
/// marketing this product gives a salon, and half its owners could not use it.
///
/// Inactive and expired offers are listed too. A customer sees only live ones;
/// the owner is the person who needs to see the ones that are not, because
/// "why is nobody coming" is usually "the offer is off".
struct EditOffersSheet: View {
    let repo: ProviderRepository

    @Environment(\.dismiss) private var dismiss

    @State private var title = ""
    @State private var details = ""
    @State private var percent = ""
    @State private var working = false
    @State private var error: String?

    private var trimmedTitle: String {
        title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if repo.offers.isEmpty {
                        Text(L.noOffersYet.t)
                            .font(Brand.font(13.5)).foregroundStyle(Brand.accent)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 16)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(repo.offers) { offer in row(offer) }
                        }
                    }

                    Divider().background(Brand.petal)

                    VStack(spacing: 9) {
                        TextField(L.offerTitleHint.t, text: $title, axis: .vertical)
                            .font(Brand.font(15)).foregroundStyle(Brand.ink)
                            .lineLimit(1...3)
                            .padding(13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 13))
                        TextField(L.offerDescHint.t, text: $details, axis: .vertical)
                            .font(Brand.font(15)).foregroundStyle(Brand.ink)
                            .lineLimit(1...3)
                            .padding(13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 13))
                        TextField(L.offerPercentHint.t, text: $percent)
                            .font(Brand.font(15)).foregroundStyle(Brand.ink)
                            .keyboardType(.numberPad)
                            .padding(13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 13))
                    }

                    ErrorBanner(message: error)

                    BrandButton(title: .addOffer, isLoading: working,
                                isEnabled: !trimmedTitle.isEmpty) {
                        Task { await add() }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 22).padding(.top, 14)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.offers.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(working)
                }
            }
        }
    }

    private func row(_ offer: SalonOffer) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(offer.title)
                    .font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                if !offer.description.isEmpty {
                    Text(offer.description)
                        .font(Brand.font(12)).foregroundStyle(Brand.textMuted)
                }
                // Live, not merely active: an offer past its expiry is off to
                // every customer, and saying "Active" over it would be a lie
                // she could only find out by asking one.
                if offer.isLive() {
                    Text(L.offerActive.t)
                        .font(Brand.font(11, .medium))
                        .foregroundStyle(Brand.success)
                }
            }
            Spacer()
            Toggle("", isOn: Binding(
                get: { offer.active },
                set: { on in Task { try? await repo.setOfferActive(offer, active: on) } }
            ))
            .labelsHidden()
            .tint(Brand.accent)
            Button {
                Task { try? await repo.deleteOffer(offer) }
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 13)).foregroundStyle(Brand.danger)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(L.offerRemove.t)
        }
        .padding(13)
        .background(.white, in: RoundedRectangle(cornerRadius: 13))
    }

    private func add() async {
        working = true; defer { working = false }
        error = nil
        do {
            // A percent that is not a number, or is out of range, is dropped
            // rather than refused: it is the optional field, and a badge is not
            // worth blocking the offer over.
            let pct = min(max(Int(percent.trimmingCharacters(in: .whitespaces)) ?? 0, 0), 100)
            try await repo.addOffer(
                title: trimmedTitle,
                details: details.trimmingCharacters(in: .whitespacesAndNewlines),
                discountPercent: pct)
            title = ""; details = ""; percent = ""
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
