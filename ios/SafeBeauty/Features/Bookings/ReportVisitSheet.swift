import SwiftUI
import SafeBeautyCore

/// What she says happened, mirroring what a salon can already say about her.
///
/// `reportCustomer` has let a salon rate its customer, mark her a no-show and
/// flag her for misconduct since this product shipped, and an admin can suspend
/// her over it. She had nothing. Meanwhile a booking becomes COMPLETED two
/// hours after its start time whether or not anyone served her, and the
/// platform's commission is booked on that.
struct ReportVisitSheet: View {
    let appointment: Appointment

    @Environment(\.dismiss) private var dismiss

    @State private var reason = "NOT_SERVED"
    @State private var note = ""
    @State private var working = false
    @State private var error: String?
    @State private var sent = false

    private static let reasons: [(code: String, label: () -> String)] = [
        ("NOT_SERVED", { L.visitNotServed.t }),
        ("TURNED_AWAY", { L.visitTurnedAway.t }),
        ("DIFFERENT_SERVICE", { L.visitDifferentService.t }),
        ("OVERCHARGED", { L.visitOvercharged.t }),
        ("SAFETY", { L.visitSafety.t }),
        ("OTHER", { L.reportOther.t }),
    ]

    var body: some View {
        NavigationStack {
            Group { if sent { confirmation } else { form } }
                .background(Brand.cream.ignoresSafeArea())
                .navigationTitle(L.reportVisitTitle.t)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(sent ? L.close.t : L.cancel.t) { dismiss() }
                            .foregroundStyle(Brand.accent).disabled(working)
                    }
                }
        }
    }

    private var confirmation: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 40)).foregroundStyle(Brand.success)
            Text(L.reportVisitSentTitle.t)
                .font(Brand.font(17, .bold)).foregroundStyle(Brand.ink)
            // What happens next, including the money — and that the salon is
            // not told who complained. She is the one who has to go back there.
            Text(L.reportVisitSentBody.t)
                .font(Brand.font(14)).foregroundStyle(Brand.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Which visit, so she cannot report the wrong one from a list.
                VStack(alignment: .leading, spacing: 3) {
                    Text(appointment.salonName)
                        .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                    Text(TimeChip.dateLabel(appointment.appointmentDate))
                        .font(Brand.font(12.5)).foregroundStyle(Brand.textMuted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(13)
                .background(.white, in: RoundedRectangle(cornerRadius: 13))

                Text(L.reportVisitWhy.t)
                    .font(Brand.font(12, .semibold)).foregroundStyle(Brand.accent)

                VStack(spacing: 0) {
                    ForEach(Self.reasons, id: \.code) { item in
                        Button { reason = item.code } label: {
                            HStack {
                                Text(item.label())
                                    .font(Brand.font(15)).foregroundStyle(Brand.ink)
                                    .multilineTextAlignment(.leading)
                                Spacer(minLength: 8)
                                if reason == item.code {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(Brand.accent)
                                }
                            }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if item.code != Self.reasons.last?.code {
                            Divider().background(Brand.petal.opacity(0.5))
                        }
                    }
                }
                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))

                TextField(L.reportNotePlaceholder.t, text: $note, axis: .vertical)
                    .font(Brand.font(15)).foregroundStyle(Brand.ink)
                    .lineLimit(2...5)
                    .padding(13)
                    .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))

                ErrorBanner(message: error)

                BrandButton(title: .reportSubmit, isLoading: working, isEnabled: !working) {
                    Task { await submit() }
                }
                Spacer(minLength: 20)
            }
            .padding(.horizontal, 24).padding(.top, 16)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func submit() async {
        working = true; defer { working = false }
        error = nil
        do {
            _ = try await Callables.call("reportVisit", [
                "appointmentId": .string(appointment.id),
                "reason": .string(reason),
                "note": .string(note.trimmingCharacters(in: .whitespaces)),
            ])
            sent = true
        } catch let e as Callables.CallableError {
            // The server's refusals are codes now — too early, too late,
            // already reported — so she is told which one rather than a blanket
            // "cannot report", which is how a person concludes the app is on
            // the salon's side.
            self.error = e.localized ?? L.errNetwork.t
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
