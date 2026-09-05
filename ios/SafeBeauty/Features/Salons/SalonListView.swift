import SwiftUI
import SafeBeautyCore

struct SalonListView: View {
    @State private var repo = SalonRepository()

    var body: some View {
        NavigationStack {
            Group {
                if repo.isLoading && repo.salons.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if repo.error != nil && repo.salons.isEmpty {
                    // "We could not load" is a different sentence from "there
                    // are none", and showing the wrong one teaches a customer
                    // that the app is empty when it is actually broken.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t).font(Brand.font(17, .bold))
                    } actions: {
                        Button(L.retry.t) { repo.start() }
                            .font(Brand.font(15, .medium))
                            .foregroundStyle(Brand.accent)
                    }
                } else if repo.salons.isEmpty {
                    ContentUnavailableView {
                        Text(L.noSalonsYet.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else {
                    List(repo.salons) { salon in
                        NavigationLink {
                            SalonDetailView(salon: salon)
                        } label: {
                            SalonRow(salon: salon)
                        }
                        .listRowBackground(Brand.cream)
                        .listRowSeparatorTint(Brand.petal.opacity(0.4))
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.salons.t)
            .navigationBarTitleDisplayMode(.large)
        }
        .task { repo.start() }
    }
}

struct SalonRow: View {
    let salon: Salon

    var body: some View {
        HStack(spacing: 13) {
            RoundedRectangle(cornerRadius: 13)
                .fill(Brand.gradient)
                .frame(width: 56, height: 56)
                .overlay(
                    Text(salon.salonName.prefix(1))
                        .font(Brand.font(22, .bold))
                        .foregroundStyle(.white)
                )

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(salon.salonName)
                        .font(Brand.font(16, .bold))
                        .foregroundStyle(Brand.ink)
                        .lineLimit(1)
                    if salon.isVerified {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 13))
                            .foregroundStyle(Brand.gold)
                            .accessibilityLabel(L.verified.t)
                    }
                }

                if !salon.district.isEmpty {
                    Text(salon.district)
                        .font(Brand.font(12.5))
                        .foregroundStyle(Brand.accent)
                        .lineLimit(1)
                }

                if salon.lowestPrice > 0 {
                    // The number stays left-to-right inside a right-to-left
                    // row, so "۸۰ افغانی" does not render with the figure on
                    // the wrong side of its unit.
                    HStack(spacing: 4) {
                        Text(L.from.t)
                        Text(verbatim: "\(salon.lowestPrice)")
                            .environment(\.layoutDirection, .leftToRight)
                        Text(L.afn.t)
                    }
                    .font(Brand.font(12.5, .medium))
                    .foregroundStyle(Brand.deep)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 7)
    }
}
