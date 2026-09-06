import SwiftUI
import SafeBeautyCore

/// What she has agreed to, in the order it happens.
///
/// Android splits this from Requests, and rightly: a confirmed booking is not a
/// decision any more, it is a plan. Past visits sit below, because a salon owner
/// looks up "who was that customer last Tuesday" as often as she looks ahead.
struct ProviderCalendarView: View {
    let repo: ProviderRepository

    @State private var reporting: Appointment?

    var body: some View {
        NavigationStack {
            Group {
                if repo.salon == nil {
                    NoSalonYet(repo: repo)
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
                                ForEach(repo.past) { booking in
                                    VStack(alignment: .leading, spacing: 0) {
                                        ProviderBookingRow(booking: booking)
                                        // Only on a visit that was supposed to
                                        // happen. There is nothing to say about
                                        // a booking the salon itself declined.
                                        if booking.status == .completed
                                            || booking.status == .confirmed {
                                            Button(L.rateCustomer.t) { reporting = booking }
                                                .font(Brand.font(13, .medium))
                                                .foregroundStyle(Brand.accent)
                                                .buttonStyle(.borderless)
                                                .padding(.top, 2)
                                        }
                                    }
                                    .listRowBackground(Color.white)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabCalendar.t)
            .sheet(item: $reporting) { booking in
                ReportCustomerSheet(booking: booking, repo: repo).appDirection()
            }
        }
    }
}
