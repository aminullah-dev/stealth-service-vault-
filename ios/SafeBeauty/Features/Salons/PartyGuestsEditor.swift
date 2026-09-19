import SwiftUI
import SafeBeautyCore

/// The guest list for a wedding party.
///
/// Android has had this and iOS flattened nothing at all — it simply could not
/// make one. That matters more here than the feature count suggests: a wedding
/// party is the most valuable booking in this market, and the model exists on
/// the server precisely so it is not one very long queue on one stylist.
///
/// Per guest, not per service. The salon receives who is having what and can
/// plan; a flat list of "Cut, Cut, Colour" is what the server's own comment
/// calls the wrong shape.
struct PartyGuestsEditor: View {
    let salon: Salon
    @Binding var guests: [Party.Guest]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.partyPrepay.t)
                .font(Brand.font(12.5)).foregroundStyle(Brand.accent)

            ForEach($guests) { $guest in
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        TextField(L.guestName.t, text: $guest.name)
                            .font(Brand.font(14.5))
                            .padding(.horizontal, 12).padding(.vertical, 9)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 11))
                        Button {
                            guests.removeAll { $0.id == guest.id }
                        } label: {
                            Image(systemName: "trash")
                                .font(.system(size: 13)).foregroundStyle(Brand.danger)
                        }
                        .buttonStyle(.plain)
                    }
                    FlowLayout(spacing: 8) {
                        ForEach(salon.services, id: \.self) { service in
                            ServiceChip(name: service,
                                        price: salon.pricePerService[service],
                                        isSelected: guest.services.contains(service)) {
                                if let i = guest.services.firstIndex(of: service) {
                                    guest.services.remove(at: i)
                                } else if guest.services.count < Party.maxServicesPerGuest {
                                    guest.services.append(service)
                                }
                            }
                        }
                    }
                }
                .padding(12)
                .background(Brand.petal.opacity(0.22), in: RoundedRectangle(cornerRadius: 14))
            }

            if guests.count < Party.maxGuests {
                Button {
                    // Named by position, so a bride adding six people is not
                    // typing six names before she can choose a single service.
                    guests.append(Party.Guest(name: L.guestNumber(guests.count + 1)))
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "plus.circle.fill")
                        Text(L.addGuest.t)
                    }
                    .font(Brand.font(14, .medium)).foregroundStyle(Brand.accent)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
