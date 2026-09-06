import SwiftUI
import SafeBeautyCore

struct NotificationsView: View {
    @Environment(AuthService.self) private var auth
    @State private var repo = NotificationsRepository()

    var body: some View {
        NavigationStack {
            Group {
                if repo.items.isEmpty && !repo.isLoading && repo.error != nil {
                    // "Nothing here" and "we could not look" are different
                    // sentences. The listener already records which; the view
                    // was showing the reassuring one either way, on the tab a
                    // customer opens specifically to check whether her salon
                    // has replied.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Color(hex: 0xC0392B))
                    }
                } else if repo.items.isEmpty && !repo.isLoading {
                    ContentUnavailableView {
                        Text(L.noNotifications.t)
                            .font(Brand.font(16, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else {
                    List(repo.items) { item in
                        NotificationRow(item: item)
                            .listRowBackground(item.isRead ? Color.white : Brand.petal.opacity(0.16))
                            .onAppear { Task { await repo.markRead(item) } }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.notifications.t)
            .toolbar {
                if repo.unreadCount > 0 {
                    ToolbarItem(placement: .primaryAction) {
                        Button(L.markAllRead.t) { Task { await repo.markAllRead() } }
                            .font(Brand.font(13, .medium))
                            .foregroundStyle(Brand.accent)
                    }
                }
            }
        }
        .task(id: auth.session?.uid) {
            if let uid = auth.session?.uid { repo.start(recipientId: uid) }
        }
    }
}

struct NotificationRow: View {
    let item: AppNotification

    var body: some View {
        HStack(alignment: .top, spacing: 11) {
            Image(systemName: item.type.symbolName)
                .font(.system(size: 16))
                .foregroundStyle(item.type.isNegative ? Color(hex: 0xC0392B) : Brand.accent)
                .frame(width: 26)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.title)
                    .font(Brand.font(14.5, item.isRead ? .medium : .bold))
                    .foregroundStyle(Brand.ink)
                // The body is written by the server in her language already —
                // it carries service names and amounts, so it is displayed
                // rather than reconstructed.
                if !item.body.isEmpty {
                    Text(item.body)
                        .font(Brand.font(13))
                        .foregroundStyle(Brand.ink.opacity(0.75))
                }
                Text(Self.relative(item.date))
                    .font(Brand.font(11.5))
                    .foregroundStyle(Brand.accent)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 5)
    }

    private static func relative(_ date: Date) -> String {
        let f = RelativeDateTimeFormatter()
        f.locale = AppLanguage.current.locale
        f.unitsStyle = .full
        return f.localizedString(for: date, relativeTo: Date())
    }
}
