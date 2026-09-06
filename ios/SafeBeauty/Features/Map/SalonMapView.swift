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

    /// The name, and under it the neighbourhood.
    ///
    /// Android puts the same thing in the marker's snippet — a map of a city
    /// this size is read by neighbourhood, and two pins a centimetre apart in
    /// «شهرنو» and «کارته سه» are a different answer to "which one is near me"
    /// than two unlabelled dots. Written out rather than hidden behind a tap
    /// because tapping a pin here opens the salon, and a label nobody can reach
    /// without leaving the map is a label the map does not have.
    @ViewBuilder
    private func pinLabel(_ salon: Salon) -> some View {
        VStack(spacing: 0) {
            Text(salon.salonName)
                .font(Brand.font(12, .medium))
                .foregroundStyle(Brand.ink)
            if !salon.district.isEmpty {
                // The label, never the stored key: the document holds
                // "KBL_Shirpur" and the map would have printed exactly that.
                Text(Areas.address(district: salon.district, areaKey: salon.areaKey))
                    .font(Brand.font(10.5))
                    .foregroundStyle(Brand.accent)
            }
        }
        // Two short lines rather than one long one wrapped by MapKit, which
        // breaks a Persian place name wherever it happens to run out of room.
        .multilineTextAlignment(.center)
        .lineLimit(1)
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                Map(initialPosition: camera) {
                    ForEach(pinned) { salon in
                        Annotation(coordinate: .init(
                            latitude: salon.latitude, longitude: salon.longitude)) {
                            Button { selected = salon } label: {
                                ZStack {
                                    Circle().fill(Brand.gradient).frame(width: 34, height: 34)
                                    Image(systemName: "scissors")
                                        .font(.system(size: 14)).foregroundStyle(.white)
                                }
                                .shadow(radius: 3)
                            }
                        } label: {
                            pinLabel(salon)
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
