import SwiftUI
import SafeBeautyCore

/// The salon's stylists, which only the web console could edit.
///
/// This is not a nicety on iPhone. The booking screen has a stylist picker, and
/// `hasSlotConflict` treats each distinct `staffId` as its own chair — so a
/// salon with no staff has exactly one chair, the phantom one that `""` names,
/// and two customers can never be served at the same hour no matter how many
/// people work there. A salon owner who only has a phone could not fix that.
///
/// Removing a stylist does not touch bookings already made against her: those
/// keep her id and stay blocked, which is right — the appointment happened, and
/// freeing the slot would double-book whoever covers it.
struct EditStaffSheet: View {
    let repo: ProviderRepository

    @Environment(\.dismiss) private var dismiss

    @State private var staff: [StaffMember] = []
    @State private var newName = ""
    @State private var newSpecialty = ""
    @State private var working = false
    @State private var error: String?
    @State private var loaded = false

    private var trimmedName: String {
        newName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(L.staffHint.t)
                        .font(Brand.font(12.5))
                        .foregroundStyle(Brand.textMuted)
                        .fixedSize(horizontal: false, vertical: true)

                    if staff.isEmpty {
                        Text(L.staffEmpty.t)
                            .font(Brand.font(13.5))
                            .foregroundStyle(Brand.accent)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 18)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(staff) { member in
                                row(member)
                            }
                        }
                    }

                    Divider().background(Brand.petal)

                    VStack(spacing: 9) {
                        TextField(L.staffNameLabel.t, text: $newName)
                            .font(Brand.font(15)).foregroundStyle(Brand.ink)
                            .padding(13)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
                        TextField(L.staffSpecialtyLabel.t, text: $newSpecialty)
                            .font(Brand.font(15)).foregroundStyle(Brand.ink)
                            .padding(13)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
                        Button(L.staffAdd.t) { add() }
                            .font(Brand.font(14.5, .medium))
                            .foregroundStyle(trimmedName.isEmpty ? Brand.textFaint : Brand.accent)
                            .disabled(trimmedName.isEmpty)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
                    }

                    ErrorBanner(message: error)

                    BrandButton(title: .save, isLoading: working) {
                        Task { await save() }
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 22).padding(.top, 14)
            }
            .background(Brand.cream.ignoresSafeArea())
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(L.staffTitle.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.cancel.t) { dismiss() }
                        .foregroundStyle(Brand.accent).disabled(working)
                }
            }
            .task {
                // Once. Re-reading on every repository tick would throw away
                // what she has typed each time a booking arrives.
                guard !loaded else { return }
                staff = repo.salon?.staff ?? []
                loaded = true
            }
        }
    }

    private func row(_ member: StaffMember) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(member.name)
                    .font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                if !member.specialty.isEmpty {
                    Text(member.specialty)
                        .font(Brand.font(12)).foregroundStyle(Brand.textMuted)
                }
            }
            Spacer()
            Button {
                staff.removeAll { $0.id == member.id }
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 13)).foregroundStyle(Brand.danger)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(L.staffRemove.t)
        }
        .padding(13)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 13))
    }

    private func add() {
        guard !trimmedName.isEmpty else { return }
        // A fresh UUID, never derived from the name: the id is what bookings
        // store, so a name corrected next week must not orphan them.
        staff.append(StaffMember(
            id: UUID().uuidString,
            name: trimmedName,
            specialty: newSpecialty.trimmingCharacters(in: .whitespacesAndNewlines)))
        newName = ""
        newSpecialty = ""
    }

    private func save() async {
        working = true; defer { working = false }
        error = nil
        do {
            try await repo.saveStaff(staff)
            dismiss()
        } catch {
            self.error = L.errNetwork.t
        }
    }
}
