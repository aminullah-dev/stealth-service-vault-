import SwiftUI
import SafeBeautyCore

/// What she has agreed to, in the order it happens.
///
/// Android splits this from Requests, and rightly: a confirmed booking is not a
/// decision any more, it is a plan. Past visits sit below, because a salon owner
/// looks up "who was that customer last Tuesday" as often as she looks ahead.
struct ProviderCalendarView: View {
    let repo: ProviderRepository

    var body: some View {
        NavigationStack {
            Group {
                if repo.salon == nil {
                    NoSalonYet()
                } else if repo.upcoming.isEmpty && repo.past.isEmpty {
                    ContentUnavailableView {
                        Text(L.noUpcoming.t)
                            .font(Brand.font(16, .medium)).foregroundStyle(Brand.ink)
                    }
                } else {
                    List {
                        if !repo.upcoming.isEmpty {
                            Section(L.upcoming.t) {
                                ForEach(repo.upcoming) { ProviderBookingRow(booking: $0) }
                            }
                        }
                        if !repo.past.isEmpty {
                            Section(L.pastBookings.t) {
                                ForEach(repo.past) { ProviderBookingRow(booking: $0) }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabCalendar.t)
        }
    }
}
