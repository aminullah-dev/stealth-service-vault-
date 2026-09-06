import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// The customers who have written to this salon.
///
/// The loop this closes: a customer messages the salon from her phone,
/// `notifyOnChatMessage` writes a notification and sends a push, and the salon
/// owner on an iPhone had nowhere at all to read it. The web console got a
/// Messages tab; the iOS provider app had none, so the notification arrived and
/// led nowhere.
///
/// Threads come from `salonId`, which is why that field is denormalised onto
/// every message: the conversation id ends with the salon and Firestore cannot
/// match a suffix.
struct ProviderMessagesView: View {
    let repo: ProviderRepository

    @State private var messages: [ChatMessage] = []
    @State private var listener: ListenerRegistration?
    @State private var openThread: String?

    /// One row per customer, newest conversation first.
    private var threads: [(id: String, last: ChatMessage, count: Int)] {
        Dictionary(grouping: messages, by: \.conversationId)
            .compactMap { key, value in
                guard let last = value.max(by: { $0.timestamp < $1.timestamp }) else { return nil }
                return (id: key, last: last, count: value.count)
            }
            .sorted { $0.last.timestamp > $1.last.timestamp }
    }

    var body: some View {
        NavigationStack {
            Group {
                if repo.salon == nil {
                    NoSalonYet(repo: repo)
                } else if threads.isEmpty {
                    ContentUnavailableView {
                        Text(L.noMessages.t)
                            .font(Brand.font(16, .medium)).foregroundStyle(Brand.ink)
                    }
                } else {
                    List(threads, id: \.id) { thread in
                        Button { openThread = thread.id } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(thread.last.senderName.isEmpty
                                     ? L.anonymousCustomer.t : thread.last.senderName)
                                    .font(Brand.font(15, .bold)).foregroundStyle(Brand.ink)
                                Text(thread.last.content)
                                    .font(Brand.font(13.5))
                                    .foregroundStyle(Brand.ink.opacity(0.8))
                                    .lineLimit(2)
                                Text(TimeChip.dateLabel(thread.last.timestamp))
                                    .font(Brand.font(12)).foregroundStyle(Brand.accent)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(Color.white)
                    }
                    .listStyle(.insetGrouped)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.tabMessages.t)
            .sheet(item: Binding(get: { openThread.map(ThreadID.init) },
                                 set: { openThread = $0?.id })) { thread in
                ProviderThreadView(conversationId: thread.id,
                                   salonName: repo.salon?.salonName ?? "").appDirection()
            }
        }
        .task(id: repo.salon?.id) { start() }
        .onDisappear { listener?.remove(); listener = nil }
    }

    private struct ThreadID: Identifiable { let id: String }

    private func start() {
        listener?.remove(); listener = nil
        guard let salonId = repo.salon?.id, !salonId.isEmpty else { return }
        listener = Firestore.firestore().collection("chat_messages")
            .whereField("salonId", isEqualTo: salonId)
            .order(by: "timestamp")
            // Bounded like every other listener here. Two hundred messages is
            // several months of a small salon's conversations.
            .limit(toLast: 200)
            .addSnapshotListener { snapshot, _ in
                let docs = (snapshot?.documents ?? []).map { (id: $0.documentID, data: $0.data()) }
                messages = DocumentDecoding.decodeAll(
                    ChatMessage.self, documents: docs, assigningID: { $0.id = $1 }).values
            }
    }
}

/// One conversation, from the salon's side.
///
/// Deliberately not a second implementation of the thread: it writes the same
/// message shape SalonChatView writes and reuses MessageBubble. What differs is
/// who "mine" is.
struct ProviderThreadView: View {
    let conversationId: String
    let salonName: String

    @Environment(AuthService.self) private var auth
    @Environment(\.dismiss) private var dismiss

    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var isSending = false
    @State private var listener: ListenerRegistration?

    /// The salon this thread belongs to — parts[1] of the conversation id, which
    /// is what the rules parse and what the message must carry.
    private var salonId: String {
        let parts = conversationId.split(separator: "_")
        return parts.count == 2 ? String(parts[1]) : ""
    }

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
                                MessageBubble(message: message,
                                              isMine: message.senderId == auth.session?.uid)
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
                    .onAppear {
                        if let last = messages.last?.id { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
                composer
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(messages.first?.senderName ?? L.tabMessages.t)
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
                .font(Brand.font(15)).foregroundStyle(Brand.ink)
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
        isSending = true; defer { isSending = false }
        draft = ""
        do {
            try await Firestore.firestore().collection("chat_messages").addDocument(data: [
                "conversationId": conversationId,
                // Required by the rules and matched against the conversation
                // id's second part, which is also how this thread was found.
                "salonId": salonId,
                "senderId": session.uid,
                "senderName": salonName.isEmpty ? session.name : salonName,
                "content": text,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            ])
        } catch {
            // Back in the field rather than lost. A refusal that silently ate
            // the message would look like it had sent.
            draft = text
        }
    }
}
