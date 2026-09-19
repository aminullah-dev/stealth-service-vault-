import SwiftUI
import FirebaseFirestore

/// Who she has blocked, and the way back.
///
/// Apple asks for a block mechanism; a block with no way to undo it is a
/// mechanism that punishes a mis-tap forever. It also has to be findable
/// somewhere other than the thread she blocked from, because that thread may be
/// the first thing that disappeared.
///
/// Names are read one at a time from `users` and `salons` rather than stored on
/// the block: a name copied at block time goes stale, and this list is short.
struct BlockedAccountsSheet: View {
    let moderation: Moderation

    @Environment(\.dismiss) private var dismiss
    @State private var names: [String: String] = [:]

    var body: some View {
        NavigationStack {
            Group {
                if moderation.blocked.isEmpty {
                    ContentUnavailableView {
                        Text(L.blockedEmpty.t)
                            .font(Brand.font(15, .medium)).foregroundStyle(Brand.ink)
                    }
                } else {
                    List {
                        ForEach(Array(moderation.blocked).sorted(), id: \.self) { id in
                            HStack {
                                Text(names[id] ?? "…")
                                    .font(Brand.font(14.5)).foregroundStyle(Brand.ink)
                                Spacer()
                                Button(L.unblockAction.t) {
                                    Task { try? await moderation.unblock(id) }
                                }
                                .font(Brand.font(13, .medium))
                                .foregroundStyle(Brand.accent)
                                .buttonStyle(.borderless)
                            }
                            .listRowBackground(Brand.cream)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.blockedTitle.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
            .task(id: moderation.blocked) { await loadNames() }
        }
    }

    /// Names, from the two places a customer is actually allowed to read one.
    ///
    /// Not from `users/{uid}`: the rules let her read only her own user
    /// document, so that read is denied for every person she has blocked. And
    /// not `fullName`, which is not a field these documents have — the users
    /// collection stores `name`. The first version did both, so every row read
    /// "Blocked accounts", the sheet's own title, and two blocked commenters
    /// were two identical rows she could not tell apart.
    ///
    /// A salon has a public name. A customer's is denormalized onto whatever
    /// she wrote — `post_comments.authorName`, `reviews.customerName` — which
    /// is readable, and is also the name shown beside the words that made her
    /// block them.
    private func loadNames() async {
        let db = Firestore.firestore()
        for id in moderation.blocked where names[id] == nil {
            if let salon = try? await db.document("salons/\(id)").getDocument(),
               let name = salon.data()?["salonName"] as? String, !name.isEmpty {
                names[id] = name
                continue
            }
            if let snap = try? await db.collection("post_comments")
                .whereField("userId", isEqualTo: id).limit(to: 1).getDocuments(),
               let name = snap.documents.first?.data()["authorName"] as? String,
               !name.isEmpty {
                names[id] = name
                continue
            }
            if let snap = try? await db.collection("reviews")
                .whereField("customerId", isEqualTo: id).limit(to: 1).getDocuments(),
               let name = snap.documents.first?.data()["customerName"] as? String,
               !name.isEmpty {
                names[id] = name
                continue
            }
            // Someone whose every comment and review has since been removed.
            // The row still has to be liftable, so it keeps the id — which is
            // ugly and distinguishable, where the title was neither.
            names[id] = id
        }
    }
}
