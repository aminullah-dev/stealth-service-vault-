import SwiftUI
import SafeBeautyCore

/// The salons she has kept, which Android gives a tab of its own and iOS had
/// nowhere at all.
///
/// Reuses SalonRow rather than drawing a second kind of salon card: the same
/// salon should not look like two different things depending on which tab she
/// reached it from.
struct FavoritesView: View {
    @State private var repo = SalonRepository()
    @State private var favourites = FavoritesStore.shared

    private var visible: [Salon] {
        repo.salons.filter { favourites.contains($0.id) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if repo.isLoading && repo.salons.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if visible.isEmpty {
                    // Says what to do, not just that there is nothing. An empty
                    // screen that only reports emptiness leaves her to guess
                    // that a heart exists somewhere else.
                    ContentUnavailableView {
                        Text(L.noFavourites.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Brand.ink)
                    } description: {
                        Text(L.noFavouritesHint.t)
                            .font(Brand.font(14))
                            .foregroundStyle(Brand.accent)
                    }
                } else {
                    List(visible) { salon in
                        NavigationLink { SalonDetailView(salon: salon) } label: {
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
            .navigationTitle(L.favorites.t)
            .navigationBarTitleDisplayMode(.large)
        }
        .task { repo.start() }
    }
}

/// The heart itself, so the salon list, the favourites tab and the salon page
/// all toggle the same way.
struct FavoriteButton: View {
    let salonId: String
    var size: CGFloat = 17

    @State private var favourites = FavoritesStore.shared
    @Environment(AuthService.self) private var auth
    @Environment(SignInPrompt.self) private var signInPrompt

    private var isOn: Bool { favourites.contains(salonId) }

    var body: some View {
        Button {
            // Reachable while browsing without an account now that the salon
            // list no longer forces sign-in first. `toggle` already guards an
            // empty uid and no-ops, which used to be invisible — a tap that
            // silently does nothing reads as a broken heart, not a locked one.
            if auth.session == nil { signInPrompt.request() }
            else { favourites.toggle(salonId) }
        } label: {
            Image(systemName: isOn ? "heart.fill" : "heart")
                .font(.system(size: size))
                .foregroundStyle(isOn ? Brand.deep : Brand.accent)
                // Only the fill animates. Scaling the whole row on every tap
                // moves what is underneath her thumb.
                .contentTransition(.symbolEffect(.replace))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isOn ? L.removeFavourite.t : L.addFavourite.t)
    }
}
