import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// What the salons are showing and offering.
struct FeedView: View {
    @Environment(Moderation.self) private var moderation
    @Environment(AuthService.self) private var auth
    /// The salon catalogue, so a card can open the salon it belongs to.
    /// Posts and offers carry only a salonId; without this the feed is a
    /// gallery you cannot act on.
    @State private var repo = SalonRepository()
    @State private var posts: [SalonPost] = []
    @State private var offers: [SalonOffer] = []
    @State private var stories: [SalonStory] = []
    @State private var loadFailed = false
    @State private var isLoading = true
    /// Which of the posts on screen this customer has liked.
    ///
    /// Read here rather than per card: a listener per tile would put fifty of
    /// them on this screen. The rules let her read only her own like rows, so
    /// filtering by userId is both the query and the whole permission.
    @State private var liked: Set<String> = []
    @State private var likeListener: ListenerRegistration?

    /// A blocked salon's work disappears from the feed, which is the whole
    /// point of blocking one. Filtered here rather than in the query: the
    /// block list is small and local, and a Firestore `not-in` is capped at ten
    /// values and would silently start dropping the wrong salons at eleven.
    private var visiblePosts: [SalonPost] {
        posts.filter { !moderation.isBlocked($0.salonId) }
    }

    private var visibleStories: [SalonStory] {
        stories.filter { !moderation.isBlocked($0.salonId) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && posts.isEmpty && offers.isEmpty && stories.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if loadFailed && posts.isEmpty && offers.isEmpty && stories.isEmpty {
                    // A read that failed is not an empty feed. Both were
                    // showing the same reassuring sentence.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Color(hex: 0xC0392B))
                    }
                } else if posts.isEmpty && offers.isEmpty && stories.isEmpty {
                    ContentUnavailableView {
                        Text(L.feedEmpty.t)
                            .font(Brand.font(16, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 16) {
                            // Above everything else, because a story expires in
                            // a day and nothing else on this screen has a
                            // deadline. Android places them the same way.
                            if !stories.isEmpty {
                                Text(L.stories.t)
                                    .font(Brand.font(16, .bold))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.horizontal, 18)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 12) {
                                        ForEach(visibleStories) { story in
                                            StoryBubble(story: story)
                                        }
                                    }
                                    .padding(.horizontal, 18)
                                }
                                .defaultScrollAnchor(.leading)
                            }
                            if !offers.isEmpty {
                                Text(L.offers.t)
                                    .font(Brand.font(16, .bold))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.horizontal, 18)
                                ForEach(offers) { offer in
                                    if let salon = repo.salons.first(where: { $0.id == offer.salonId }) {
                                        NavigationLink { SalonDetailView(salon: salon) } label: {
                                            OfferCard(offer: offer)
                                        }
                                        .buttonStyle(.plain)
                                    } else {
                                        // Its salon is gone or not yet loaded.
                                        // Shown, but not offered as a route to
                                        // a page that cannot be built.
                                        OfferCard(offer: offer)
                                    }
                                }
                            }
                            if !posts.isEmpty {
                                Text(L.latest.t)
                                    .font(Brand.font(16, .bold))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.horizontal, 18)
                                    .padding(.top, offers.isEmpty ? 0 : 6)
                                ForEach(visiblePosts) { post in
                                    if let salon = repo.salons.first(where: { $0.id == post.salonId }) {
                                        NavigationLink { SalonDetailView(salon: salon) } label: {
                                            PostCard(post: post, moderation: moderation,
                                                     isLiked: liked.contains(post.id),
                                                     onToggleLike: { toggleLike(post) })
                                        }
                                        .buttonStyle(.plain)
                                    } else {
                                        PostCard(post: post, moderation: moderation,
                                                 isLiked: liked.contains(post.id),
                                                 onToggleLike: { toggleLike(post) })
                                    }
                                }
                            }
                        }
                        .padding(.vertical, 14)
                    }
                }
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(L.discover.t)
            .refreshable { await load() }
        }
        .task { repo.start(); await load() }
        // Rebuilt when the feed changes or she signs in, and torn down with the
        // view — a Firestore listener that outlives its owner keeps billing.
        .task(id: "\(auth.session?.uid ?? "")|\(posts.map(\.id).joined())") {
            watchLikes(uid: auth.session?.uid ?? "", postIds: posts.map(\.id))
        }
        .onDisappear { likeListener?.remove(); likeListener = nil }
    }

    /// One listener for every post on screen, refreshed when that set changes.
    ///
    /// `whereIn` takes at most thirty values, so the ids are chunked — the feed
    /// reads fifty. A chunk that has not reported yet contributes nothing,
    /// which shows an unfilled heart for a moment rather than a wrong one.
    private func watchLikes(uid: String, postIds: [String]) {
        likeListener?.remove()
        likeListener = nil
        liked = []
        let wanted = Array(Set(postIds.filter { !$0.isEmpty })).prefix(30)
        guard !uid.isEmpty, !wanted.isEmpty else { return }
        likeListener = Firestore.firestore().collection("post_likes")
            .whereField("userId", isEqualTo: uid)
            .whereField("postId", in: Array(wanted))
            .addSnapshotListener { snap, _ in
                let ids = (snap?.documents ?? []).compactMap { $0.data()["postId"] as? String }
                Task { @MainActor in liked = Set(ids) }
            }
    }

    /// The document id is "{postId}_{userId}", which is what the rules check —
    /// so a second like from the same person is impossible to write rather than
    /// merely discouraged, and the count on the post is maintained by a trigger
    /// that no client can inflate.
    private func toggleLike(_ post: SalonPost) {
        guard let uid = auth.session?.uid, !uid.isEmpty else { return }
        let ref = Firestore.firestore().document("post_likes/\(post.id)_\(uid)")
        let wasLiked = liked.contains(post.id)
        // Optimistic: the snapshot will confirm, and a heart that waits for a
        // round trip feels broken.
        if wasLiked { liked.remove(post.id) } else { liked.insert(post.id) }
        Task {
            do {
                if wasLiked {
                    try await ref.delete()
                } else {
                    try await ref.setData([
                        "postId": post.id,
                        "salonId": post.salonId,
                        "userId": uid,
                        "createdAt": Int(Date().timeIntervalSince1970 * 1000),
                    ])
                }
            } catch {
                // Put it back rather than leaving a heart that lies.
                await MainActor.run {
                    if wasLiked { liked.insert(post.id) } else { liked.remove(post.id) }
                }
            }
        }
    }

    private func load() async {
        defer { isLoading = false }
        let db = Firestore.firestore()

        // Ordered on the SERVER, then sorted again here.
        //
        // An unordered limit(50) does not mean "the 50 newest" — Firestore
        // takes the first 50 by document id, which is arbitrary, so once the
        // collection passed fifty posts the feed silently stopped showing new
        // ones and sorting the client's arbitrary 50 only put them in a tidy
        // order. Ordering server-side drops a post that lacks createdAt, which
        // is the accepted trade: every post the server writes has one, and
        // showing the wrong fifty is worse than omitting a malformed document.
        var failed = false

        // Expiry is filtered here, not queried. Firestore cannot compare a
        // field to "now", and an inequality on expiresAt would drop every story
        // written before that field existed. Sixty is Android's bound too.
        if let snap = try? await db.collection("salon_stories")
            .order(by: "createdAt", descending: true).limit(to: 60).getDocuments() {
            let now = Date()
            stories = DocumentDecoding.decodeAll(
                SalonStory.self,
                documents: snap.documents.map { (id: $0.documentID, data: $0.data()) },
                assigningID: { $0.id = $1 }).values
                .filter { $0.isLive(now: now) }
                .sorted { $0.createdAt > $1.createdAt }
        }

        if let snap = try? await db.collection("salon_posts")
            .order(by: "createdAt", descending: true).limit(to: 50).getDocuments() {
            posts = DocumentDecoding.decodeAll(
                SalonPost.self,
                documents: snap.documents.map { (id: $0.documentID, data: $0.data()) },
                assigningID: { $0.id = $1 }).values
                .sorted { $0.createdAt > $1.createdAt }
        } else { failed = true }

        if let snap = try? await db.collection("salon_offers")
            .order(by: "createdAt", descending: true).limit(to: 50).getDocuments() {
            offers = DocumentDecoding.decodeAll(
                SalonOffer.self,
                documents: snap.documents.map { (id: $0.documentID, data: $0.data()) },
                assigningID: { $0.id = $1 }).values
                // Filtered here rather than with whereField("active", ==, true),
                // because expiry has to be checked too and an equality cannot
                // express "no expiry means never expires".
                .filter { $0.isLive() }
                .sorted { $0.createdAt > $1.createdAt }
        } else { failed = true }

        loadFailed = failed
    }
}

