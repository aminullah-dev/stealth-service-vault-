import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// A salon, its services, and the times she can actually have.
///
/// The grid comes from `DayGrid` and the collisions from `Slots`, both of which
/// are ported from the server and tested against it. Nothing here re-derives
/// either — a second opinion about which slots are free is exactly how a client
/// starts offering times that get refused at payment.
struct SalonDetailView: View {
    let salon: Salon

    @Environment(AuthService.self) private var auth

    @State private var selectedServices: Set<String> = []
    @State private var selectedDay = Date()
    @State private var selectedSlot: Int64?
    @State private var booked: [Appointment] = []
    @State private var isLoadingSlots = false
    /// Whether the last getBookedSlots read failed. A failed read is not an
    /// empty diary — treating it as one offers every hour of the day as free.
    @State private var slotsUnavailable = false
    @State private var showBooking = false
    @State private var showKyc = false
    @State private var showChat = false
    @State private var reviews: [Review] = []

    /// The next seven days, starting today, in Kabul.
    private var days: [Date] {
        (0..<7).compactMap {
            DayGrid.kabulCalendar.date(byAdding: .day, value: $0, to: Date())
        }
    }

    /// What the salon offers that day, minus what is already taken.
    ///
    /// The layout is computed from the chosen services so a two-hour booking
    /// asks for two hours — offering a slot that only fits one is how someone
    /// picks a time the server then refuses.
    private var availableSlots: [Int64] {
        // The salon's own timings, not empty maps. These were `[:]`, so the
        // client laid every service out as one slot while the server built the
        // span from the stored durations — the narrow direction, which offers
        // times the server then refuses at payment, after she has chosen one
        // and started paying.
        let layout = Slots.serviceLayout(
            serviceNames: Array(selectedServices),
            timings: salon.serviceTiming,
            durationPerService: salon.durationPerService,
            slotMinutes: salon.slotDurationMinutes)

        return DayGrid.slots(for: selectedDay,
                             hours: salon.workingHours,
                             slotMinutes: salon.slotDurationMinutes,
                             span: layout.span,
                             blockedDates: salon.blockedDates)
            .filter { start in
                !Slots.hasConflict(
                    existing: booked, requestedStart: start,
                    requestedOffsets: layout.busyOffsets,
                    staffId: "", slotMinutes: salon.slotDurationMinutes)
            }
    }

