import SwiftUI
import SafeBeautyCore

/// What she has taken, and what she owes for it.
///
/// The debt is the part that matters and the part nothing on iOS showed. A cash
/// booking is money she collects at the door, so the platform's commission
/// becomes a balance on `provider_balances` — written by the webhook, cleared by
/// an admin when she settles. An owner who cannot see it finds out when someone
/// telephones her about it.
struct ProviderIncomeView: View {
    let repo: ProviderRepository

    /// Only visits that actually happened. A confirmed booking is a promise;
    /// counting it as income makes every cancellation look like a loss she
    /// already banked.
    private var completed: [Appointment] {
        repo.appointments.filter { $0.status == .completed }
    }

    /// What a visit earned, or nil when that cannot be recovered.
    ///
    /// `total` sums the prices stored on the booking, and five of this salon's
    /// eight completed visits have no `services` array at all — they were
    /// written before the app stored one. The provider console falls back to
    /// the salon's current price for the named service, which is why its
    /// variable is called `est`; here that fallback returns nothing, because
    /// the bookings name "mo" and the salon's price list now says «مو».
    ///
    /// So the price is genuinely unrecoverable, and nil is the answer. Printing
    /// 0 AFN would be a claim that the visit earned nothing, which is a
    /// different and false statement — and the one a salon owner would read as
    /// hers.
    private func earned(_ booking: Appointment) -> Int? {
        if booking.total > 0 { return booking.total }
        if let price = repo.salon?.pricePerService[booking.serviceName], price > 0 { return price }
        return nil
    }

    private var taken: Int { completed.compactMap(earned).reduce(0, +) }

    /// How many the total leaves out, so the figure is not read as covering all
    /// eight visits when it covers three.
    private var unpriced: Int { completed.filter { earned($0) == nil }.count }

