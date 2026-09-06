import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Moving a booking, which iOS could not do at all.
///
/// `rescheduleAppointment` has been deployed the whole time and Android puts a
/// «تغییر زمان» button on every live booking card. On iPhone the only way to
/// change a time was to cancel and book again — which loses her place, and on a
/// paid booking means a refund and a second payment for the same visit.
///
/// Uses the same SlotPicker the salon page uses, on purpose: a reschedule screen
/// with its own idea of which slots are free is exactly the second opinion that
/// offers a time the server then refuses.
struct RescheduleSheet: View {
    let booking: Appointment
    let onMoved: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var salon: Salon?
    @State private var isLoadingSalon = true
    @State private var selectedDay = Date()
    @State private var selectedSlot: Int64?
    @State private var isWorking = false
    @State private var error: String?
    @State private var done = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // What she is moving away from, so the picker below is a
                    // comparison rather than a blank choice.
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L.rescheduleFrom.t)
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.ink.opacity(0.75))
                        Text(TimeChip.label(booking.appointmentDate))
                            .font(Brand.font(16, .bold))
                            .foregroundStyle(Brand.ink)
                        Text(booking.salonName)
                            .font(Brand.font(13)).foregroundStyle(Brand.accent)
                    }

                    if done {
                        ErrorBanner(message: L.rescheduleDone.t, tone: .notice)
                    } else if isLoadingSalon {
                        ProgressView().tint(Brand.accent)
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else if let salon {
                        SlotPicker(salon: salon,
                                   // The services already on the booking, so a
                                   // two-hour job still asks for two hours.
                                   serviceNames: booking.serviceNames,
                                   // It cannot conflict with itself; without
                                   // this the hour she currently holds shows as
                                   // taken, by her.
                                   excluding: booking.id,
                                   selectedDay: $selectedDay,
                                   selectedSlot: $selectedSlot)
                    } else {
                        Text(L.couldNotLoad.t)
                            .font(Brand.font(14)).foregroundStyle(Color(hex: 0xC0392B))
                    }

                    ErrorBanner(message: error)

                    if !done {
                        BrandButton(title: .reschedule,
                                    isLoading: isWorking,
                                    isEnabled: selectedSlot != nil && !isWorking) {
                            Task { await submit() }
                        }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 26).padding(.top, 18)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.reschedule.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(done ? L.close.t : L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                        .disabled(isWorking)
                }
            }
        }
        .task { await loadSalon() }
    }

    /// The booking carries the salon's name but not its working hours, its
    /// blocked days or its slot length — and every one of those decides what
    /// times may be offered.
    private func loadSalon() async {
        isLoadingSalon = true
        defer { isLoadingSalon = false }
        let snap = try? await Firestore.firestore()
            .document("salons/\(booking.salonId)").getDocument()
        guard let snap, snap.exists, let raw = snap.data() else { return }
        var decoded = try? DocumentDecoding.decode(Salon.self, from: raw)
        decoded?.id = snap.documentID
        salon = decoded
    }

    private func submit() async {
        guard let slot = selectedSlot else { return }
        isWorking = true; defer { isWorking = false }
        error = nil
        do {
            _ = try await Callables.call("rescheduleAppointment", [
                "appointmentId": .string(booking.id),
                "newDate": .int(Int(slot)),
            ])
            done = true
            onMoved()
        } catch let e as Callables.CallableError {
            // The server's own reasons, said in her language rather than passed
            // through in English. SALON_CLOSED and the slot collision are both
            // things she can act on — pick another day, pick another time — and
            // "something went wrong" would tell her neither.
            if case .failedPrecondition(let message, let reason) = e {
                if reason == "SALON_CLOSED" { error = L.rescheduleClosed.t }
                else if message.lowercased().contains("no longer") { error = L.rescheduleTooLate.t }
                else { error = L.rescheduleTaken.t }
            } else {
                error = L.errNetwork.t
            }
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
