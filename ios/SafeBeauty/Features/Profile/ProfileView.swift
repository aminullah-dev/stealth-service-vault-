import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Her account: who she is to the product, what she is owed, and how to leave.
///
/// Read straight from her own users document, which the rules permit
/// (`allow get: if isSignedIn() && ownsDoc(uid)`) and nothing else. The wallet
/// and loyalty figures are server-written and displayed as found — a client
/// that recomputed a balance would eventually disagree with the one money is
/// actually settled against.
struct ProfileView: View {
    @Environment(AuthService.self) private var auth

    @State private var referralCredit = 0
    @State private var loyaltyPoints = 0
    @State private var referralCode = ""
    @State private var phone = ""
    @State private var showKyc = false
    @State private var confirmSignOut = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    identityCard
                    walletCard
                    if !referralCode.isEmpty { inviteCard }
                    verificationRow

                    Button(role: .destructive) { confirmSignOut = true } label: {
                        Text(L.signOut.t)
                            .font(Brand.font(15, .medium))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(.white, in: RoundedRectangle(cornerRadius: 13))
                    }
                    .padding(.top, 6)
                    .padding(.bottom, 30)
                }
                .padding(.horizontal, 22)
                .padding(.top, 14)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.profile.t)
            .sheet(isPresented: $showKyc) { KycView() }
            .alert(L.signOut.t, isPresented: $confirmSignOut) {
                Button(L.cancel.t, role: .cancel) {}
                Button(L.signOut.t, role: .destructive) { auth.signOut() }
            } message: {
                Text(L.signOutWarning.t)
            }
        }
        .task(id: auth.session?.uid) { await load() }
    }

    private var identityCard: some View {
        VStack(spacing: 8) {
            Circle()
                .fill(Brand.gradient)
                .frame(width: 66, height: 66)
                .overlay(
                    Text(String(auth.session?.name.prefix(1) ?? ""))
                        .font(Brand.font(26, .bold)).foregroundStyle(.white)
                )
            Text(auth.session?.name ?? "")
                .font(Brand.font(19, .bold)).foregroundStyle(Brand.ink)
            if !phone.isEmpty {
                // A phone number reads left-to-right in every language.
                Text(phone)
                    .font(Brand.font(13))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.accent)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    private var walletCard: some View {
        HStack(spacing: 0) {
            statTile(L.walletCredit.t, "\(referralCredit)", suffix: L.afn.t)
            Divider().frame(height: 40).overlay(Brand.petal.opacity(0.5))
            statTile(L.loyaltyPoints.t, "\(loyaltyPoints)", suffix: nil)
        }
        .padding(.vertical, 15)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    private func statTile(_ label: String, _ value: String, suffix: String?) -> some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                Text(value)
                    .font(Brand.font(21, .bold))
                    .environment(\.layoutDirection, .leftToRight)
                if let suffix {
                    Text(suffix).font(Brand.font(12)).foregroundStyle(Brand.accent)
                }
            }
            .foregroundStyle(Brand.ink)
            Text(label).font(Brand.font(12)).foregroundStyle(Brand.accent)
        }
        .frame(maxWidth: .infinity)
    }

    private var inviteCard: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(L.yourInviteCode.t).font(Brand.font(13, .medium))
                .foregroundStyle(Brand.ink.opacity(0.75))
            HStack {
                Text(referralCode)
                    .font(.system(size: 19, weight: .bold, design: .monospaced))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.deep)
                Spacer()
                // Copy, not share. A share sheet posts it somewhere public by
                // default, and the invite code is tied to one account's credit —
                // it is hers to hand to someone, not to broadcast.
                Button {
                    UIPasteboard.general.string = referralCode
                } label: {
                    Image(systemName: "doc.on.doc").foregroundStyle(Brand.accent)
                }
            }
            Text(L.inviteExplain.t)
                .font(Brand.font(11.5)).foregroundStyle(Brand.accent)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private var verificationRow: some View {
        let status = auth.session?.kycStatus ?? "NONE"
        Button {
            // Only NONE and REJECTED can act. PENDING is waiting on a person
            // and APPROVED is done, and submitKyc refuses both — so the row
            // shows state rather than offering a button that would be refused.
            if status == "NONE" || status == "REJECTED" { showKyc = true }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: status == "APPROVED" ? "checkmark.shield.fill"
                                : status == "PENDING" ? "clock.fill" : "shield")
                    .foregroundStyle(status == "APPROVED" ? Brand.deep : Brand.gold)
                Text(L.verifyIdentity.t).font(Brand.font(14.5, .medium)).foregroundStyle(Brand.ink)
                Spacer()
                Text(statusLabel(status)).font(Brand.font(13)).foregroundStyle(Brand.accent)
                if status == "NONE" || status == "REJECTED" {
                    Image(systemName: "chevron.forward").font(.system(size: 12))
                        .foregroundStyle(Brand.accent)
                }
            }
            .padding(15)
            .background(.white, in: RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }

    private func statusLabel(_ s: String) -> String {
        switch s {
        case "APPROVED": L.kycApproved.t
        case "PENDING": L.kycPending.t
        case "REJECTED": L.kycRejected.t
        default: L.kycNone.t
        }
    }

    private func load() async {
        guard let uid = auth.session?.uid, !uid.isEmpty,
              let snap = try? await Firestore.firestore().document("users/\(uid)").getDocument(),
              let d = snap.data()
        else { return }
        // Displayed as the server wrote them. referralCredit is real money she
        // can spend at checkout, and loyaltyPoints convert into it at 100 —
        // neither is a number this app should be deriving.
        referralCredit = (d["referralCredit"] as? Int) ?? Int((d["referralCredit"] as? Double) ?? 0)
        loyaltyPoints = (d["loyaltyPoints"] as? Int) ?? 0
        referralCode = (d["referralCode"] as? String) ?? ""
        phone = (d["phone"] as? String) ?? ""
    }
}
