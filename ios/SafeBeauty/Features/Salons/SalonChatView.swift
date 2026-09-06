import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Messaging the salon, which iOS had no way to do.
///
/// Android has had this since the beginning and the rules have always allowed
/// it — `chat_messages` with a conversationId of `{customerId}_{salonId}`, read
/// and written by either participant via `isParticipantInConversation`. Only
/// the screen was missing, so an iPhone customer could reach platform support
/// and not the salon she was actually booking with. Asking "do you have
/// anything on Thursday?" or "I will be ten minutes late" had to happen outside
/// the app, which on this product means handing over a phone number.
///
/// Deliberately not a second implementation of the thread: it writes the same
/// message shape SupportView writes and reuses MessageBubble. What differs is
/// the conversationId and the absence of a support ticket — a salon thread is
/// not a queue entry for the platform admin.
struct SalonChatView: View {
    let salon: Salon

    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var isSending = false
    @State private var listener: ListenerRegistration?

    /// The id the rules parse: parts[0] must be her, parts[1] the salon.
    private var conversationId: String { "\(auth.session?.uid ?? "")_\(salon.id)" }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSending
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(messages) { message in
                                MessageBubble(
                                    message: message,
                                    isMine: message.senderId == auth.session?.uid
                                )
                                .id(message.id)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                    }
                    .onChange(of: messages.count) { _, _ in
                        if let last = messages.last?.id {
                            withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                        }
                    }
                    // And once on open. onChange fires only on a CHANGE, so a
                    // thread whose messages had already loaded opened at the
                    // oldest one — the same bug the support thread had.
                    .onAppear {
                        if let last = messages.last?.id { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }

                if messages.isEmpty {
                    Text(L.chatFirstMessage.t)
                        .font(Brand.font(13.5))
                        .foregroundStyle(Brand.accent)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40).padding(.bottom, 12)
                }

                composer
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(salon.salonName)
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
            TextField("", text: $draft, axis: .vertical)
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
        guard let uid = auth.session?.uid, !uid.isEmpty else { return }
        listener?.remove()
        listener = Firestore.firestore().collection("chat_messages")
            .whereField("conversationId", isEqualTo: conversationId)
            .order(by: "timestamp")
            .limit(toLast: 100)
            .addSnapshotListener { snapshot, _ in
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                messages = DocumentDecoding.decodeAll(
                    ChatMessage.self, documents: docs, assigningID: { $0.id = $1 }).values
            }
    }

    private func send() async {
        guard let session = auth.session else { return }
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        isSending = true
        defer { isSending = false }
        draft = ""

        do {
            try await Firestore.firestore().collection("chat_messages").addDocument(data: [
                "conversationId": conversationId,
                // Denormalised so the salon can query its own threads: the
                // conversation id ends with the salon, and Firestore cannot
                // match a suffix. Without it a salon owner has no way to list
                // the conversations she is in — which is why messages were
                // written and read by nobody.
                "salonId": salon.id,
                "senderId": session.uid,
                "senderName": session.name,
                "content": text,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            ])
        } catch {
            // Put it back in the field rather than losing what she typed. The
            // rules refuse a conversationId that is not hers, and a refusal
            // that silently ate the message would look like it had sent.
            draft = text
        }
    }
}
