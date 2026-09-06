import SwiftUI
import FirebaseFirestore
import SafeBeautyCore

/// What the salons are showing and offering.
struct FeedView: View {
    @State private var posts: [SalonPost] = []
    @State private var offers: [SalonOffer] = []
    @State private var loadFailed = false
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && posts.isEmpty && offers.isEmpty {
                    ProgressView().tint(Brand.accent)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if loadFailed && posts.isEmpty && offers.isEmpty {
                    // A read that failed is not an empty feed. Both were
                    // showing the same reassuring sentence.
                    ContentUnavailableView {
                        Text(L.couldNotLoad.t)
                            .font(Brand.font(17, .medium))
                            .foregroundStyle(Color(hex: 0xC0392B))
                    }
                } else if posts.isEmpty && offers.isEmpty {
                    ContentUnavailableView {
                        Text(L.feedEmpty.t)
                            .font(Brand.font(16, .medium))
                            .foregroundStyle(Brand.ink)
                    }
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 16) {
                            if !offers.isEmpty {
                                Text(L.offers.t)
                                    .font(Brand.font(16, .bold))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.horizontal, 18)
                                ForEach(offers) { OfferCard(offer: $0) }
                            }
                            if !posts.isEmpty {
                                Text(L.latest.t)
                                    .font(Brand.font(16, .bold))
                                    .foregroundStyle(Brand.ink)
                                    .padding(.horizontal, 18)
                                    .padding(.top, offers.isEmpty ? 0 : 6)
                                ForEach(posts) { PostCard(post: $0) }
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
        .task { await load() }
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

struct PostCard: View {
    let post: SalonPost

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
                if post.likeCount > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "heart.fill").font(.system(size: 11))
                        Text(verbatim: "\(post.likeCount)")
                            .environment(\.layoutDirection, .leftToRight)
                    }
                    .font(Brand.font(12)).foregroundStyle(Brand.accent)
                }
            }
            .padding(13)
        }
        .background(.white, in: RoundedRectangle(cornerRadius: 16))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
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
