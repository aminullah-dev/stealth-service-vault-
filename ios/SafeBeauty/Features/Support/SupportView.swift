import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Talking to support.
///
/// An ordinary chat under `support_{uid}`, plus a ticket document that puts her
/// in the admin's queue. Both are direct writes, which the rules allow narrowly:
/// she may create a message only where `senderId == me()` and only in a
/// conversation she is a participant of, and she may write only the ticket whose
/// document id is her own uid.
///
/// The thread is one conversation forever, but closing a ticket draws a line
/// through it: the server archives each closed conversation into
/// `support_tickets/{uid}/history`, and this screen shows only what came after
/// the newest line. Nothing is moved, so an older build still showing the whole
/// thread is showing true data — just all of it.
struct SupportView: View {
    @Environment(AuthService.self) private var auth
    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var isSending = false
    @State private var listener: ListenerRegistration?

    @State private var history: [SupportConversation] = []
    /// Until the history's first snapshot, which messages are "current" is not
    /// known — showing the whole thread and then snatching the old part away
    /// is worse than a moment of spinner.
    @State private var historyLoaded = false
    @State private var historyListener: ListenerRegistration?
    @State private var showHistory = false
    /// "Not now", and the thank-you having run its course. For this view
    /// session only: she is asked again the next time she opens support.
    @State private var hiddenRatingId: String?
    /// The row being rated, kept on screen while the write settles.
    @State private var pinnedRatingId: String?

    private var conversationId: String {
        ChatMessage.supportConversationId(for: auth.session?.uid ?? "")
    }

    /// The conversation still running: messages after the newest close.
    private var currentMessages: [ChatMessage] {
        historyLoaded ? SupportConversation.currentMessages(messages, history: history) : []
    }