    private var total: Int {
        selectedServices.reduce(0) { $0 + (salon.pricePerService[$1] ?? 0) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                cover
                    .padding(.horizontal, -22)   // full-bleed inside the padded stack
                    .padding(.top, -12)
                header

                // Said before she picks a time, not after she has chosen one
                // and pressed Book. createPaymentSession refuses an unverified
                // customer, and finding that out at the last step wastes the
                // whole selection.
                // Only NONE and REJECTED can act. submitKyc refuses a PENDING
                // account — a review is already open — so offering the button
                // there sent her to a form that re-uploaded over the files
                // under review and was then refused. PENDING states itself
                // instead of inviting the same loop again.
                let kyc = auth.session?.kycStatus ?? "NONE"
                if kyc == "PENDING" {
                    HStack(spacing: 9) {
                        Image(systemName: "clock.fill")
                        Text(L.kycPending.t).font(Brand.font(13.5, .medium))
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(Brand.deep)
                    .padding(13)
                    .background(Brand.gold.opacity(0.16),
                                in: RoundedRectangle(cornerRadius: 12))
                } else if kyc != "APPROVED" {
                    Button { showKyc = true } label: {
                        HStack(spacing: 9) {
                            Image(systemName: "person.badge.shield.checkmark")
                            Text(L.verifyToBook.t)
                                .font(Brand.font(13.5, .medium))
                                .multilineTextAlignment(.leading)
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.forward").font(.system(size: 12))
                        }
                        .foregroundStyle(Brand.deep)
                        .padding(13)
                        .background(Brand.gold.opacity(0.16),
                                    in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                }

                if !salon.services.isEmpty {
                    section(L.chooseServices) {
                        FlowLayout(spacing: 8) {
                            ForEach(salon.services, id: \.self) { service in
                                ServiceChip(
                                    name: service,
                                    price: salon.pricePerService[service],
                                    isSelected: selectedServices.contains(service)
                                ) {
                                    if selectedServices.contains(service) {
                                        selectedServices.remove(service)
                                    } else {
                                        selectedServices.insert(service)
                                    }
                                    selectedSlot = nil
                                }
                            }
                        }
                    }
                }

                section(L.chooseDay) {
                    // Anchored to the leading edge, which mirrors: in a
                    // right-to-left layout that is the RIGHT edge, where today
                    // sits. Without it the row opens scrolled to the far end
                    // and the first day she sees is next week — verified on the
                    // simulator, where today was off-screen and Tuesday was the
                    // first thing visible.
                    // Full-bleed, with the inset moved onto the scroll CONTENT.
                    // Inside the page's 22pt padding the first chip — today —
                    // was clipped by the viewport edge, so the one day she is
                    // most likely to want was the one she could not read.
                    // contentMargins gives the row its own breathing space and
                    // lets the last chip run to the edge, which is also the
                    // honest signal that there are more days to scroll to.
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 9) {
                            ForEach(days, id: \.timeIntervalSince1970) { day in
                                DayChip(day: day,
                                        isSelected: DayGrid.kabulCalendar.isDate(
                                            day, inSameDayAs: selectedDay)) {
                                    selectedDay = day
                                    selectedSlot = nil
                                    Task { await loadSlots() }
                                }
                            }
                        }
                    }
                    .contentMargins(.horizontal, 22, for: .scrollContent)
                    .padding(.horizontal, -22)
                    .defaultScrollAnchor(.leading)
                }

                section(L.chooseTime) {
                    if isLoadingSlots {
                        ProgressView().tint(Brand.accent)
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else if slotsUnavailable {
                        // Three reasons now, not two. "Could not load" is not
                        // "fully booked", and it is certainly not a free day.
                        Text(L.couldNotLoad.t)
                            .font(Brand.font(14))
                            .foregroundStyle(Color(hex: 0xC0392B))
                    } else if availableSlots.isEmpty {
                        // Two different reasons, two different sentences: a
                        // salon that is shut that day is not a salon that is
                        // fully booked, and telling her the wrong one wastes
                        // her time on the other six days.
                        Text(isClosedToday ? L.closedThatDay.t : L.noTimesLeft.t)
                            .font(Brand.font(14))
                            .foregroundStyle(Brand.accent)
                    } else {
                        FlowLayout(spacing: 8) {
                            ForEach(availableSlots, id: \.self) { slot in
                                TimeChip(millis: slot, isSelected: selectedSlot == slot) {
                                    selectedSlot = slot
                                }
                            }
                        }
                    }
                }

                if total > 0 {
                    HStack {
                        Text(L.total.t).font(Brand.font(15, .medium))
                        Spacer()
                        HStack(spacing: 4) {
                            Text(verbatim: "\(total)")
                                .environment(\.layoutDirection, .leftToRight)
                            Text(L.afn.t)
                        }
                        .font(Brand.font(17, .bold))
                    }
                    .foregroundStyle(Brand.ink)
                    .padding(.top, 4)
                }

                if !reviews.isEmpty {
                    section(L.reviews) {
                        VStack(alignment: .leading, spacing: 12) {
                            ForEach(reviews) { ReviewRow(review: $0) }
                        }
                    }
                }

            }
            .padding(.horizontal, 22)
            .padding(.top, 12)
            .padding(.bottom, 12)
        }
        .background(Brand.cream.ignoresSafeArea())
        // The one action this page exists for, pinned rather than scrolled to.
        // It sat at the bottom of the content, so on a salon with services,
        // seven days, a full grid and reviews it was several screens below the
        // time she had just chosen — and the moment after choosing is exactly
        // when she is ready to book. safeAreaInset keeps the scroll content
        // clear of it rather than covering the last row.
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 6) {
                // The total, so the price is in front of her at the moment she
                // commits rather than only inside the sheet that follows.
                if total > 0 {
                    HStack(spacing: 4) {
                        Text(L.total.t).font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                        Text(verbatim: "\(total)")
                            .font(Brand.font(14, .bold)).foregroundStyle(Brand.ink)
                            .environment(\.layoutDirection, .leftToRight)
                        Text(L.afn.t).font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                    }
                }
                BrandButton(title: .book,
                            isEnabled: !selectedServices.isEmpty && selectedSlot != nil
                                       && auth.session?.kycStatus == "APPROVED") {
                    showBooking = true
                }
            }
            .padding(.horizontal, 22)
            .padding(.top, 10)
            .padding(.bottom, 6)
            .background(.regularMaterial)
        }
        .navigationTitle(salon.salonName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { showChat = true } label: {
                    Image(systemName: "bubble.left.and.bubble.right")
                }
                .accessibilityLabel(L.messageSalon.t)
            }
        }
        .task { await loadSlots(); await loadReviews() }
        .sheet(isPresented: $showChat) { SalonChatView(salon: salon) }
        .sheet(isPresented: $showBooking, onDismiss: {
            // The grid is redrawn on return, so a slot someone else took while
            // she was deciding stops being offered.
            Task { await loadSlots() }
        }) {
            if let slot = selectedSlot {
                BookingSheet(salon: salon,
                             serviceNames: Array(selectedServices),
                             startMillis: slot)
            }
        }
        .sheet(isPresented: $showKyc) { KycView() }
    }

    /// The salon's reviews, newest first.
    ///
    /// `allow read: if isSignedIn()` covers this, and the query is bounded —
    /// a salon with hundreds would otherwise pull all of them to show five.
    private func loadReviews() async {
        guard let snap = try? await Firestore.firestore()
                .collection("reviews")
                .whereField("salonId", isEqualTo: salon.id)
                .limit(to: 20)
                .getDocuments()
        else { return }
        let docs = snap.documents.map { (id: $0.documentID, data: $0.data()) }
        // Sorted here rather than with orderBy: createdAt is written by the
        // server but an older review without it would be DROPPED by an ordered
        // query rather than sorted last, and a salon losing its earliest
        // reviews is worse than an unindexed sort over twenty rows.
        reviews = DocumentDecoding.decodeAll(
            Review.self, documents: docs, assigningID: { $0.id = $1 })
            .values
            .sorted { $0.createdAt > $1.createdAt }
    }

    private var isClosedToday: Bool {
        let weekday = DayGrid.weekday(of: selectedDay)
        return !(salon.workingHours.first { $0.dayOfWeek == weekday }?.isOpen ?? false)
    }

    /// The salon's own photo, full width. Same field the list now uses, and
    /// the same reason: the room is what she is choosing. Shown only when
    /// there is one — a placeholder band of grey at the top of every salon
    /// that has not uploaded a photo makes the page look broken rather than
    /// plain.
    @ViewBuilder
    private var cover: some View {
        if let url = URL(string: salon.coverImageUrl), !salon.coverImageUrl.isEmpty {
            AsyncImage(url: url) { phase in
                if case .success(let image) = phase {
                    image.resizable().scaledToFill()
                } else {
                    Brand.petal.opacity(0.35)
                }
            }
            .frame(height: 180)
            .frame(maxWidth: .infinity)
            .clipped()
            .accessibilityHidden(true)   // decorative; the name is right below it
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(salon.salonName).font(Brand.font(22, .bold)).foregroundStyle(Brand.ink)
                if salon.isVerified {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(Brand.gold)
                        .accessibilityLabel(L.verified.t)
                }
            }
            HStack(spacing: 10) {
                if salon.rating > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 12)).foregroundStyle(Brand.gold)
                        Text(String(format: "%.1f", salon.rating))
                            .font(Brand.font(13, .medium)).foregroundStyle(Brand.ink)
                            .environment(\.layoutDirection, .leftToRight)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(L.ratingLabel(salon.rating, salon.confirmedCount))
                }
                if !salon.district.isEmpty {
                    Text(salon.district).font(Brand.font(13)).foregroundStyle(Brand.accent)
                }
            }
        }
    }

    @ViewBuilder
    private func section(_ title: L, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.t).font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
            content()
        }
    }

    /// Asks the server what is taken, rather than reading appointments directly.
    ///
    /// `getBookedSlots` returns only {time, staffId, isParty, id} — no customer
    /// names, no phone numbers. Reading the appointments collection would need
    /// permissions a customer does not have and should not have: who else is
    /// booked at this salon today is not her business.
    private func loadSlots() async {
        isLoadingSlots = true
        defer { isLoadingSlots = false }

        let start = DayGrid.dayStart(selectedDay)
        let end = start.addingTimeInterval(24 * 3600)
        // Not `try?`. A swallowed failure here left `booked` empty, and an empty
        // booked list means "nothing is taken" — so a salon that is full renders
        // as a whole day of free times, and she picks one the server refuses.
        // The read failing is a different thing from the diary being empty and
        // she is told which.
        let response: JSON?
        do {
            response = try await Callables.call("getBookedSlots", [
                "salonId": .string(salon.id),
                "dayStart": .int(Int(start.timeIntervalSince1970 * 1000)),
                "dayEnd": .int(Int(end.timeIntervalSince1970 * 1000)),
            ])
            slotsUnavailable = false
        } catch {
            slotsUnavailable = true
            booked = []
            return
        }

        // Each entry becomes a one-slot appointment: the server has already
        // expanded multi-slot bookings into individual busy times, so nothing
        // here needs to re-expand them.
        booked = (response?["booked"]?.arrayValue ?? []).compactMap { entry in
            guard let time = entry["time"]?.intValue else { return nil }
            var a = Appointment()
            a.id = entry["id"]?.stringValue ?? ""
            a.appointmentDate = Int64(time)
            a.staffId = entry["staffId"]?.stringValue ?? ""
            a.isParty = entry["isParty"]?.boolValue ?? false
            a.slotsCount = 1
            a.status = .confirmed
            return a
        }
    }
}