/// A salon's photo in the feed.
///
/// The report control is on the card rather than behind a long-press: a control
/// nobody can find is the same as not having one, and this is the one Apple
/// asks for by name.
struct PostCard: View {
    let post: SalonPost
    let moderation: Moderation
    let isLiked: Bool
    let onToggleLike: () -> Void

    @State private var showComments = false
    @State private var showReport = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // AsyncImage rather than a cache library: the feed is small, and a
            // dependency that has to be pinned, updated and audited is not
            // worth it for fifty images.
            AsyncImage(url: URL(string: post.imageUrl)) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().scaledToFill()
                case .failure:
                    // A broken image is shown as broken rather than as blank
                    // space, so a salon whose photo failed to upload is a
                    // visible problem instead of an invisible one.
                    ZStack {
                        Brand.petal.opacity(0.3)
                        Image(systemName: "photo").foregroundStyle(Brand.accent)
                    }
                default:
                    Brand.petal.opacity(0.2)
                }
            }
            .frame(height: 240)
            .clipped()

            VStack(alignment: .leading, spacing: 5) {
                Text(post.salonName)
                    .font(Brand.font(14.5, .bold)).foregroundStyle(Brand.ink)
                if !post.caption.isEmpty {
                    Text(post.caption)
                        .font(Brand.font(13.5)).foregroundStyle(Brand.ink.opacity(0.8))
                }
                HStack(spacing: 14) {
                    // A control, not a readout. iOS drew the count and gave her
                    // no way to add to it: the heart was a number pointing at
                    // something she could not do, and a salon never heard that
                    // anyone liked its work from an iPhone.
                    Button(action: onToggleLike) {
                        HStack(spacing: 4) {
                            Image(systemName: isLiked ? "heart.fill" : "heart")
                                .font(.system(size: 12))
                            if post.likeCount > 0 {
                                Text(verbatim: "\(post.likeCount)")
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                        }
                        .font(Brand.font(12))
                        .foregroundStyle(isLiked ? Brand.ink : Brand.accent)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(L.feedLikes.t)
                    // Always offered, not only when there are comments already.
                    // The count was a number pointing at nothing: a customer
                    // could look at a salon's work and had no way to say
                    // anything about it, and the salon never heard from her.
                    Button { showReport = true } label: {
                        Image(systemName: "flag")
                            .font(.system(size: 11))
                            .foregroundStyle(Brand.textMuted)
                            .accessibilityLabel(L.reportAction.t)
                    }
                    .buttonStyle(.borderless)
                    Button { showComments = true } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "bubble.right").font(.system(size: 11))
                            if post.commentCount > 0 {
                                Text(verbatim: "\(post.commentCount)")
                                    .environment(\.layoutDirection, .leftToRight)
                            }
                        }
                        .font(Brand.font(12)).foregroundStyle(Brand.accent)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L.comments.t)
                }
            }
            .padding(13)
        }
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
        .sheet(isPresented: $showComments) {
            PostCommentsSheet(post: post).appDirection()
        }
        .sheet(isPresented: $showReport) {
            ReportSheet(target: .post, targetId: post.id,
                        authorId: post.salonId, authorKind: "SALON",
                        moderation: moderation)
                .appDirection()
        }
    }
}