    /// The just-closed conversation, when it is waiting for her rating.
    /// Backfilled rows are never offered — nobody is asked about a
    /// conversation from before this existed.
    private var ratingCandidate: SupportConversation? {
        guard let newest = SupportConversation.newest(history),
              newest.id != hiddenRatingId else { return nil }
        return newest.canBeRated || newest.id == pinnedRatingId ? newest : nil
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !historyLoaded {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if currentMessages.isEmpty, let candidate = ratingCandidate {
                    // In place of the empty state, and above a composer that
                    // still works: writing simply starts the next conversation.
                    ScrollView {
                        SupportRatingCard(
                            conversation: candidate,
                            title: .rateConversationTitle,
                            onSubmitting: { pinnedRatingId = candidate.id },
                            onRated: {
                                let id = candidate.id
                                Task {
                                    try? await Task.sleep(for: .seconds(2.5))
                                    withAnimation { hiddenRatingId = id }
                                }
                            },
                            onNotNow: { withAnimation { hiddenRatingId = candidate.id } })
                        .id(candidate.id)
                        .padding(.horizontal, 16).padding(.top, 24)
                    }
                    .scrollDismissesKeyboard(.interactively)
                } else if currentMessages.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bubble.left.and.bubble.right")
                            .font(.system(size: 34)).foregroundStyle(Brand.accent)
                        Text(L.supportIntro.t)
                            .font(Brand.font(14))
                            .foregroundStyle(Brand.ink.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollViewReader { proxy in
                        ScrollView {
                            LazyVStack(spacing: 9) {
                                ForEach(currentMessages) { message in
                                    MessageBubble(
                                        message: message,
                                        isMine: message.senderId == auth.session?.uid)
                                    .id(message.id)
                                }
                            }
                            .padding(.horizontal, 16).padding(.vertical, 12)
                        }
                        // Newest message in view when the thread opens and when
                        // one arrives. A support thread she has to scroll to
                        // read the answer of is a support thread she stops using.
                        .onChange(of: currentMessages.count) { _, _ in
                            if let last = currentMessages.last?.id {
                                withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                            }
                        }
                        // And once when the thread opens. onChange only fires on
                        // a CHANGE, so a thread whose messages had already
                        // loaded opened at the oldest one — she had to scroll
                        // down to find the answer she came back for.
                        .onAppear {
                            if let last = currentMessages.last?.id {
                                proxy.scrollTo(last, anchor: .bottom)
                            }
                        }
                    }
                }

                composer
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.support.t)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showHistory = true } label: {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundStyle(Brand.accent)
                    }
                    .accessibilityLabel(L.supportHistory.t)
                }
            }
            .sheet(isPresented: $showHistory) {
                SupportHistoryView(history: history).appDirection()
            }
        }
        .task(id: auth.session?.uid) { start() }
        .onDisappear {
            listener?.remove(); listener = nil
            historyListener?.remove(); historyListener = nil
        }
    }

    private var composer: some View {
        HStack(spacing: 9) {
            TextField(L.typeMessage.t, text: $draft, axis: .vertical)
                .font(Brand.font(15))
                .lineLimit(1...4)
                .padding(.horizontal, 13).padding(.vertical, 10)
                .background(Brand.surface, in: RoundedRectangle(cornerRadius: 20))

            Button {
                Task { await send() }
            } label: {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: 30))
                    // Mirrors, so in a right-to-left thread the send arrow does
                    // not point back at the text she just typed.
                    .foregroundStyle(canSend ? Brand.accent : Brand.petal)
            }
            .disabled(!canSend || isSending)
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .background(Brand.cream)
    }

    private var canSend: Bool {
        !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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

        historyListener?.remove()
        // Bounded like every other read, newest first. closedAt is written on
        // every row the server creates, so ordering by it drops nothing.
        historyListener = Firestore.firestore()
            .collection("support_tickets").document(uid).collection("history")
            .order(by: "closedAt", descending: true)
            .limit(to: 50)
            .addSnapshotListener { snapshot, _ in
                if let snapshot {
                    let docs = snapshot.documents.map { (id: $0.documentID, data: $0.data()) }
                    history = DocumentDecoding.decodeAll(
                        SupportConversation.self, documents: docs,
                        assigningID: { $0.id = $1 }).values
                }
                // Set on an error too. With no history readable the whole
                // thread is shown, which is what this screen did before —
                // never a spinner that does not end.
                historyLoaded = true
            }
    }

    private func send() async {
        guard let session = auth.session else { return }
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        isSending = true
        defer { isSending = false }
        // Cleared optimistically: if the write fails she still has the text in
        // the thread's absence rather than a field that silently kept it.
        draft = ""

        let db = Firestore.firestore()
        do {
            try await db.collection("chat_messages").addDocument(data: [
                "conversationId": conversationId,
                "senderId": session.uid,
                "senderName": session.name,
                "content": text,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            ])
            // The ticket is the admin's queue entry, not the conversation. Set
            // after the message so a ticket never appears with nothing behind
            // it, and merged so an existing thread keeps its history.
            try await db.collection("support_tickets").document(session.uid).setData([
                "id": session.uid,
                "userId": session.uid,
                "userName": session.name,
                "userRole": session.role,
                "status": "OPEN",
                "updatedAt": Int(Date().timeIntervalSince1970 * 1000),
                "unreadForAdmin": true,
            ], merge: true)
        } catch {
            // Put the text back rather than losing what she wrote.
            draft = text
        }
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    let isMine: Bool

    var body: some View {
        HStack {
            if isMine { Spacer(minLength: 40) }
            VStack(alignment: isMine ? .trailing : .leading, spacing: 3) {
                Text(message.content)
                    .font(Brand.font(14.5))
                    .foregroundStyle(isMine ? .white : Brand.ink)
                Text(Self.time(message.date))
                    .font(.system(size: 10))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(isMine ? .white.opacity(0.75) : Brand.accent)
            }
            .padding(.horizontal, 13).padding(.vertical, 9)
            // Brand.chipInactive, not Color.white — the other party's bubble was a
            // stark white rectangle on a dark screen. Same fix as the salon
            // list's filter chips.
            .background(isMine ? AnyShapeStyle(Brand.gradient) : AnyShapeStyle(Brand.chipInactive),
                        in: RoundedRectangle(cornerRadius: 15))
            if !isMine { Spacer(minLength: 40) }
        }
    }

    private static func time(_ date: Date) -> String {
        let f = DateFormatter()
        f.timeZone = DayGrid.kabul
        f.locale = AppLanguage.current.locale
        f.setLocalizedDateFormatFromTemplate("HH:mm")
        return f.string(from: date)
    }
}
