package com.safebeauty.app.data.firebase

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.db.dao.SalonCacheDao
import com.safebeauty.app.data.db.entities.toEntity
import com.safebeauty.app.util.CrashReporter
import com.safebeauty.app.util.PhoneUtils
import com.safebeauty.app.util.SearchKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listeners emit an empty result on error (instead of closing the flow), so a
 * transient permission/network error can never propagate up through stateIn and
 * crash the app.
 *
 * This file used to say that every live query deliberately used at most one
 * equality filter and did the rest in memory, to avoid composite indexes and
 * the FAILED_PRECONDITION a missing one produces. That was a reasonable trade
 * while the collections were small, and it stopped being one: it meant the app
 * downloaded the entire salon collection on every open, and it is structurally
 * why there was no ranking — nothing can rank a list the device has already
 * sorted by name.
 *
 * Salon discovery is now server-side, paginated and indexed (see salonPage).
 * The indexes it needs are committed in firestore.indexes.json alongside the
 * queries, which is what makes the old objection no longer apply: an index that
 * ships with its query cannot be the one that is missing.
 */
@Singleton
class FirestoreRepository @Inject constructor(
    private val salonCacheDao: SalonCacheDao
) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    private val usersCol        = db.collection("users")
    private val salonsCol       = db.collection("salons")
    private val appointmentsCol = db.collection("appointments")
    private val chatCol         = db.collection("chat_messages")
    private val reviewsCol      = db.collection("reviews")
    private val broadcastsCol   = db.collection("broadcasts")
    private val galleryCol      = db.collection("salon_gallery")
    private val postsCol        = db.collection("salon_posts")
    private val storiesCol      = db.collection("salon_stories")
    private val postLikesCol    = db.collection("post_likes")
    private val postCommentsCol = db.collection("post_comments")
    // How many Discover tiles to fetch. A grid shows ~9 per screen, so 100
    // is several screens of scrolling before anyone notices an edge.
    private val FEED_PAGE_SIZE  = 100L
    private val COMMENT_PAGE_SIZE = 200L
    private val offersCol       = db.collection("salon_offers")
    private val waitlistCol      = db.collection("waitlist")
    private val notificationsCol = db.collection("notifications")
    private val platformConfigCol  = db.collection("platform_config")
    private val providerBalancesCol = db.collection("provider_balances")
    private val payoutsCol           = db.collection("payouts")
    private val refundRequestsCol    = db.collection("refund_requests")
    private val promoCodesCol        = db.collection("promo_codes")
    private val supportTicketsCol    = db.collection("support_tickets")
    private val favoritesCol         = db.collection("favorites")

    // ── How much of a growing collection a live listener may hold ─────────────
    //
    // A snapshot listener with no limit is a standing promise to download a
    // collection that only ever grows, and to re-send it whenever any one
    // document in it changes. For anything keyed to a person or a salon that
    // promise gets more expensive every month they keep using the app — the
    // customers who stay longest pay the most, which is exactly backwards.
    //
    // These bounds are not arbitrary: each is set to comfortably exceed what its
    // screen can show, so nothing a person can actually reach is missing. Where
    // a limit would change an answer rather than a view — a set of liked posts,
    // a count — the fix is a different query, not a smaller one.
    private val RECENT_NOTIFICATIONS = 100L   // the notification centre
    private val RECENT_APPOINTMENTS  = 100L   // a bookings list, and its badges
    private val ACTIVE_WAITLIST      = 50L    // simultaneous waits, not history
    private val SALON_QUEUE          = 200L   // a salon's unconfirmed bookings
    private val SALON_PORTFOLIO      = 100L   // reviews, photos and offers per salon
    private val PROVIDER_PAYOUTS     = 100L   // a provider's payout history
    private val ADMIN_QUEUE          = 200L   // things awaiting an admin decision
    private val ADMIN_HISTORY        = 200L   // admin lists that are a record, not a queue
    /** An announcement with no expiry set stops showing after this. */
    private val BROADCAST_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    // provider_balances holds one document per provider, so it is bounded by how
    // many salons the platform has rather than by how long it has been running.
    // This is a backstop against that assumption being wrong, not a page.
    private val PROVIDER_LEDGER      = 1000L
    // Same reasoning for the salon directory: one document per business, so it
    // grows with how many salons join, not with how long the platform runs.
    private val SALON_DIRECTORY      = 1000L

    // ── Users ─────────────────────────────────────────────────────────────────

    // NOTE: PIN authentication and the pre-auth phone lookup moved to Cloud
    // Functions (authenticateWithPin / lookupAccountByPhone). The client no
    // longer bulk-reads the users collection, so `users` reads are locked to
    // owner/admin in firestore.rules. getUserById below only ever reads the
    // caller's OWN document.

    /**
     * Registration only — never an update.
     *
     * A whole-document set(), which is right for a document that does not exist
     * yet and wrong for one that does: UserDocument omits phoneDigits, nameKey,
     * suspended and suspendedReason, all of which the rules freeze, so reusing
     * this to save a profile would delete them and be refused. That is not
     * hypothetical — the salon equivalent shipped and broke every provider's
     * Save button. Field-level update() is the path for edits; see updateUserName
     * and the others below.
     */
    suspend fun createUser(user: UserDocument) {
        usersCol.document(user.uid).set(user).await()
    }

    /** True if any account already uses [phone] — the login identifier must be unique. */
    suspend fun phoneExists(phone: String): Boolean =
        runCatching {
            !usersCol.whereEqualTo("phone", phone).limit(1).get().await().isEmpty
        }.getOrDefault(false)

    /**
     * Finish a salon that registration started and never completed.
     *
     * The account is real, the details she typed are on her user document, and
     * the salon simply is not there — because createProviderSalon is called from
     * exactly one place, the registration screen, and a dropped connection there
     * used to end the story. Called once when a provider signs in and has no
     * salon; the callable is idempotent, so a retry that races another retry
     * returns the same salon rather than making a second one.
     *
     * Returns true when a salon now exists.
     */
    suspend fun finishPendingSalon(user: UserDocument): Boolean {
        if (user.pendingSalonName.isBlank() || user.pendingSalonDistrict.isBlank()) return false
        return runCatching {
            functions.getHttpsCallable("createProviderSalon")
                .call(hashMapOf(
                    "salonName" to user.pendingSalonName,
                    "district"  to user.pendingSalonDistrict,
                    "services"  to user.pendingSalonServices
                ))
                .await()
            true
        }.getOrElse { false }
    }

    suspend fun getUserById(uid: String): UserDocument? {
        return usersCol.document(uid).get().await()
            .toObject(UserDocument::class.java)?.copy(uid = uid)
    }

    suspend fun setUserStatus(uid: String, status: String) {
        usersCol.document(uid).update("status", status).await()
    }

    /**
     * Suspend or reinstate an account, through the audited callable.
     *
     * This used to write users.status directly from the device — the same defect
     * the web console had. Nothing on the server reads that field to decide
     * whether an account may act, so the badge turned red and the person went on
     * booking and paying; the action never reached admin_audit; and reinstating
     * wrote APPROVED unconditionally, promoting any provider who had been
     * suspended while still awaiting review.
     *
     * adminSetUserStatus writes both halves of a suspension, records who did it
     * and why, and restores the standing the account actually had.
     */
    suspend fun setSuspended(uid: String, suspend: Boolean, reason: String = "") {
        val payload = hashMapOf<String, Any>("targetUid" to uid, "suspend" to suspend)
        if (suspend) payload["reason"] = reason
        functions.getHttpsCallable("adminSetUserStatus").call(payload).await()
    }

    suspend fun suspendUser(uid: String, reason: String) = setSuspended(uid, true, reason)
    suspend fun unsuspendUser(uid: String)               = setSuspended(uid, false)

    /** Rejects a pending provider application with a reason shown on their AccountStatusScreen. */
    suspend fun rejectProvider(uid: String, reason: String) {
        usersCol.document(uid)
            .update(mapOf("status" to "REJECTED", "rejectionReason" to reason))
            .await()
    }

    suspend fun updateUserName(uid: String, name: String) {
        usersCol.document(uid).update("name", name).await()
    }

    // NOTE: PIN hash updates go through the updatePinHash Cloud Function — a
    // direct client write can be rules-denied after the Firebase Auth password
    // has already changed (Forgot-PIN signs in fresh, before uid_map exists),
    // which would desync the two credentials and lock the account out.

    /** Legacy Base64 path — kept so existing photos still display after migration. */
    suspend fun updateUserPhoto(uid: String, base64: String) {
        usersCol.document(uid).update("profilePhotoBase64", base64).await()
    }

    /** New Storage path — stores the HTTPS download URL returned by [StorageRepository]. */
    suspend fun updateUserPhotoUrl(uid: String, url: String) {
        usersCol.document(uid).update("profilePhotoUrl", url).await()
    }

    /** Provider's HesabPay account number, so the admin knows where to send payouts. */
    suspend fun updateHesabAccountNumber(uid: String, hesabAccountNumber: String) {
        usersCol.document(uid).update("hesabAccountNumber", hesabAccountNumber).await()
    }

    // Loyalty points are awarded server-side (confirmAppointment) and are now
    // frozen against client writes in firestore.rules — so the old client-side
    // incrementLoyaltyPoints() writer was removed (it was unused and would now
    // be denied). The client only ever reads the points below.

    fun observeUserLoyaltyPoints(uid: String): Flow<Int> = callbackFlow {
        val listener = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { trySend(0); return@addSnapshotListener }
            trySend(snap?.getLong("loyaltyPoints")?.toInt() ?: 0)
        }
        awaitClose { listener.remove() }
    }

    /** Live view of a single user's own document (used to gate on kycStatus). */
    fun observeUser(uid: String): Flow<UserDocument?> = callbackFlow {
        val listener = usersCol.document(uid).addSnapshotListener { snap, err ->
            if (err != null) { trySend(null); return@addSnapshotListener }
            trySend(snap?.toObject(UserDocument::class.java)?.copy(uid = snap.id))
        }
        awaitClose { listener.remove() }
    }

    /** Users awaiting KYC review, oldest first — for the admin verification tab. */
    fun observeKycPending(): Flow<List<UserDocument>> = callbackFlow {
        val listener = usersCol
            .whereEqualTo("kycStatus", "PENDING")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(ADMIN_QUEUE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(UserDocument::class.java)?.copy(uid = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        runCatching { usersCol.document(uid).update("fcmToken", token).await() }
    }

    /**
     * Records the language this user reads, so server-sent notifications can be
     * written in it. Without this the Cloud Functions have no way to know, and
     * every push falls back to Dari. Best-effort: a failure here must never
     * block sign-in.
     */
    suspend fun updateLanguage(uid: String, lang: String) {
        runCatching { usersCol.document(uid).update("lang", lang).await() }
    }

    fun observePendingProviders(): Flow<List<UserDocument>> = callbackFlow {
        val listener = usersCol
            .whereEqualTo("status", "PENDING")
            // The role filter ran after the fact, so approving providers read
            // every pending account of any kind.
            .whereEqualTo("role", "PROVIDER")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(ADMIN_QUEUE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(UserDocument::class.java)?.copy(uid = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Salons ────────────────────────────────────────────────────────────────

    /**
     * Live stream of available salons.
     * On success: writes to local cache for offline use.
     * On error: falls back to the local cache so the UI stays populated.
     */
    fun observeAvailableSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val scope = this
        // Deliberately unordered and generously bounded. This is no longer the
        // list anyone browses — salonPage does that — it is the local directory
        // findSalon looks a booking's salon up in, and an ordering with a limit
        // would decide which salons are findable. The bound is a backstop
        // against the assumption that a salon directory stays small, not a page.
        val listener = salonsCol
            .whereEqualTo("isAvailable", true)
            .limit(SALON_DIRECTORY)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    scope.launch {
                        val cached = salonCacheDao.observeAvailable().firstOrNull() ?: emptyList()
                        trySend(cached.map { it.toDocument() })
                    }
                    return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(SalonDocument::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.rating }
                    ?: emptyList()
                scope.launch { salonCacheDao.upsertAll(list.map { it.toEntity() }) }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeSalonByProvider(providerId: String): Flow<SalonDocument?> = callbackFlow {
        val listener = salonsCol
            .whereEqualTo("providerId", providerId)
            .limit(1)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                val doc = snap?.documents?.firstOrNull()
                trySend(doc?.toObject(SalonDocument::class.java)?.copy(id = doc.id))
            }
        awaitClose { listener.remove() }
    }

    fun observeAllSalons(): Flow<List<SalonDocument>> = callbackFlow {
        val listener = salonsCol.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(SalonDocument::class.java)?.copy(id = it.id) }
                ?.sortedBy { it.salonName }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    // ── Server-side salon discovery ───────────────────────────────────────────
    //
    // observeAllSalons above attaches a live listener to the entire salons
    // collection, downloads it, then sorts and filters on the device. At 500
    // salons that is 500 reads on every app open plus a fan-out to every
    // connected client on every salon edit — and it is structurally why there is
    // no ranking: nothing can rank a list the device already sorted by name.
    //
    // These read a bounded page with the filters applied by Firestore. They are
    // one-shot rather than listeners: a page that silently reshuffles under the
    // customer's thumb while they are reading it is not an improvement, and the
    // list refreshes on pull or on filter change.

    /** How the customer's list is ordered. Mirrors SalonSort in the view model. */
    enum class SalonOrder { RATING, PRICE, NAME }

    data class SalonFilter(
        // Which city's salons to show. Every query carries it, because a
        // customer in Herat looking at Kabul salons is not a filter she forgot
        // to apply — it is the wrong list.
        //
        // ORDERING DEPENDENCY: this only works once every salon has `city`,
        // which deriveSalonFields writes and adminNormalizeSalons backfills.
        // Ship this ahead of that backfill and an equality filter on a field
        // nothing has yet returns nothing at all, for every customer.
        val city: String = "",             // "" = every city
        val districtKey: String = "",      // "" = every ناحیه
        // The گذر/محله inside that district, when the customer picked one.
        //
        // A separate field rather than a second value in districtKey, because
        // they are different levels: a salon in District 17 whose neighbourhood
        // is Khair Khana must be found by someone filtering District 17 AND by
        // someone filtering Khair Khana, and one field cannot answer both. Only
        // ever one of the two is set — the finer choice implies its district.
        val areaKey: String = "",          // "" = anywhere in the district
        val category: String = "",         // "" = every category
        val favoriteIds: List<String> = emptyList(),  // non-empty = favourites only
        val search: String = "",           // prefix match on the salon name
    )

    data class SalonPage(
        val salons: List<SalonDocument>,
        val cursor: DocumentSnapshot?,     // pass back as `after` for the next page
        val endReached: Boolean,
    )

    private val SALON_PAGE_SIZE = 20L

    /**
     * Build the query for a filter. Kept in one place so the paged read and the
     * count can never disagree about what "matching" means — a count derived
     * from a different query than the list is a number that is confidently wrong.
     */
    private fun salonQuery(filter: SalonFilter, order: SalonOrder): Query {
        // Matches observeAvailableSalons, which this replaces. Dropping it would
        // surface salons that have closed themselves to bookings — a salon the
        // customer can find, tap and fail to book is worse than one she cannot
        // find. Whether an unavailable salon should be discoverable at all is a
        // product decision, not one to change silently while migrating.
        var q: Query = salonsCol.whereEqualTo("isAvailable", true)

        if (filter.city.isNotBlank()) {
            q = q.whereEqualTo("city", filter.city)
        }
        if (filter.districtKey.isNotBlank()) {
            q = q.whereEqualTo("districtKey", filter.districtKey)
        }
        if (filter.areaKey.isNotBlank()) {
            q = q.whereEqualTo("areaKey", filter.areaKey)
        }
        if (filter.category.isNotBlank()) {
            q = q.whereArrayContains("categories", filter.category)
        }
        if (filter.favoriteIds.isNotEmpty()) {
            // whereIn takes at most 30 values. A customer with more favourites
            // than that gets the first 30 here and the rest filtered in the view
            // model, rather than an exception.
            q = q.whereIn(FieldPath.documentId(), filter.favoriteIds.take(30))
        }

        // Normalised the way the server stores nameKey — see SearchKey. Sending
        // the raw text queries a range over spelling that was never written.
        val search = SearchKey.normalize(filter.search)
        if (search.isNotEmpty()) {
            // Firestore cannot match a substring. A range on the normalized name
            // gives prefix search, which covers typing the start of a name; \uf8ff
            // is the highest code point, so it bounds the range at "anything
            // starting with this". Mid-word search is not possible here without
            // an external search service, which the architecture deliberately
            // excludes.
            q = q.orderBy("nameKey")
                .startAt(search)
                .endAt(search + "\uf8ff")
            return q.limit(SALON_PAGE_SIZE)
        }

        q = when (order) {
            // sortRating and minPrice are written by deriveSalonFields on every
            // salon, never inherited — Firestore drops documents that lack the
            // orderBy field, so an unrated or unpriced salon would vanish from a
            // sorted list rather than appear at the end of it.
            SalonOrder.RATING -> q.orderBy("sortRating", Query.Direction.DESCENDING)
            SalonOrder.PRICE  -> q.orderBy("minPrice", Query.Direction.ASCENDING)
            SalonOrder.NAME   -> q.orderBy("nameKey", Query.Direction.ASCENDING)
        }
        return q.limit(SALON_PAGE_SIZE)
    }

    /**
     * One page of salons matching [filter], ordered by [order].
     *
     * Falls back to the encrypted local cache when the query fails, because the
     * listener this replaces did — losing that would mean a customer with no
     * signal sees an empty list rather than the salons she saw yesterday. The
     * fallback is a first page only: a cache cannot be paged through.
     */
    suspend fun salonPage(
        filter: SalonFilter,
        order: SalonOrder = SalonOrder.RATING,
        after: DocumentSnapshot? = null,
    ): SalonPage {
        var q = salonQuery(filter, order)
        if (after != null) q = q.startAfter(after)

        return runCatching {
            val snap = q.get().await()
            SalonPage(
                salons = snap.documents.mapNotNull {
                    it.toObject(SalonDocument::class.java)?.copy(id = it.id)
                },
                cursor = snap.documents.lastOrNull(),
                endReached = snap.documents.size < SALON_PAGE_SIZE,
            )
        }.getOrElse { err ->
            CrashReporter.recordNonFatal(err, "salonPage")
            if (after != null) return@getOrElse SalonPage(emptyList(), null, true)
            val cached = runCatching {
                (salonCacheDao.observeAvailable().firstOrNull() ?: emptyList()).map { it.toDocument() }
            }.getOrDefault(emptyList())
            SalonPage(salons = cached, cursor = null, endReached = true)
        }
    }

    /**
     * One salon by id, for a "book again" on a salon that is not in the current
     * page. The old lookup searched the fully-downloaded list, which paging
     * makes unreliable in exactly the case that matters: an older booking.
     */
    suspend fun getSalonById(salonId: String): SalonDocument? =
        runCatching {
            salonsCol.document(salonId).get().await()
                .toObject(SalonDocument::class.java)?.copy(id = salonId)
        }.getOrNull()

    /**
     * How many salons match [filter], for the "N providers found" line.
     *
     * An aggregation query, so it costs a fraction of reading the documents —
     * and it has to exist at all because that number used to be the size of the
     * fully-downloaded list, which pagination makes wrong.
     */
    suspend fun salonCount(filter: SalonFilter): Int? =
        runCatching {
            salonQuery(filter, SalonOrder.RATING)
                .count()
                .get(AggregateSource.SERVER)
                .await()
                .count
                .toInt()
        }.getOrElse {
            // Null, not zero. Defaulting a failed count to 0 makes "we could not
            // ask" indistinguishable from "there are none" — which is the exact
            // failure mode this whole phase exists to remove, and I reintroduced
            // it here. The caller shows no number rather than a wrong one.
            CrashReporter.recordNonFatal(it, "salonCount")
            null
        }

    /**
     * Record that a search or filter combination found nothing.
     *
     * No identity is attached — see the demand_signals rules. What matters is
     * that a district wanted a category and the platform had nothing to offer;
     * who asked is neither needed nor recorded.
     *
     * Best-effort: a customer's search must not fail because analytics did.
     */
    suspend fun recordNoResults(districtKey: String, category: String, lang: String) {
        runCatching {
            db.collection("demand_signals").add(
                mapOf(
                    "kind"        to "NO_RESULTS",
                    "districtKey" to districtKey,
                    "category"    to category,
                    "serviceName" to "",
                    "salonId"     to "",
                    "lang"        to lang,
                    "at"          to System.currentTimeMillis(),
                )
            ).await()
        }
    }

    suspend fun createSalon(salon: SalonDocument): String {
        val ref = salonsCol.add(salon).await()
        return ref.id
    }

    /**
     * Save the salon's own edits, without deleting what the server owns.
     *
     * This was a whole-document set() from the Kotlin POJO, and SalonDocument
     * does not declare `reliability`, `needsDiscoveryReview` or
     * `discoveryReview` — they are derived, and the rules freeze them precisely
     * so a salon cannot write its own reputation or clear its own review flag.
     * A set() deletes a field it does not mention, the deleted field reads back
     * as the default, the default never equals the stored value, and the write
     * is refused. Every Save, for every salon, with a generic failure dialog and
     * nothing in the logs saying why.
     *
     * merge() writes what the object carries and leaves the rest alone, which is
     * exactly the shape the rules are asking for. It is also the honest
     * description of the operation: a provider is editing her salon, not
     * replacing it.
     */
    suspend fun updateSalon(salon: SalonDocument) {
        salonsCol.document(salon.id).set(salon, SetOptions.merge()).await()
    }

    suspend fun setAvailability(salonId: String, isAvailable: Boolean) {
        salonsCol.document(salonId).update("isAvailable", isAvailable).await()
    }

    // NOTE: confirming an appointment (status flip + confirmedCount + loyalty
    // points + notification) is one atomic transaction in the confirmAppointment
    // Cloud Function — not client-side writes.

    suspend fun setSalonVerified(salonId: String, verified: Boolean) {
        salonsCol.document(salonId).update("isVerified", verified).await()
    }

    // ── Appointments ──────────────────────────────────────────────────────────

    fun observeForCustomer(customerId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("customerId", customerId)
            // Descending by date, so this is every upcoming booking followed by
            // recent history — the order the bookings list already showed, now
            // decided by the server. Unbounded, a long-standing customer
            // re-downloaded every appointment she had ever made each time one of
            // them changed.
            .orderBy("appointmentDate", Query.Direction.DESCENDING)
            .limit(RECENT_APPOINTMENTS)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observePendingForSalon(salonId: String): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("salonId", salonId)
            // PENDING was filtered after downloading every booking the salon had
            // ever taken — so the queue of things needing a decision cost the
            // salon's whole history to display. Ascending: the soonest
            // appointment is the one that needs answering first.
            .whereEqualTo("status", "PENDING")
            .orderBy("appointmentDate", Query.Direction.ASCENDING)
            .limit(SALON_QUEUE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * A salon's appointments inside one calendar month.
     *
     * Replaces the calendar's use of observeAllForSalon, which downloaded every
     * booking the salon had ever taken so the composable could keep the ones
     * falling in the month on screen. The month is a date range, which Firestore
     * can answer directly — the same salonId + appointmentDate index the booking
     * conflict check uses.
     *
     * [startMs] inclusive, [endMs] exclusive, both in the device's own timezone,
     * because that is the timezone the calendar grid is drawn in.
     */
    fun observeAppointmentsForMonth(salonId: String, startMs: Long, endMs: Long): Flow<List<AppointmentDocument>> = callbackFlow {
        val listener = appointmentsCol
            .whereEqualTo("salonId", salonId)
            .whereGreaterThanOrEqualTo("appointmentDate", startMs)
            .whereLessThan("appointmentDate", endMs)
            .orderBy("appointmentDate", Query.Direction.ASCENDING)
            .limit(SALON_QUEUE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents
                    ?.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    /**
     * A salon's booking tally — one document, so it costs the same at ten
     * bookings and at ten thousand.
     *
     * Replaces counting a downloaded copy of every appointment. The trigger that
     * maintains it sees the before and after of any write to an appointment, so
     * it stays right no matter which path changed a status.
     */
    fun observeSalonStats(salonId: String): Flow<SalonStatsDocument?> = callbackFlow {
        if (salonId.isBlank()) { trySend(null); awaitClose { }; return@callbackFlow }
        val listener = db.collection("salon_stats").document(salonId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    CrashReporter.recordNonFatal(err, "firestore:observeSalonStats")
                    trySend(null); return@addSnapshotListener
                }
                trySend(snap?.toObject(SalonStatsDocument::class.java))
            }
        awaitClose { listener.remove() }
    }


    // NOTE: appointments are created exclusively by the createPaymentSession
    // Cloud Function (pay-first); the rules deny client creation.

    // NOTE: rescheduling goes through the rescheduleAppointment Cloud Function —
    // appointment updates are rules-denied for clients.

    suspend fun sweepOldAppointments(windowMs: Long) {
        val cutoff = System.currentTimeMillis() - windowMs
        val cancelled = appointmentsCol
            .whereEqualTo("status", "CANCELLED")
            .get().await()
        cancelled.documents
            .filter { (it.getLong("createdAt") ?: 0L) < cutoff }
            .forEach { it.reference.delete().await() }
    }

    /**
     * One taken time-slot for a salon, tagged with the staff member it belongs
     * to ([staffId] is empty for a solo salon / "any available" booking).
     */
    /**
     * One taken slot, as the server sees it.
     *
     * [isParty] matters because a party holds the whole salon rather than one
     * chair. Without it the picker counts chairs and offers a time during
     * somebody's wedding that checkout then refuses.
     */
    // `id` is the appointment that occupies this slot, so a customer moving one
    // is not blocked by her own booking. hasSlotConflict already excludes it
    // server-side; the picker could not, and at a solo salon her own appointment
    // was most of what made her day look full.
    data class BookedSlot(
        val time: Long,
        val staffId: String,
        val isParty: Boolean = false,
        val id: String = "",
    )

    /**
     * Taken time-slots for a salon on the day containing [dateMs]. Served by
     * the getBookedSlots Cloud Function: a customer cannot (and must not) read
     * other customers' appointment docs directly — the rules deny that query,
     * so the old direct read silently returned empty and every slot looked
     * free, allowing double-booking. Each slot carries its staffId so a
     * multi-staff salon can serve several customers in the same time slot.
     */
    suspend fun getBookedSlotsForSalon(salonId: String, dateMs: Long): List<BookedSlot> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val result = functions
            .getHttpsCallable("getBookedSlots")
            .call(hashMapOf("salonId" to salonId, "dayStart" to startOfDay, "dayEnd" to endOfDay))
            .await()

        @Suppress("UNCHECKED_CAST")
        val map = result.getData() as? Map<String, Any?> ?: return emptyList()
        // Prefer the staff-aware "booked" shape; fall back to the legacy "slots"
        // (plain times, no staff) if the backend hasn't been updated yet.
        val booked = map["booked"] as? List<*>
        if (booked != null) {
            return booked.mapNotNull { entry ->
                val m = entry as? Map<*, *> ?: return@mapNotNull null
                val time = (m["time"] as? Number)?.toLong() ?: return@mapNotNull null
                BookedSlot(
                    time,
                    m["staffId"] as? String ?: "",
                    m["isParty"] == true,
                    m["id"] as? String ?: "",
                )
            }
        }
        return (map["slots"] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong()?.let { t -> BookedSlot(t, "") } }
            ?: emptyList()
    }

    // ── Chat ──────────────────────────────────────────────────────────────────

    // A conversation between one customer and one salon has no natural end, and
    // this used to load all of it on every open — unbounded, re-read in full on
    // every new message, and sorted on the device. A pair who have talked for a
    // year would pay for that year every time either of them opened the thread.
    private val CHAT_WINDOW = 50L

    /**
     * The most recent [CHAT_WINDOW] messages, oldest-first for display.
     *
     * Still a live listener, because a chat that does not update as the other
     * person types is not a chat — but now over a bounded window, so its cost
     * does not grow with the length of the relationship. Older messages are
     * fetched on demand by [olderMessages].
     */
    fun observeConversation(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = chatCol
            .whereEqualTo("conversationId", conversationId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(CHAT_WINDOW)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
                    ?.reversed()          // newest-first from Firestore, oldest-first on screen
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * The page of messages immediately before [beforeTimestamp], oldest-first.
     *
     * A one-shot read: history does not change, so there is nothing to listen to.
     */
    suspend fun olderMessages(conversationId: String, beforeTimestamp: Long): List<ChatMessage> =
        runCatching {
            chatCol
                .whereEqualTo("conversationId", conversationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .whereLessThan("timestamp", beforeTimestamp)
                .limit(CHAT_WINDOW)
                .get().await()
                .documents
                .mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
                .reversed()
        }.getOrElse {
            CrashReporter.recordNonFatal(it, "olderMessages")
            emptyList()
        }

    suspend fun sendChatMessage(message: ChatMessage) {
        chatCol.add(message).await()
    }

    // ── Reviews ─────────────────────────────────────────────────────────────────

    fun observeReviewsForSalon(salonId: String): Flow<List<ReviewDocument>> = callbackFlow {
        val listener = reviewsCol
            .whereEqualTo("salonId", salonId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(SALON_PORTFOLIO)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(ReviewDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun replyToReview(reviewId: String, reply: String) {
        reviewsCol.document(reviewId).update(
            mapOf("providerReply" to reply, "repliedAt" to System.currentTimeMillis())
        ).await()
    }

    /** Reserves a review document ID up front so photos can be uploaded under
     *  reviews/{id}/ before the review doc itself is written. */
    fun newReviewId(): String = reviewsCol.document().id

    /**
     * Submits a review through the server-side submitReview callable — the only
     * path that can create a review now. The callable binds the review to a real,
     * served appointment the caller owns (one review per booking) and writes the
     * doc with the Admin SDK; direct client creates are refused in firestore.rules
     * to stop review-farming (points → wallet credit) and rating forgery. Photos
     * are uploaded first (client-side, under reviews/{uid}/…) and their URLs
     * passed in. Throws if the backend rejects (not your booking / already
     * reviewed / not yet served).
     */
    suspend fun submitReview(
        appointmentId: String,
        salonId: String,
        rating: Int,
        comment: String,
        imageUrls: List<String>
    ) {
        functions.getHttpsCallable("submitReview").call(
            hashMapOf(
                "appointmentId" to appointmentId,
                "salonId"       to salonId,
                "rating"        to rating,
                "comment"       to comment,
                "imageUrls"     to imageUrls
            )
        ).await()
    }

    // ── Salon gallery (portfolio photos) ─────────────────────────────────────

    fun observeGalleryForSalon(salonId: String): Flow<List<GalleryImageDocument>> = callbackFlow {
        val listener = galleryCol
            .whereEqualTo("salonId", salonId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(SALON_PORTFOLIO)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(GalleryImageDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Reserves a new Firestore document ID so the Storage path can be pre-computed. */
    fun newGalleryDocId(): String = galleryCol.document().id

    // ── Social discovery feed (salon_posts) ─────────────────────────────────────

    /**
     * Live stories across every salon, newest first.
     *
     * Expiry is filtered client-side against expiresAt rather than queried,
     * because a range filter here would need a composite index and the set is
     * tiny by construction — nothing survives more than a day.
     */
    fun observeStories(): Flow<List<StoryDocument>> = callbackFlow {
        val listener = storiesCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val now = System.currentTimeMillis()
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(StoryDocument::class.java)?.copy(id = it.id) }
                    ?.filter { it.expiresAt > now }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * The global Discover feed: the newest salon posts across the whole platform.
     *
     * Ordered and capped SERVER-side. It previously attached a listener to the
     * whole collection and took the first 100 after they arrived, so the display
     * was capped but the download was not — every open of Discover pulled every
     * post ever published, and kept paying for each one on every change.
     */
    fun observeFeed(): Flow<List<SalonPostDocument>> = callbackFlow {
        val listener = postsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(FEED_PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(SalonPostDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Likes and comments on Discover posts ─────────────────────────────────
    //
    // The like id is "{postId}_{userId}", so liking twice is not a thing the
    // client has to guard against — the second write lands on the same document.

    private fun likeId(postId: String, userId: String) = "${postId}_$userId"

    /**
     * The ids of every post [userId] has liked.
     *
     * One query for the whole grid rather than a read per tile. The rules let a
     * user read only their own likes, so this is also the only shape of like
     * query a client can make — nobody can list who liked a salon's photo.
     */
    /**
     * Which of [postIds] this user has liked.
     *
     * Scoped to the posts on screen rather than to the user. It used to observe
     * every like the account had ever made, which is the one listener here a
     * limit could not fix: the result is a membership test, not a list, so
     * truncating it does not shorten anything — it reports a liked post as
     * unliked, draws an empty heart, and turns the next tap into a second like.
     *
     * The feed is a bounded page, so the answer only ever concerns that many
     * posts. whereIn takes 30 values, so the ids are chunked and the union of
     * the chunks is emitted; each chunk is its own listener and each is bounded
     * by construction.
     */
    fun observeMyLikes(userId: String, postIds: List<String>): Flow<Set<String>> = callbackFlow {
        val wanted = postIds.filter { it.isNotBlank() }.distinct()
        if (userId.isBlank() || wanted.isEmpty()) { trySend(emptySet()); awaitClose { }; return@callbackFlow }

        val chunks = wanted.chunked(30)
        val byChunk = arrayOfNulls<Set<String>>(chunks.size)
        val listeners = chunks.mapIndexed { i, chunk ->
            postLikesCol
                .whereEqualTo("userId", userId)
                .whereIn("postId", chunk)
                .addSnapshotListener { snap, err ->
                    byChunk[i] = if (err != null) emptySet()
                                 else snap?.documents?.mapNotNull { it.getString("postId") }?.toSet() ?: emptySet()
                    // Emit the union so far. A chunk that has not reported yet
                    // contributes nothing, which shows an unfilled heart for a
                    // moment rather than a wrong one.
                    trySend(byChunk.filterNotNull().flatten().toSet())
                }
        }
        awaitClose { listeners.forEach { it.remove() } }
    }

    suspend fun setPostLiked(postId: String, salonId: String, userId: String, liked: Boolean) {
        if (postId.isBlank() || userId.isBlank()) return
        val ref = postLikesCol.document(likeId(postId, userId))
        if (liked) {
            ref.set(mapOf(
                "postId"    to postId,
                "salonId"   to salonId,
                "userId"    to userId,
                "createdAt" to System.currentTimeMillis(),
            )).await()
        } else {
            ref.delete().await()
        }
    }

    /** A post's comments, oldest first — a thread reads top to bottom. */
    fun observeComments(postId: String): Flow<List<PostCommentDocument>> = callbackFlow {
        if (postId.isBlank()) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val listener = postCommentsCol
            .whereEqualTo("postId", postId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(COMMENT_PAGE_SIZE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap?.documents
                    ?.mapNotNull { it.toObject(PostCommentDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun addComment(comment: PostCommentDocument) {
        val ref = postCommentsCol.document()
        ref.set(comment.copy(id = ref.id)).await()
    }

    suspend fun deleteComment(commentId: String) {
        if (commentId.isBlank()) return
        postCommentsCol.document(commentId).delete().await()
    }

    suspend fun addGalleryImage(image: GalleryImageDocument) {
        val id = image.id.ifBlank { galleryCol.document().id }
        galleryCol.document(id).set(image.copy(id = id)).await()
    }

    /** Returns the storagePath field for [imageId], or blank if the doc doesn't exist. */
    suspend fun getGalleryImageStoragePath(imageId: String): String =
        runCatching {
            galleryCol.document(imageId).get().await()
                .getString("storagePath").orEmpty()
        }.getOrDefault("")

    suspend fun deleteGalleryImage(imageId: String) {
        galleryCol.document(imageId).delete().await()
    }

    // ── salon offers (provider-posted promotions; informational) ────────────────

    /** All offers a salon has posted (for the provider's own management list). */
    fun observeOffersForSalon(salonId: String): Flow<List<OfferDocument>> = callbackFlow {
        val listener = offersCol
            .whereEqualTo("salonId", salonId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(SALON_PORTFOLIO)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(OfferDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Every live offer across all salons (active + not expired), for customer display. */
    fun observeActiveOffers(): Flow<List<OfferDocument>> = callbackFlow {
        val listener = offersCol
            .whereEqualTo("active", true)
            // Expiry stays a client-side test: isLive treats expiresAt == 0 as
            // "never expires", and Firestore cannot express that OR alongside
            // the range it would need. What this bound risks is a salon losing
            // its offer badge, not a customer losing money, so recency is the
            // right thing to keep.
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ADMIN_HISTORY)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val now = System.currentTimeMillis()
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(OfferDocument::class.java)?.copy(id = it.id) }
                    ?.filter { it.isLive(now) }   // drop expired client-side (no index needed)
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Create or edit an offer (rules enforce the provider owns the salon). */
    suspend fun upsertOffer(offer: OfferDocument) {
        val id = offer.id.ifBlank { offersCol.document().id }
        offersCol.document(id).set(offer.copy(id = id)).await()
    }

    suspend fun setOfferActive(offerId: String, active: Boolean) {
        offersCol.document(offerId).update("active", active).await()
    }

    suspend fun deleteOffer(offerId: String) {
        offersCol.document(offerId).delete().await()
    }

    suspend fun deleteUser(uid: String) {
        usersCol.document(uid).delete().await()
    }

    suspend fun deleteSalonByProvider(providerId: String) {
        val docs = salonsCol.whereEqualTo("providerId", providerId).get().await()
        docs.documents.forEach { salonDoc ->
            // Remove any portfolio photos belonging to this salon, then the salon.
            runCatching {
                galleryCol.whereEqualTo("salonId", salonDoc.id).get().await()
                    .documents.forEach { it.reference.delete().await() }
            }
            salonDoc.reference.delete().await()
        }
    }

    // ── Admin — users ────────────────────────────────────────────────────────
    //
    // This replaces observeAllUsers, which held a snapshot listener on the whole
    // collection and sorted it in memory. At the 12 accounts the platform has
    // today that is invisible; at 100,000 it is 100,000 documents downloaded to
    // a phone on every open of the Users tab, and re-sent on every write anyone
    // makes. It was the only listener in the app that scaled with the customer
    // base rather than with one person's own data.
    //
    // Same shape as salonPage/salonCount, for the same reason: one query builder
    // so the list and the count can never disagree about what "matching" means.

    data class UserFilter(
        val role: String? = null,      // null = every role
        val search: String = "",
    )

    data class UserPage(
        val users: List<UserDocument>,
        val cursor: DocumentSnapshot?, // pass back as `after` for the next page
        val endReached: Boolean,
    )

    /**
     * The Stats tab's six numbers.
     *
     * Null means "could not ask", not zero — see salonCount. An admin looking at
     * a confidently wrong 0 has no way to tell it from an empty platform.
     */
    data class UserCounts(
        val total: Int? = null,
        val providers: Int? = null,
        val customers: Int? = null,
        val pending: Int? = null,
        val suspended: Int? = null,
    )

    private val USER_PAGE_SIZE = 25L

    private fun userQuery(filter: UserFilter): Query {
        var q: Query = usersCol
        filter.role?.let { q = q.whereEqualTo("role", it) }

        // Normalised the way the server stores nameKey — see SearchKey. Sending
        // the raw text queries a range over spelling that was never written.
        val search = SearchKey.normalize(filter.search)
        if (search.isNotEmpty()) {
            // An admin searching for a person types one of two things: a phone
            // number or a name. Both are answered by a field the server derives
            // (deriveUserPhoneKey), never by one the client can write.
            val key = PhoneUtils.loginKey(search)
            if (key.isNotEmpty()) {
                // Exact, not prefix: this is the same key login resolves by, so
                // "the number I was given" finds the account that number signs
                // into — including accounts whose stored phone was never
                // normalized, which is the whole reason the key exists.
                return q.whereEqualTo("phoneDigits", key).limit(USER_PAGE_SIZE)
            }
            // Firestore cannot match a substring. A range over the normalized
            // name gives prefix search; \uf8ff is the highest code point, so it
            // bounds the range at "anything starting with this". Mid-name search
            // would need an external search service, which the architecture
            // deliberately excludes.
            return q.orderBy("nameKey")
                .startAt(search)
                .endAt(search + "\uf8ff")
                .limit(USER_PAGE_SIZE)
        }

        // By document id, not createdAt. The comment here used to say createdAt
        // is "present on every account"; identity.js:855 says the opposite in
        // as many words — ordering by it "dropped every account written before
        // createdAt existed, which is exactly the population missing these
        // keys". Firestore excludes documents that lack the orderBy field, so
        // an admin browsing users could not see the accounts most likely to
        // need an admin. The id is on every document by definition, and it is
        // what idPage already pages by on the server.
        return q.orderBy(FieldPath.documentId()).limit(USER_PAGE_SIZE)
    }

    /** One page of users matching [filter]. */
    suspend fun usersPage(filter: UserFilter, after: DocumentSnapshot? = null): UserPage {
        var q = userQuery(filter)
        if (after != null) q = q.startAfter(after)
        return runCatching {
            val snap = q.get().await()
            UserPage(
                users = snap.documents.mapNotNull {
                    it.toObject(UserDocument::class.java)?.copy(uid = it.id)
                },
                cursor = snap.documents.lastOrNull(),
                endReached = snap.documents.size < USER_PAGE_SIZE,
            )
        }.getOrElse { err ->
            CrashReporter.recordNonFatal(err, "usersPage")
            UserPage(emptyList(), null, true)
        }
    }

    /** Counted on the server, so the six numbers do not require reading 100,000 documents. */
    suspend fun userCounts(): UserCounts {
        suspend fun countOf(q: Query): Int? = runCatching {
            q.count().get(AggregateSource.SERVER).await().count.toInt()
        }.getOrElse {
            CrashReporter.recordNonFatal(it, "userCounts")
            null
        }
        return UserCounts(
            total     = countOf(usersCol),
            providers = countOf(usersCol.whereEqualTo("role", "PROVIDER")),
            customers = countOf(usersCol.whereEqualTo("role", "CUSTOMER")),
            pending   = countOf(usersCol.whereEqualTo("status", "PENDING")),
            suspended = countOf(usersCol.whereEqualTo("status", "SUSPENDED")),
        )
    }

    /**
     * The users behind a set of ids — for attaching names to balances, payouts
     * and refunds.
     *
     * Those screens used to read the whole user collection and join in memory.
     * Looking up only the ids actually on screen keeps the cost proportional to
     * what is displayed instead of to how many people have registered. whereIn
     * takes at most 30 values, so the ids are chunked.
     */
    suspend fun usersByIds(ids: Collection<String>): Map<String, UserDocument> {
        val wanted = ids.filter { it.isNotBlank() }.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, UserDocument>()
        wanted.chunked(30).forEach { chunk ->
            runCatching {
                usersCol.whereIn(FieldPath.documentId(), chunk).get().await()
                    .documents.forEach { d ->
                        d.toObject(UserDocument::class.java)?.let { out[d.id] = it.copy(uid = d.id) }
                    }
            }.onFailure { CrashReporter.recordNonFatal(it, "usersByIds") }
        }
        return out
    }

    // ── Broadcasts ───────────────────────────────────────────────────────────

    /**
     * The announcements this reader should actually see.
     *
     * [role] and [lang] are the reader's own; an empty target matches everyone,
     * which is what an untargeted send means. A district-targeted one is skipped
     * unless [districtKey] matches — a customer has no district, and showing it
     * to her anyway is how a message meant for salons in one ناحیه reaches
     * everybody in Kabul.
     */
    fun visibleBroadcasts(
        all: List<BroadcastDocument>,
        role: String,
        lang: String,
        districtKey: String = "",
        now: Long = System.currentTimeMillis(),
    ): List<BroadcastDocument> = visibleBroadcastsFor(all, role, lang, districtKey, now)

    companion object BroadcastVisibility {
        /** An announcement with no expiry set stops showing after this. */
        private const val BROADCAST_MAX_AGE = 14L * 24 * 60 * 60 * 1000

        /**
         * Pure, and on the companion so a unit test can call it without a
         * Firestore instance — the rule is the part worth pinning, and a rule
         * that can only be exercised against a live database is one nobody
         * exercises.
         */
        @JvmStatic
        fun visibleBroadcastsFor(
            all: List<BroadcastDocument>,
            role: String,
            lang: String,
            districtKey: String,
            now: Long,
        ): List<BroadcastDocument> = all.filter { b ->
            val notExpired = if (b.expiresAt > 0L) b.expiresAt > now
                         // No expiry set, so fall back on age: the announcements
                         // sent before expiries existed would otherwise never
                         // retire, and one from August was still on screen.
                             else b.createdAt == 0L || now - b.createdAt <= BROADCAST_MAX_AGE
            val roleOk = b.targetRole.isBlank() || b.targetRole.equals(role, ignoreCase = true)
            val langOk = b.targetLang.isBlank() || b.targetLang.equals(lang, ignoreCase = true)
            val areaOk = b.targetDistrict.isBlank() || b.targetDistrict == districtKey
            notExpired && roleOk && langOk && areaOk
        }
    }

    fun observeBroadcasts(): Flow<List<BroadcastDocument>> = callbackFlow {
        val listener = broadcastsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ADMIN_HISTORY)
            .addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(BroadcastDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun sendBroadcast(doc: BroadcastDocument) {
        broadcastsCol.add(doc).await()
    }

    // ── Export ───────────────────────────────────────────────────────────────

    suspend fun getAppointmentsForUser(userId: String): List<AppointmentDocument> =
        appointmentsCol.whereEqualTo("customerId", userId).get().await()
            .documents.mapNotNull { it.toObject(AppointmentDocument::class.java)?.copy(id = it.id) }
            .sortedByDescending { it.appointmentDate }

    // ── Waitlist ──────────────────────────────────────────────────────────────────

    suspend fun addToWaitlist(entry: WaitlistEntry): String {
        val ref = waitlistCol.add(entry).await()
        return ref.id
    }

    suspend fun removeFromWaitlist(entryId: String) {
        waitlistCol.document(entryId).delete().await()
    }

    suspend fun dismissWaitlistEntry(entryId: String) {
        waitlistCol.document(entryId).update("status", "EXPIRED").await()
    }

    fun observeMyWaitlist(customerId: String): Flow<List<WaitlistEntry>> = callbackFlow {
        val listener = waitlistCol
            .whereEqualTo("customerId", customerId)
            // The status filter was applied after downloading everything, so a
            // customer who had joined many waitlists over the years read all of
            // them to display the two she was actually waiting on. Ascending,
            // because a waitlist is a queue and the oldest entry is the one
            // nearest the front.
            .whereIn("status", listOf("WAITING", "SLOT_AVAILABLE"))
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(ACTIVE_WAITLIST)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(WaitlistEntry::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * When a slot opens for [salonId] on [dateMs], promote the earliest-waiting
     * customer to SLOT_AVAILABLE. Uses a single equality filter + in-memory date
     * check to avoid composite index requirements.
     */
    suspend fun notifyFirstWaiting(salonId: String, dateMs: Long) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        val entries = waitlistCol.whereEqualTo("salonId", salonId).get().await()
        val first = entries.documents
            .mapNotNull { it.toObject(WaitlistEntry::class.java)?.copy(id = it.id) }
            .filter { it.requestedDate == startOfDay && it.status == "WAITING" }
            .minByOrNull { it.createdAt }
        if (first != null) {
            waitlistCol.document(first.id).update("status", "SLOT_AVAILABLE").await()
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    fun observeNotifications(uid: String): Flow<List<NotificationDocument>> = callbackFlow {
        val listener = notificationsCol
            .whereEqualTo("recipientId", uid)
            // Sorted and bounded by the server. A notification is never deleted,
            // so this listener grew for the life of the account and re-sent the
            // whole history on every new one. The newest RECENT_NOTIFICATIONS are
            // months of activity for any real customer; the notification centre
            // is a recent-activity view, not an archive.
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(RECENT_NOTIFICATIONS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    CrashReporter.recordNonFatal(err, "firestore:observeNotifications")
                    trySend(emptyList()); return@addSnapshotListener
                }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(NotificationDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // NOTE: notification documents are created exclusively by the Cloud
    // Functions (Admin SDK) — every doc becomes a real FCM push, so an open
    // client create path would let any signed-in user push arbitrary text to
    // any user's phone. The Firestore rules enforce this (create: if false).

    suspend fun markNotificationRead(notificationId: String) {
        runCatching { notificationsCol.document(notificationId).update("isRead", true).await() }
            .onFailure { CrashReporter.recordNonFatal(it, "firestore:markNotificationRead") }
    }

    suspend fun markAllNotificationsRead(uid: String) {
        runCatching {
            val batch = db.batch()
            val docs  = notificationsCol
                .whereEqualTo("recipientId", uid)
                .whereEqualTo("isRead", false)
                .get().await()
            docs.documents.forEach { batch.update(it.reference, "isRead", true) }
            if (docs.documents.isNotEmpty()) batch.commit().await()
        }.onFailure { CrashReporter.recordNonFatal(it, "firestore:markAllNotificationsRead") }
    }

    suspend fun deleteNotification(notificationId: String) {
        runCatching { notificationsCol.document(notificationId).delete().await() }
            .onFailure { CrashReporter.recordNonFatal(it, "firestore:deleteNotification") }
    }

    // ── Platform config (commission) ────────────────────────────────────────────

    /** Live commission percent from platform_config/general (defaults to 10%). */
    fun observeCommissionPercent(): Flow<Double> = callbackFlow {
        val listener = platformConfigCol.document("general")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(10.0); return@addSnapshotListener }
                trySend(snap?.getDouble("commissionPercent") ?: 10.0)
            }
        awaitClose { listener.remove() }
    }

    /** Admin-only write (enforced by security rules). */
    suspend fun setCommissionPercent(percent: Double) {
        platformConfigCol.document("general")
            .set(mapOf("commissionPercent" to percent), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /** Live list of promo codes (admin-only read, enforced by rules), newest first. */
    fun observePromoCodes(): Flow<List<PromoDocument>> = callbackFlow {
        val listener = promoCodesCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ADMIN_HISTORY)
            .addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(PromoDocument::class.java) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Admin-only: flagged (misconduct) customer reports still awaiting review. */
    fun observeFlaggedReports(): Flow<List<CustomerReportDocument>> = callbackFlow {
        val listener = db.collection("customer_reports")
            .whereEqualTo("flagged", true)
            .whereEqualTo("status", "OPEN")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ADMIN_QUEUE)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(CustomerReportDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    // ── Support tickets ─────────────────────────────────────────────────────────

    /**
     * Opens (or re-opens) this user's support ticket about a booking. One doc per
     * user keyed by [userId]; contacting support again just refreshes it and
     * flags it unread for the admin. The conversation itself is a normal chat
     * under "support_{userId}".
     */
    suspend fun upsertSupportTicket(
        userId: String,
        userName: String,
        userRole: String,
        relatedInfo: String
    ) {
        supportTicketsCol.document(userId).set(
            SupportTicket(
                id             = userId,
                userId         = userId,
                userName       = userName,
                userRole       = userRole,
                relatedInfo    = relatedInfo,
                status         = "OPEN",
                updatedAt      = System.currentTimeMillis(),
                unreadForAdmin = true
            )
        ).await()
    }

    /** Admin-only: live list of open support tickets, newest first. */
    fun observeOpenSupportTickets(): Flow<List<SupportTicket>> = callbackFlow {
        // Ordered by updatedAt, not createdAt: support_tickets has never had a
        // createdAt, and ordering by a field the documents do not carry returns
        // nothing at all — an admin would see an empty support queue with
        // tickets sitting in it.
        val listener = supportTicketsCol
            .whereEqualTo("status", "OPEN")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(ADMIN_QUEUE)
            .addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(SupportTicket::class.java)?.copy(id = it.id) }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Admin-only: clears the unread flag (the admin has opened the thread). */
    suspend fun markSupportTicketRead(userId: String) {
        runCatching { supportTicketsCol.document(userId).update("unreadForAdmin", false).await() }
    }

    /** Admin-only: closes a resolved ticket so it leaves the inbox. */
    suspend fun closeSupportTicket(userId: String) {
        runCatching { supportTicketsCol.document(userId).update("status", "CLOSED").await() }
    }

    // ── Provider balances (payout ledger) ────────────────────────────────────────

    /** Live list of what the platform owes each provider, highest first. */
    fun observeProviderBalances(): Flow<List<ProviderBalance>> = callbackFlow {
        // Deliberately no orderBy. Sorting by owedAmount and taking the top N
        // would drop the negative balances off the end — which is precisely the
        // bug the comment below records fixing.
        val listener = providerBalancesCol.limit(PROVIDER_LEDGER).addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(ProviderBalance::class.java) }
                // Keep anything non-zero: positive = platform owes the provider (a
                // payout), negative = the provider owes the platform (cash-booking
                // commission debt). Only 0 (fully settled) is dropped, so a provider
                // in debt no longer silently disappears from the admin finance list.
                ?.filter { it.owedAmount != 0L }
                ?.sortedByDescending { it.owedAmount }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Live owed balance for one provider — what they see on their own Income tab. */
    fun observeProviderBalance(providerId: String): Flow<Long> = callbackFlow {
        val listener = providerBalancesCol.document(providerId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(0L); return@addSnapshotListener }
                trySend(snap?.getLong("owedAmount") ?: 0L)
            }
        awaitClose { listener.remove() }
    }

    /** Live payout history for one provider (most recent first). */
    fun observePayoutsForProvider(providerId: String): Flow<List<PayoutDocument>> = callbackFlow {
        val listener = payoutsCol.whereEqualTo("providerId", providerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(PROVIDER_PAYOUTS)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(PayoutDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Live payout history (most recent first). Admin reads all rows. */
    fun observePayouts(): Flow<List<PayoutDocument>> = callbackFlow {
        val listener = payoutsCol
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ADMIN_HISTORY)
            .addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(PayoutDocument::class.java)?.copy(id = it.id) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    /** Live refund requests belonging to one customer (rules allow own rows). */
    fun observeRefundsForCustomer(customerId: String): Flow<List<RefundRequestDocument>> = callbackFlow {
        val listener = refundRequestsCol
            .whereEqualTo("customerId", customerId)
            // Feeds a badge on the bookings list, so it only needs to cover the
            // bookings that list can show — and it is bounded by the same number
            // for exactly that reason.
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(RECENT_APPOINTMENTS)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents
                    ?.mapNotNull { it.toObject(RefundRequestDocument::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /** Live refund requests (most recent first). Admin reads all rows. */
    /**
     * Refund requests still awaiting an admin decision, oldest first.
     *
     * Renamed from observeRefundRequests, which read every refund ever made so
     * the caller could filter for PENDING. Bounding that by recency would have
     * hidden an old pending refund behind newer settled ones — and a pending
     * refund an admin never sees is a customer's money that never comes back.
     * Filtering on the server keeps the queue complete at any history length.
     */
    fun observePendingRefundRequests(): Flow<List<RefundRequestDocument>> = callbackFlow {
        val listener = refundRequestsCol
            .whereEqualTo("status", "PENDING")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(ADMIN_QUEUE)
            .addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents
                ?.mapNotNull { it.toObject(RefundRequestDocument::class.java)?.copy(id = it.id) }
                ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    // ── Favorites (Firestore mirror) ────────────────────────────────────────────

    /**
     * Mirrors a favorite to Firestore so the server can notify favoriters when
     * their salon posts an offer. The on-device Room list stays the UI source of
     * truth; this is a best-effort write (doc id "{customerId}_{salonId}").
     */
    suspend fun setFavorite(customerId: String, salonId: String, isFavorite: Boolean) {
        if (customerId.isBlank() || salonId.isBlank()) return
        val ref = favoritesCol.document("${customerId}_$salonId")
        if (isFavorite) {
            ref.set(mapOf(
                "customerId" to customerId,
                "salonId"    to salonId,
                "createdAt"  to System.currentTimeMillis()
            )).await()
        } else {
            ref.delete().await()
        }
    }

    // ── Seeder check ──────────────────────────────────────────────────────────

    suspend fun isUsersEmpty(): Boolean =
        usersCol.limit(1).get().await().isEmpty

    suspend fun isAdminMissing(): Boolean =
        usersCol.whereEqualTo("role", "ADMIN").limit(1).get().await().isEmpty
}