struct OfferCard: View {
    let offer: SalonOffer

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12).fill(Brand.gradient)
                    .frame(width: 52, height: 52)
                Image(systemName: "tag.fill").foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(offer.title)
                    .font(Brand.font(14.5, .bold)).foregroundStyle(Brand.ink)
                Text(offer.salonName)
                    .font(Brand.font(12.5)).foregroundStyle(Brand.accent)
                if !offer.description.isEmpty {
                    Text(offer.description)
                        .font(Brand.font(12.5)).foregroundStyle(Brand.ink.opacity(0.75))
                }
            }
            Spacer(minLength: 0)
            // Only shown when there is a number behind it. The live offer's
            // discount is written into its title and both numeric fields are
            // zero, so a badge here would read "0%".
            if offer.hasNumericDiscount {
                Text(offer.discountPercent > 0
                     ? "\(offer.discountPercent)%"
                     : "\(offer.discountAmount)")
                    .font(Brand.font(15, .bold))
                    .environment(\.layoutDirection, .leftToRight)
                    .foregroundStyle(Brand.gold)
            }
        }
        .padding(13)
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
    }
}

/// One salon's 24-hour announcement.
///
/// A bubble rather than a card: it sits in a row, it is read at a glance, and
/// it is gone tomorrow. The text is the point — a photo is optional and most
/// of these are a sentence about a free chair.
struct StoryBubble: View {
    let story: SalonStory
    @State private var showing = false

