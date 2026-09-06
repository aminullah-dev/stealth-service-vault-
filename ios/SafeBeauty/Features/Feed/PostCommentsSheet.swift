import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// The conversation under a salon's photo, which iOS had no way to read or join.
///
/// `post_comments` has rules, an index and an Android screen; on iPhone the
/// comment count on a card was a number pointing at nothing. That is the whole
/// social half of Discover — a customer could look at a salon's work and not
/// say anything about it, and the salon never heard from her.
struct PostCommentsSheet: View {
    let post: SalonPost

    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var comments: [PostComment] = []
    @State private var draft = ""
    @State private var isSending = false
    @State private var error: String?
    @State private var listener: ListenerRegistration?

    private var trimmed: String { draft.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canSend: Bool { !trimmed.isEmpty && trimmed.count <= 300 && !isSending }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        if comments.isEmpty {
                            Text(L.noComments.t)
                                .font(Brand.font(14)).foregroundStyle(Brand.accent)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.top, 30)
                        }
                        ForEach(comments) { comment in
                            CommentRow(comment: comment,
                                       isMine: comment.userId == auth.session?.uid) {
                                Task { await delete(comment) }
                            }
                        }
                    }
                    .padding(.horizontal, 18).padding(.vertical, 14)
                }

                if let error {
                    ErrorBanner(message: error).padding(.horizontal, 14)
                }
                composer
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.comments.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
            }
        }
        .task { start() }
        .onDisappear { listener?.remove(); listener = nil }
    }

    private var composer: some View {
        HStack(spacing: 10) {
            TextField(L.writeComment.t, text: $draft, axis: .vertical)
                .font(Brand.font(15))
                .foregroundStyle(Brand.ink)
                .lineLimit(1...4)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(.white, in: RoundedRectangle(cornerRadius: 20))

            Button { Task { await send() } } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(canSend ? Brand.accent : Brand.petal)
            }
            .disabled(!canSend)
            .accessibilityLabel(L.send.t)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(.regularMaterial)
    }

    private func start() {
        listener?.remove()
        listener = Firestore.firestore().collection("post_comments")
            .whereField("postId", isEqualTo: post.id)
            .order(by: "createdAt")
            // Bounded like every other listener here. A photo with more than
            // two hundred comments is a happy problem this app does not have.
            .limit(toLast: 200)
            .addSnapshotListener { snapshot, _ in
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                comments = DocumentDecoding.decodeAll(
                    PostComment.self, documents: docs, assigningID: { $0.id = $1 }).values
            }
    }

    private func send() async {
        guard let session = auth.session else { return }
        let text = trimmed
        guard !text.isEmpty else { return }
        if text.count > 300 { error = L.commentTooLong.t; return }
        isSending = true; defer { isSending = false }
        error = nil
        draft = ""
        do {
            try await Firestore.firestore().collection("post_comments").addDocument(data: [
                "postId": post.id,
                // Read back through the post by the rules, so it has to be the
                // salon this photo actually belongs to.
                "salonId": post.salonId,
                "userId": session.uid,
                // Checked against her own user document. A name that does not
                // match the stored one is refused, which is what stops a comment
                // being signed with somebody else's.
                "authorName": session.name,
                "text": text,
                "createdAt": Int(Date().timeIntervalSince1970 * 1000),
            ])
        } catch {
            // Put it back rather than losing what she typed.
            draft = text
            self.error = L.errNetwork.t
        }
    }

    private func delete(_ comment: PostComment) async {
        do {
            try await Firestore.firestore().document("post_comments/\(comment.id)").delete()
        } catch {
            self.error = L.errNetwork.t
        }
    }
}

private struct CommentRow: View {
    let comment: PostComment
    let isMine: Bool
    let onDelete: () -> Void

    @State private var confirming = false

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Text(comment.authorName)
                    .font(Brand.font(13.5, .bold)).foregroundStyle(Brand.ink)
                Spacer(minLength: 0)
                // Retracting her own. The salon and the admin moderate through
                // their own tools; this is only ever her own comment.
                if isMine {
                    Button { confirming = true } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 12)).foregroundStyle(Brand.accent)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L.deleteComment.t)
                }
            }
            Text(comment.text)
                .font(Brand.font(14)).foregroundStyle(Brand.ink.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.white, in: RoundedRectangle(cornerRadius: 12))
        .confirmationDialog(L.deleteComment.t, isPresented: $confirming, titleVisibility: .visible) {
            Button(L.deleteComment.t, role: .destructive, action: onDelete)
            Button(L.cancel.t, role: .cancel) {}
        }
    }
}
