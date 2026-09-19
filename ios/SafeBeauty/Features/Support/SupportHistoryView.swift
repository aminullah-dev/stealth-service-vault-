import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// Her closed support conversations, newest first.
///
/// Given the rows rather than listening itself: SupportView already holds the
/// bounded history listener, and a second one on the same 50 documents would be
/// the same read billed twice. The sheet's content is re-evaluated when that
/// listener updates, so a rating given here shows on the row when she goes back.
struct SupportHistoryView: View {
    let history: [SupportConversation]

    @Environment(\.dismiss) private var dismiss

    private var rows: [SupportConversation] {
        history.sorted { $0.closedAt > $1.closedAt }
    }

    var body: some View {
        NavigationStack {
            Group {
                if rows.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 34)).foregroundStyle(Brand.accent)
                            .accessibilityHidden(true)
                        Text(L.supportHistoryEmpty.t)
                            .font(Brand.font(14))
                            .foregroundStyle(Brand.ink.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(rows) { conversation in
                                NavigationLink(value: conversation.id) {
                                    SupportHistoryRow(conversation: conversation)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                    }
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.supportHistory.t)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }
                        .foregroundStyle(Brand.accent)
                }
            }
            // By id, looked up in the live rows, so a pushed transcript sees
            // the row the listener last delivered rather than a copy from the
            // moment she tapped.
            .navigationDestination(for: String.self) { id in
                if let conversation = history.first(where: { $0.id == id }) {
                    SupportConversationView(conversation: conversation)
                }
            }
        }
    }
}

private struct SupportHistoryRow: View {
    let conversation: SupportConversation

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(Self.period(conversation))
                        .font(Brand.font(13.5, .medium))
                        .foregroundStyle(Brand.ink)
                    Spacer(minLength: 6)
                    SupportRatingStars(rating: conversation.rating)
                }
                if !conversation.lastMessage.isEmpty {
                    Text(conversation.lastMessage)
                        .font(Brand.font(13))
                        .foregroundStyle(Brand.ink.opacity(0.8))
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                Text(L.supportMessagesCount(conversation.messageCount))
                    .font(Brand.font(12))
                    .foregroundStyle(Brand.textMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // chevron.forward, not .right: it mirrors to point the way the
            // navigation goes in a right-to-left layout.
            Image(systemName: "chevron.forward")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Brand.textFaint)
                .accessibilityHidden(true)
        }
        .padding(14)
        .background(Brand.surface, in: RoundedRectangle(cornerRadius: 14))
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    /// The day it closed — or the span, when it ran across days. An interval
    /// formatter collapses a same-day span to one date by itself, and orders a
    /// span the way the language does. In Kabul, like every date in the app.
    static func period(_ c: SupportConversation) -> String {
        let f = DateIntervalFormatter()
        f.timeZone = DayGrid.kabul
        f.locale = AppLanguage.current.locale
        f.dateTemplate = "d MMM y"
        let start = c.openedAt > 0 && c.openedAt <= c.closedAt ? c.openedDate : c.closedDate
        return f.string(from: start, to: c.closedDate)
    }
}

/// One past conversation, read-only.
///
/// A one-time read rather than a listener: a closed conversation's messages
/// are fixed by its `openedAt...closedAt`, and a listener would be a
/// registration to leak for nothing that can change. The query uses the index
/// every thread already uses (conversationId ASC, timestamp DESC), bounded and
/// reversed here.
struct SupportConversationView: View {
    let conversation: SupportConversation

    @Environment(AuthService.self) private var auth
    @State private var messages: [ChatMessage] = []
    @State private var isLoading = true
    @State private var failed = false
    /// Keeps the card on screen once she has submitted — see
    /// SupportRatingCard.onSubmitting.
    @State private var ratingPinned = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 9) {
                HStack(spacing: 8) {
                    Text(SupportHistoryRow.period(conversation))
                        .font(Brand.font(12.5))
                        .foregroundStyle(Brand.textMuted)
                    Spacer()
                    SupportRatingStars(rating: conversation.rating)
                }
                .padding(.bottom, 4)

                if isLoading {
                    ProgressView().tint(Brand.accent).padding(.vertical, 30)
                } else if failed {
                    ErrorBanner(message: L.couldNotLoad.t)
                    Button(L.retry.t) { Task { await load() } }
                        .font(Brand.font(14, .medium))
                        .foregroundStyle(Brand.accent)
                        .frame(minHeight: 44)
                } else {
                    ForEach(messages) { message in
                        MessageBubble(message: message,
                                      isMine: message.senderId == auth.session?.uid)
                    }
                }

                if conversation.canBeRated || ratingPinned {
                    SupportRatingCard(
                        conversation: conversation,
                        title: .rateThisConversation,
                        onSubmitting: { ratingPinned = true })
                    .padding(.top, 14)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Brand.cream.ignoresSafeArea())
        .navigationTitle(L.supportConversation.t)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: conversation.id) { await load() }
    }

    private func load() async {
        guard let uid = auth.session?.uid, !uid.isEmpty else { return }
        isLoading = true
        failed = false
        defer { isLoading = false }
        do {
            let snapshot = try await Firestore.firestore().collection("chat_messages")
                .whereField("conversationId", isEqualTo: ChatMessage.supportConversationId(for: uid))
                .whereField("timestamp", isGreaterThanOrEqualTo: conversation.openedAt)
                .whereField("timestamp", isLessThanOrEqualTo: conversation.closedAt)
                .order(by: "timestamp", descending: true)
                .limit(to: 300)
                .getDocuments()
            let docs = snapshot.documents.map { (id: $0.documentID, data: $0.data()) }
            messages = DocumentDecoding.decodeAll(
                ChatMessage.self, documents: docs, assigningID: { $0.id = $1 }).values
                .filter(conversation.contains)
                .sorted { $0.timestamp < $1.timestamp }
        } catch {
            failed = true
        }
    }
}