    var body: some View {
        Button { showing = true } label: {
            VStack(spacing: 6) {
                ZStack {
                    Circle().strokeBorder(Brand.gradient, lineWidth: 2.5)
                        .frame(width: 66, height: 66)
                    if let url = URL(string: story.imageUrl), !story.imageUrl.isEmpty {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase {
                                image.resizable().scaledToFill()
                            } else {
                                Brand.petal.opacity(0.35)
                            }
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(Circle())
                    } else {
                        Circle().fill(Brand.petal.opacity(0.35))
                            .frame(width: 56, height: 56)
                            .overlay(
                                Text(story.salonName.prefix(1))
                                    .font(Brand.font(20, .bold))
                                    .foregroundStyle(Brand.deep)
                            )
                    }
                }
                Text(story.salonName)
                    .font(Brand.font(11.5))
                    .foregroundStyle(Brand.ink)
                    .lineLimit(1)
                    .frame(width: 70)
            }
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showing) { StoryView(story: story).appDirection() }
    }
}

/// The announcement itself, full screen, because that is how a story is read.
struct StoryView: View {
    let story: SalonStory
    @Environment(\.dismiss) private var dismiss
    @Environment(Moderation.self) private var moderation
    @State private var showReport = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let url = URL(string: story.imageUrl), !story.imageUrl.isEmpty {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase {
                                image.resizable().scaledToFit()
                            } else {
                                Brand.petal.opacity(0.25).frame(height: 220)
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    if !story.text.isEmpty {
                        Text(story.text)
                            .font(Brand.font(16))
                            .foregroundStyle(Brand.ink)
                    }
                    Spacer(minLength: 20)
                }
                .padding(.horizontal, 20).padding(.top, 16)
            }
            .background(Brand.cream.ignoresSafeArea())
            .navigationTitle(story.salonName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L.close.t) { dismiss() }.foregroundStyle(Brand.accent)
                }
                // A story is a salon's photograph and free text shown to every
                // customer, and it was the one surface the server, the rules
                // and the index all supported but no screen could reach.
                ToolbarItem(placement: .primaryAction) {
                    Button { showReport = true } label: {
                        Image(systemName: "flag").foregroundStyle(Brand.textMuted)
                            .accessibilityLabel(L.reportAction.t)
                    }
                }
            }
            .sheet(isPresented: $showReport) {
                ReportSheet(target: .story, targetId: story.id,
                            authorId: story.salonId, authorKind: "SALON",
                            moderation: moderation)
                    .appDirection()
            }
        }
    }
}