    var body: some View {
        NavigationStack {
            Group {
                if repo.salon == nil {
                    NoSalonYet(repo: repo)
                } else {
                    ScrollView {
                        VStack(spacing: 16) {
                            HStack(spacing: 0) {
                                stat(L.completedVisits.t, "\(completed.count)", suffix: nil)
                                Divider().frame(height: 42).overlay(Brand.petal.opacity(0.5))
                                stat(L.earnedTotal.t, "\(taken)", suffix: L.afn.t)
                            }
                            .padding(.vertical, 16)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))

                            if unpriced > 0 {
                                Text(L.earnedExcludes(unpriced))
                                    .font(Brand.font(12)).foregroundStyle(Brand.accent)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }

                            // Shown even at zero. "You owe nothing" is worth
                            // reading; a card that appears only when there is a
                            // debt teaches her that its absence means nothing
                            // was calculated.
                            VStack(alignment: .leading, spacing: 6) {
                                // The sign IS the meaning. commission.js:
                                // "Positive means the platform owes the salon;
                                // negative means the salon owes the platform."
                                // Both live values in production today are
                                // negative, so a card that printed the raw
                                // number under one fixed label would have told
                                // both salons the opposite of the truth about
                                // their own money. Magnitude and a sentence,
                                // matching the provider console exactly.
                                Text(repo.owed >= 0 ? L.platformOwesYou.t : L.youOweCommission.t)
                                    .font(Brand.font(13, .medium))
                                    .foregroundStyle(Brand.ink.opacity(0.75))
                                HStack(spacing: 5) {
                                    Text(verbatim: "\(abs(repo.owed))")
                                        .font(Brand.font(26, .bold))
                                        .environment(\.layoutDirection, .leftToRight)
                                        .foregroundStyle(repo.owed >= 0 ? Brand.success
                                                                        : Brand.danger)
                                    Text(L.afn.t)
                                        .font(Brand.font(14, .medium)).foregroundStyle(Brand.accent)
                                }
                                Text(repo.owed == 0 ? L.balanceSettled.t : L.owedExplain.t)
                                    .font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(15)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))

                            // Volume, under the money it explains. Android
                            // gives this its own tab; on four tabs it belongs
                            // beside the earnings it accounts for — a month with
                            // little income and many cancellations is a
                            // different problem from one with few bookings.
                            if totalBookings > 0 {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(spacing: 0) {
                                        stat(L.analyticsTotal.t,
                                             "\(totalBookings)", suffix: nil)
                                        Divider().frame(height: 34)
                                            .overlay(Brand.petal.opacity(0.5))
                                        stat(L.analyticsConfirmed.t,
                                             "\(analytics.confirmed)", suffix: nil)
                                        Divider().frame(height: 34)
                                            .overlay(Brand.petal.opacity(0.5))
                                        // Waiting on her. Android has always
                                        // shown this and iOS had three cards
                                        // where Android has four — the one
                                        // number on this screen that is a
                                        // thing to DO rather than a thing that
                                        // happened.
                                        stat(L.pending.t,
                                             "\(analytics.pending)", suffix: nil)
                                        Divider().frame(height: 34)
                                            .overlay(Brand.petal.opacity(0.5))
                                        stat(L.analyticsCancelled.t,
                                             "\(analytics.cancelled)", suffix: nil)
                                    }
                                    if !byService.isEmpty {
                                        Text(L.analyticsByService.t)
                                            .font(Brand.font(13, .bold))
                                            .foregroundStyle(Brand.ink)
                                            .padding(.top, 4)
                                        ForEach(byService, id: \.name) { row in
                                            HStack(spacing: 8) {
                                                Text(row.name)
                                                    .font(Brand.font(13))
                                                    .foregroundStyle(Brand.ink.opacity(0.85))
                                                    .lineLimit(1)
                                                // A bar, not a chart library.
                                                // One salon with one service
                                                // does not need a framework to
                                                // draw a proportion.
                                                GeometryReader { geo in
                                                    Capsule().fill(Brand.gradient)
                                                        .frame(width: max(4, geo.size.width
                                                            * CGFloat(row.count) / CGFloat(topCount)))
                                                }
                                                .frame(height: 8)
                                                Text(verbatim: "\(row.count)")
                                                    .font(Brand.font(12.5, .medium))
                                                    .environment(\.layoutDirection, .leftToRight)
                                                    .foregroundStyle(Brand.deep)
                                            }
                                        }
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(15)
                                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                            }

                            if !completed.isEmpty {
                                VStack(alignment: .leading, spacing: 0) {
                                    ForEach(completed.prefix(30)) { booking in
                                        HStack(spacing: 8) {
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(ProviderBookingRow.identify(booking))
                                                    .font(Brand.font(14, .medium))
                                                    .foregroundStyle(Brand.ink)
                                                Text(TimeChip.dateLabel(booking.appointmentDate))
                                                    .font(Brand.font(12))
                                                    .foregroundStyle(Brand.accent)
                                            }
                                            Spacer(minLength: 0)
                                            if let amount = earned(booking) {
                                                HStack(spacing: 3) {
                                                    Text(verbatim: "\(amount)")
                                                        .environment(\.layoutDirection, .leftToRight)
                                                    Text(L.afn.t)
                                                }
                                                .font(Brand.font(13, .medium))
                                                .foregroundStyle(Brand.deep)
                                            } else {
                                                // An em dash, not a zero. "We do
                                                // not know" and "she earned
                                                // nothing" are different things
                                                // to tell a salon owner.
                                                Text(verbatim: "—")
                                                    .font(Brand.font(13, .medium))
                                                    .foregroundStyle(Brand.accent)
                                            }
                                        }
                                        .padding(.vertical, 9)
                                        if booking.id != completed.prefix(30).last?.id {
                                            Divider().overlay(Brand.petal.opacity(0.4))
                                        }
                                    }
                                }
                                .padding(.horizontal, 15)
                                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 16))
                            }
                            Spacer(minLength: 20)
                        }
                        .padding(.horizontal, 18).padding(.top, 12)
                    }
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabIncome.t)
        }
    }

    /// Prefer the server's tally, which counts every booking this salon has
    /// ever had. The local list is bounded at three hundred, so counting it is
    /// right until a salon passes that and then quietly wrong — and the salon
    /// that passes it is the one this screen matters most to. Falls back to
    /// counting when the tally has not been written yet.
    private var haveStats: Bool { repo.totalBookings > 0 }

    /// The tally, through the shared derivation.
    ///
    /// Through `ProviderAnalytics` rather than reading `repo.byStatus` directly,
    /// because the three rules it applies are the ones Android applies and are
    /// now held by a parity test. Two of them were missing here:
    ///
    /// Firestore's increment leaves a key behind at zero once its last booking
    /// moves away, and nothing filtered those out — so a service the salon had
    /// stopped taking bookings for sat in the breakdown as a row with a bar of
    /// no length. Android drops them.
    ///
    /// And the order had no tie-break, so two services with the same count
    /// could swap places between reads for no reason a salon owner could see.
    /// `sorted(by:)` is not stable in Swift, and a dictionary has no order to
    /// be stable about in the first place.
    private var analytics: ProviderAnalytics {
        if haveStats {
            return .from(total: repo.totalBookings,
                         byStatus: repo.byStatus,
                         byService: repo.byService,
                         confirmedByService: [:])
        }
        // Before the trigger has written a tally: counted from the bookings
        // that are loaded. Grouped by the name stored ON the booking rather
        // than the salon's current list, because the history is what happened
        // and a renamed service did not un-happen.
        let statuses = Dictionary(grouping: repo.appointments, by: { $0.status.rawValue })
            .mapValues(\.count)
        let services = Dictionary(grouping: repo.appointments.filter { !$0.serviceName.isEmpty },
                                  by: \.serviceName).mapValues(\.count)
        return .from(total: repo.appointments.count, byStatus: statuses,
                     byService: services, confirmedByService: [:])
    }

    private var totalBookings: Int { analytics.total }

    private var byService: [(name: String, count: Int)] {
        // Every service, as Android shows them — a salon has a handful, and
        // truncating at six hid the tail from the person whose salon it is.
        analytics.serviceBreakdown.map { (name: $0.service, count: $0.count) }
    }

    /// The busiest service, so the bars are proportional to something real.
    private var topCount: Int { max(1, byService.first?.count ?? 1) }

    private func stat(_ label: String, _ value: String, suffix: String?) -> some View {
        VStack(spacing: 3) {
            HStack(spacing: 3) {
                Text(verbatim: value)
                    .font(Brand.font(22, .bold)).foregroundStyle(Brand.deep)
                    .environment(\.layoutDirection, .leftToRight)
                if let suffix {
                    Text(suffix).font(Brand.font(12)).foregroundStyle(Brand.accent)
                }
            }
            Text(label).font(Brand.font(12)).foregroundStyle(Brand.ink.opacity(0.75))
        }
        .frame(maxWidth: .infinity)
    }
}
