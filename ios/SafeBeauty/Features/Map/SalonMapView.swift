import SwiftUI
import MapKit
import SafeBeautyCore

/// Salons on a map, for the ones that have said where they are.
///
/// Today that is none of them: one salon never set coordinates and the other
/// was sitting at 37.42,-122.08 — the Android emulator's default, which is
/// Mountain View, California — until that was cleared on 2026-09-04. So the
/// empty state here is not a hypothetical, it is what this screen shows right
/// now, and it says which salons are missing rather than presenting a blank map
/// that reads as broken.
struct SalonMapView: View {
    @State private var repo = SalonRepository()
    @State private var selected: Salon?

    /// Kabul, as the fallback centre. Every salon is there and it is the only
    /// city with any.
    private static let kabul = CLLocationCoordinate2D(latitude: 34.5553, longitude: 69.2075)

    private var pinned: [Salon] { repo.salons.filter(\.hasLocation) }
    private var unpinned: [Salon] { repo.salons.filter { !$0.hasLocation } }

    private var camera: MapCameraPosition {
        // Centred on the first pinned salon, or on Kabul when there are none.
        // A map centred on 0,0 opens in the Gulf of Guinea, which is what
        // treating "unset" as a coordinate would do.
        .region(MKCoordinateRegion(
            center: pinned.first.map {
                CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
            } ?? Self.kabul,
            span: MKCoordinateSpan(latitudeDelta: 0.08, longitudeDelta: 0.08)))
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                Map(initialPosition: camera) {
                    ForEach(pinned) { salon in
                        Annotation(salon.salonName, coordinate: .init(
                            latitude: salon.latitude, longitude: salon.longitude)) {
                            Button { selected = salon } label: {
                                ZStack {
                                    Circle().fill(Brand.gradient).frame(width: 34, height: 34)
                                    Image(systemName: "scissors")
                                        .font(.system(size: 14)).foregroundStyle(.white)
                                }
                                .shadow(radius: 3)
                            }
                        }
                    }
                }
                .ignoresSafeArea(edges: .bottom)

                if !unpinned.isEmpty {
                    // Named, not counted. "2 salons are not on the map" tells
                    // her nothing she can use; the names let her go and find
                    // them in the list, and let the owner see which of her
                    // salons needs a pin.
                    VStack(alignment: .leading, spacing: 6) {
                        Text(pinned.isEmpty ? L.mapNoneP.t : L.mapSomeMissing.t)
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.ink)
                        Text(unpinned.map(\.salonName).joined(separator: "، "))
                            .font(Brand.font(12.5))
                            .foregroundStyle(Brand.accent)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 14)
                }
            }
            .navigationTitle(L.map.t)
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(item: $selected) { SalonDetailView(salon: $0) }
        }
        .task { repo.start() }
    }
}
