package com.safebeauty.app.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.safebeauty.app.data.firebase.AppointmentDocument
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.GalleryImageDocument
import com.safebeauty.app.data.firebase.NotificationDocument
import com.safebeauty.app.data.firebase.OfferDocument
import com.safebeauty.app.data.firebase.PaymentRepository
import com.safebeauty.app.data.firebase.CheckoutSession
import com.safebeauty.app.data.firebase.CheckoutOutcome
import com.safebeauty.app.data.firebase.PromoPreview
import com.safebeauty.app.data.firebase.ReviewDocument
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.activeStaff
import com.safebeauty.app.data.firebase.hasLocation
import com.safebeauty.app.data.firebase.LoyaltyTier
import com.safebeauty.app.data.firebase.StorageRepository
import com.safebeauty.app.data.firebase.WaitlistEntry
import com.safebeauty.app.data.firebase.WorkingHours
import com.safebeauty.app.util.Analytics
import com.safebeauty.app.util.CrashReporter
import java.util.Calendar
import com.safebeauty.app.data.repository.FavoritesRepository
import com.safebeauty.app.data.repository.LanguageRepository
import com.safebeauty.app.data.repository.VaultRepository
import com.safebeauty.app.ui.theme.StringResources
import com.safebeauty.app.workers.ReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Internal English keys used for Firestore filtering — independent of display language.
private val CATEGORY_KEYS = listOf("All", "Hair", "Makeup", "Nails", "Skincare", "Eyebrows")
// Index 0 is the "show everything" sentinel; the rest are the canonical Kabul
// area keys shared with the provider's district picker (see KabulAreas), so a
// salon's stored `district` always lines up with a filter option.
private val NEIGHBORHOOD_KEYS = listOf("All Neighborhoods") + com.safebeauty.app.util.KabulAreas.keys

data class BookingStatusChange(
    val salonName: String,
    val newStatus: String,
    val serviceName: String = "",
    val appointmentDate: Long = 0L
)

/** UI state machine for the prepay-at-booking HesabPay checkout. */
sealed interface CheckoutUiState {
    data object Idle : CheckoutUiState
    data object Creating : CheckoutUiState                     // contacting the backend
    data class AwaitingPayment(val session: CheckoutSession) : CheckoutUiState // open URL + poll
    data class Paid(val salonName: String) : CheckoutUiState
    // Cash booking: already confirmed server-side, nothing to open or poll —
    // just tell the customer how much to bring to the salon.
    data class CashConfirmed(val salonName: String, val amount: Long) : CheckoutUiState
    data class Failed(val message: String) : CheckoutUiState
}

/** Gift-card purchase flow (buy AFN credit for another user by phone). */
sealed interface GiftUiState {
    data object Idle : GiftUiState
    data object Creating : GiftUiState                      // contacting the backend
    data class OpenCheckout(val url: String) : GiftUiState  // open the HesabPay page
    data object Sent : GiftUiState                          // recipient credited
    data class Failed(val message: String) : GiftUiState
}

/** Tip flow (send AFN to the provider for a completed visit). */
sealed interface TipUiState {
    data object Idle : TipUiState
    data object Creating : TipUiState
    data class OpenCheckout(val url: String) : TipUiState
    data object Sent : TipUiState                           // provider credited
    data class Failed(val message: String) : TipUiState
}

/** Wallet top-up flow (add AFN credit to your own wallet via HesabPay). */
sealed interface WalletUiState {
    data object Idle : WalletUiState
    data object Creating : WalletUiState
    data class OpenCheckout(val url: String) : WalletUiState
    data object Done : WalletUiState                        // own wallet credited
    data class Failed(val message: String) : WalletUiState
}

/** How the customer's salon list is ordered. */
enum class SalonSort { RECOMMENDED, NEAREST, TOP_RATED, PRICE_LOW }

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val storageRepository: StorageRepository,
    private val paymentRepository: PaymentRepository,
    private val vaultRepository: VaultRepository,
    private val languageRepository: LanguageRepository,
    private val favoritesRepository: FavoritesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val customerId: String = checkNotNull(savedStateHandle["userId"])

    val categoryCount     = CATEGORY_KEYS.size
    val neighborhoodCount = NEIGHBORHOOD_KEYS.size

    private val _selectedCategoryIndex      = MutableStateFlow(0)
    private val _selectedNeighborhoodIndex  = MutableStateFlow(0)
    private val _currentUserName            = MutableStateFlow("")
    private val _currentUserPhone           = MutableStateFlow("")
    private val _currentUserEmail           = MutableStateFlow("")
    private val _currentUserPhoto           = MutableStateFlow("")
    private val _isOffline                  = MutableStateFlow(false)
    private val _showFavoritesOnly          = MutableStateFlow(false)
    private val _searchQuery                = MutableStateFlow("")
    private val _bookingStatusChange        = MutableSharedFlow<BookingStatusChange>(extraBufferCapacity = 4)
    private val _waitlistSlotAvailable      = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var previousStatuses: Map<String, String>         = emptyMap()
    private var previousWaitlistStatuses: Map<String, String> = emptyMap()

    val selectedCategoryIndex: StateFlow<Int>          = _selectedCategoryIndex
    val selectedNeighborhoodIndex: StateFlow<Int>      = _selectedNeighborhoodIndex
    val currentUserName: StateFlow<String>             = _currentUserName
    val currentUserPhoto: StateFlow<String>            = _currentUserPhoto
    val isOffline: StateFlow<Boolean>                  = _isOffline
    val showFavoritesOnly: StateFlow<Boolean>          = _showFavoritesOnly
    val searchQuery: StateFlow<String>                 = _searchQuery
    val bookingStatusChange: SharedFlow<BookingStatusChange> = _bookingStatusChange
    val waitlistSlotAvailable: SharedFlow<String>      = _waitlistSlotAvailable

    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Live 24-hour salon announcements, for the ring row on the dashboard.
     *
     * Also streamed by FeedViewModel for Discover. A free chair this afternoon
     * expires on its own and is worth more to both sides than anything else on
     * the screen, so it belongs on the first screen rather than one tap deep.
     */
    val stories: StateFlow<List<com.safebeauty.app.data.firebase.StoryDocument>> =
        firestoreRepository.observeStories()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var reviewThanksShown by mutableStateOf(false)
        private set

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOffline.value = false }
        override fun onLost(network: Network)      { _isOffline.value = true }
    }

    private val _allAvailableSalons: StateFlow<List<SalonDocument>> =
        firestoreRepository.observeAvailableSalons()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Server-side, paginated salon discovery ────────────────────────────────
    //
    // The list used to be the whole salons collection, downloaded and then
    // filtered and sorted on the device. Firestore does that work now, a page at
    // a time. The pieces below are the state that a paged list needs and a
    // fully-downloaded one did not: a cursor, an end marker, a loading flag, and
    // a count that no longer comes from the list's own size.

    private val _pagedSalons  = MutableStateFlow<List<SalonDocument>>(emptyList())
    val pagedSalons: StateFlow<List<SalonDocument>> = _pagedSalons

    /** How many salons match the current filters, server-counted. */
    // Null means "not known", which is not the same as zero. A failed count
    // rendered as 0 reads as "no salons here" — the invisible-empty failure this
    // phase exists to remove.
    private val _matchingCount = MutableStateFlow<Int?>(null)
    val matchingCount: StateFlow<Int?> = _matchingCount

    private val _loadingSalons = MutableStateFlow(false)
    val loadingSalons: StateFlow<Boolean> = _loadingSalons

    private val _endOfSalons = MutableStateFlow(false)
    val endOfSalons: StateFlow<Boolean> = _endOfSalons

    private var salonCursor: com.google.firebase.firestore.DocumentSnapshot? = null
    private var salonLoadJob: kotlinx.coroutines.Job? = null

    /** The filter the server should apply, from the current UI selections. */
    private fun currentSalonFilter(): FirestoreRepository.SalonFilter {
        val catIdx  = _selectedCategoryIndex.value
        val hoodIdx = _selectedNeighborhoodIndex.value
        return FirestoreRepository.SalonFilter(
            districtKey = NEIGHBORHOOD_KEYS.getOrElse(hoodIdx) { "" }
                .takeIf { hoodIdx > 0 } ?: "",
            category    = CATEGORY_KEYS.getOrElse(catIdx) { "" }
                .takeIf { catIdx > 0 } ?: "",
            favoriteIds = if (_showFavoritesOnly.value) favoriteIds.value.toList() else emptyList(),
            search      = _searchQuery.value,
        )
    }

    private fun currentSalonOrder(): FirestoreRepository.SalonOrder = when (_sortMode.value) {
        SalonSort.PRICE_LOW -> FirestoreRepository.SalonOrder.PRICE
        // NEAREST cannot be a Firestore ordering — see displayedSalons. It reads
        // a rating-ordered page and reorders it by distance on the device, which
        // is honest for a page and would not be for a whole collection.
        else -> FirestoreRepository.SalonOrder.RATING
    }

    /** Reload from the first page. Called whenever a filter or the sort changes. */
    fun refreshSalons() {
        salonLoadJob?.cancel()
        salonLoadJob = viewModelScope.launch {
            _loadingSalons.value = true
            val filter = currentSalonFilter()
            val page = firestoreRepository.salonPage(filter, currentSalonOrder(), after = null)
            salonCursor        = page.cursor
            _pagedSalons.value = page.salons
            _endOfSalons.value = page.endReached
            _matchingCount.value = firestoreRepository.salonCount(filter)
            _loadingSalons.value = false

            // An empty result with a filter applied is the platform failing to
            // serve someone who told it exactly what she wanted. Recorded so
            // supply decisions stop being guesses — see demand_signals.
            //
            // Only when a filter is actually narrowing: an empty unfiltered list
            // means the platform has no salons at all, which is already known and
            // would otherwise write a signal on every cold start.
            val narrowed = filter.districtKey.isNotBlank() ||
                filter.category.isNotBlank() ||
                filter.search.isNotBlank()
            if (narrowed && page.salons.isEmpty()) {
                val signature = "${filter.districtKey}|${filter.category}|${filter.search}"
                // One signal per distinct combination per session. Without this,
                // typing a name that matches nothing writes a row per keystroke.
                if (reportedEmptySearches.add(signature)) {
                    firestoreRepository.recordNoResults(
                        districtKey = filter.districtKey,
                        category    = filter.category,
                        lang        = languageRepository.language.value.name.lowercase(),
                    )
                }
            }
        }
    }

    /** Filter combinations already reported empty, so each is recorded once. */
    private val reportedEmptySearches = mutableSetOf<String>()

    /** Append the next page. Ignored while one is already in flight or at the end. */
    fun loadMoreSalons() {
        if (_loadingSalons.value || _endOfSalons.value) return
        val cursor = salonCursor ?: return
        salonLoadJob = viewModelScope.launch {
            _loadingSalons.value = true
            val page = firestoreRepository.salonPage(currentSalonFilter(), currentSalonOrder(), after = cursor)
            salonCursor        = page.cursor ?: cursor
            // Guard against a duplicate landing twice if a refresh raced this.
            val seen = _pagedSalons.value.map { it.id }.toSet()
            _pagedSalons.value = _pagedSalons.value + page.salons.filterNot { it.id in seen }
            _endOfSalons.value = page.endReached
            _loadingSalons.value = false
        }
    }

    val filteredSalons: StateFlow<List<SalonDocument>> = combine(
        combine(
            _allAvailableSalons,
            _selectedCategoryIndex,
            _selectedNeighborhoodIndex,
            favoriteIds,
            _showFavoritesOnly
        ) { salons, catIdx, hoodIdx, favorites, favOnly ->
            val category     = CATEGORY_KEYS.getOrElse(catIdx) { "All" }
            val neighborhood = NEIGHBORHOOD_KEYS.getOrElse(hoodIdx) { "All Neighborhoods" }
            salons.filter { salon ->
                // `categories` is the server-derived canonical list. The old
                // substring check is kept as a fallback rather than replaced:
                // it compared an English key against whatever a salon typed, so
                // a salon offering "ناخن" never matched "Nails" and every chip
                // returned nothing. Keeping it means this is strictly better
                // than before for a salon the backfill has not reached yet, and
                // never worse.
                val catMatch  = category == "All" ||
                    salon.categories.contains(category) ||
                    salon.services.any { it.contains(category, ignoreCase = true) }
                // Same shape: districtKey when it has been derived, the raw
                // stored value otherwise, so a legacy free-text district is no
                // less findable than it is today.
                val hoodMatch = neighborhood == "All Neighborhoods" ||
                    salon.districtKey == neighborhood ||
                    salon.district == neighborhood
                val favMatch  = !favOnly || favorites.contains(salon.id)
                catMatch && hoodMatch && favMatch
            }
        },
        _searchQuery
    ) { preFilt, query ->
        if (query.isBlank()) preFilt
        else preFilt.filter { it.salonName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Advanced filter + sort (rating / price / distance) ──────────────────────
    private val _customerLoc = MutableStateFlow<Pair<Double, Double>?>(null)
    val customerLoc: StateFlow<Pair<Double, Double>?> = _customerLoc
    private val _sortMode  = MutableStateFlow(SalonSort.RECOMMENDED)
    val sortMode: StateFlow<SalonSort> = _sortMode
    private val _minRating = MutableStateFlow(0.0)     // 0 = any
    val minRating: StateFlow<Double> = _minRating
    private val _maxPrice  = MutableStateFlow(0)       // 0 = any (AFN)
    val maxPrice: StateFlow<Int> = _maxPrice

    fun setCustomerLocation(lat: Double, lng: Double) { _customerLoc.value = lat to lng }
    fun setSortMode(mode: SalonSort) { _sortMode.value = mode }
    fun setMinRating(rating: Double) { _minRating.value = rating }
    fun setMaxPrice(price: Int) { _maxPrice.value = price }
    fun hasCustomerLocation(): Boolean = _customerLoc.value != null
    fun resetFilters() {
        _sortMode.value  = SalonSort.RECOMMENDED
        _minRating.value = 0.0
        _maxPrice.value  = 0
    }

    /**
     * Clears EVERY narrowing control, not just the ones in the filter sheet.
     * resetFilters() leaves category, neighbourhood, search text and the
     * favourites toggle untouched, so a customer who filtered themselves into an
     * empty list with "Makeup" + "District 5" would still see nothing after
     * resetting. This is what the empty state offers, so it has to undo
     * everything that could have emptied the list.
     */
    fun clearAllFilters() {
        resetFilters()
        _selectedCategoryIndex.value     = 0
        _selectedNeighborhoodIndex.value = 0
        _searchQuery.value               = ""
        _showFavoritesOnly.value         = false
    }

    /** The cheapest priced service at a salon (null if none priced). */
    private fun salonMinPrice(s: SalonDocument): Int? =
        s.pricePerService.values.filter { it > 0 }.minOrNull()

    /**
     * The salon list actually shown: [filteredSalons] narrowed by the minimum
     * rating and maximum price, then ordered by the chosen sort (nearest / top
     * rated / cheapest). Salons missing the sort key fall to the end.
     */
    val displayedSalons: StateFlow<List<SalonDocument>> =
        combine(_pagedSalons, _customerLoc, _sortMode, _minRating, _maxPrice) {
                salons, loc, sort, minR, maxP ->
            // District, category, favourites, search and the primary ordering are
            // applied by Firestore before this point. What remains here are the
            // two slider refinements and the distance ordering.
            //
            // Doing those on the device is not the thing Q-2 forbids: the set is
            // already a server-bounded page, so nothing extra is downloaded to
            // filter it. Rating and price could each be a server range only when
            // they match the sort field, and distance cannot be a Firestore
            // ordering at all without geohashing — which is deferred, and would
            // be meaningless today regardless, since one live salon has no
            // coordinates and the other carries the emulator's default location
            // in California.
            var list = salons
            if (minR > 0.0) list = list.filter { it.rating >= minR }
            if (maxP > 0)   list = list.filter { val mp = salonMinPrice(it); mp != null && mp <= maxP }
            when (sort) {
                SalonSort.NEAREST ->
                    if (loc == null) list
                    else list.sortedBy { s ->
                        if (s.hasLocation())
                            com.safebeauty.app.util.LocationHelper.distanceKm(loc.first, loc.second, s.latitude, s.longitude)
                        else Double.MAX_VALUE
                    }
                SalonSort.TOP_RATED -> list.sortedByDescending { it.rating }
                SalonSort.PRICE_LOW -> list.sortedBy { salonMinPrice(it) ?: Int.MAX_VALUE }
                SalonSort.RECOMMENDED -> list
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myAppointments: StateFlow<List<AppointmentDocument>> =
        firestoreRepository.observeForCustomer(customerId)
            // Hide unpaid bookings: an AWAITING_PAYMENT row exists only between
            // creating the checkout and the payment webhook confirming it.
            .map { list -> list.filter { it.status != "AWAITING_PAYMENT" } }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myWaitlist: StateFlow<List<WaitlistEntry>> =
        firestoreRepository.observeMyWaitlist(customerId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Refund status per cancelled+paid booking, keyed by appointmentId. Lets the
    // bookings list show "Refund pending" / "Refunded" on a cancelled online
    // booking (the refund is processed manually by an admin — no HesabPay API).
    val refundStatusByAppointment: StateFlow<Map<String, String>> =
        firestoreRepository.observeRefundsForCustomer(customerId)
            .map { refunds -> refunds.associate { it.appointmentId to it.status } }
            .catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Salons scored by how well they match the customer's booking history.
    // Requires ≥1 past appointment; shows up to 5 recommendations.
    val recommendedSalons: StateFlow<List<SalonDocument>> = combine(
        _allAvailableSalons, myAppointments
    ) { salons, appointments ->
        if (appointments.isEmpty()) return@combine emptyList()
        val serviceFreq   = appointments.groupingBy { it.serviceName }.eachCount()
        val districtFreq  = appointments
            .mapNotNull { appt -> salons.find { it.id == appt.salonId }?.district }
            .groupingBy { it }.eachCount()
        val topDistrict   = districtFreq.maxByOrNull { it.value }?.key
        salons
            .map { salon ->
                val serviceScore  = salon.services.sumOf { (serviceFreq[it] ?: 0) * 2 }
                val districtScore = if (salon.district == topDistrict) 1 else 0
                val ratingBonus   = if (salon.rating >= 4.5) 1 else 0
                salon to (serviceScore + districtScore + ratingBonus)
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(5)
            .map { (salon, _) -> salon }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loyaltyPoints: StateFlow<Int> =
        firestoreRepository.observeUserLoyaltyPoints(customerId)
            .catch { emit(0) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loyaltyTier: StateFlow<LoyaltyTier> = loyaltyPoints
        .map { pts ->
            when {
                pts >= 150 -> LoyaltyTier.VIP
                pts >= 50  -> LoyaltyTier.REGULAR
                else       -> LoyaltyTier.NEWCOMER
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoyaltyTier.NEWCOMER)

    // "redeemed" / "redeem_failed" (or null) — a one-shot result the redeem dialog
    // reacts to. loyaltyPoints/referralCredit refresh live on their own flows.
    var redeemResult by mutableStateOf<String?>(null)
        private set

    fun redeemLoyalty(points: Int) {
        viewModelScope.launch {
            paymentRepository.redeemLoyalty(points)
                .onSuccess { redeemResult = "redeemed" }
                .onFailure { redeemResult = "redeem_failed" }
        }
    }

    fun clearRedeemResult() { redeemResult = null }

    private val _activeSalonId = MutableStateFlow("")

    val reviewsForSalon: StateFlow<List<ReviewDocument>> = _activeSalonId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else firestoreRepository.observeReviewsForSalon(id)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val galleryForSalon: StateFlow<List<GalleryImageDocument>> = _activeSalonId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else firestoreRepository.observeGalleryForSalon(id)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Live (active + unexpired) offers for the salon whose detail sheet is open.
    val offersForSalon: StateFlow<List<OfferDocument>> = _activeSalonId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else firestoreRepository.observeOffersForSalon(id)
        }
        .map { offers -> offers.filter { it.isLive() } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // All live offers across every salon — powers the customer "Deals" strip and
    // the offer badge on salon cards. observeActiveOffers already drops expired
    // ones client-side; the map derives the set of salon ids that have an offer.
    val activeOffers: StateFlow<List<OfferDocument>> =
        firestoreRepository.observeActiveOffers()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offerSalonIds: StateFlow<Set<String>> = activeOffers
        .map { offers -> offers.map { it.salonId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setActiveSalon(id: String) { _activeSalonId.value = id }

    var bookingConfirmSalonName  by mutableStateOf<String?>(null)
        private set
    // Non-null only when the confirmed booking is a cash payment — drives the
    // "bring AFN X in cash" variant of the confirmation dialog.
    var bookingConfirmCashAmount by mutableStateOf<Long?>(null)
        private set
    var waitlistJoinedSalonName by mutableStateOf<String?>(null)
        private set

    // ── Payment / checkout flow (prepay at booking via HesabPay) ────────────────
    var checkout by mutableStateOf<CheckoutUiState>(CheckoutUiState.Idle)
        private set

    // ── Gift-card flow ──────────────────────────────────────────────────────────
    var giftState by mutableStateOf<GiftUiState>(GiftUiState.Idle)
        private set

    // One shared poller for the gift/tip/wallet checkouts. observePaymentStatus is
    // a never-completing callbackFlow, so without cancelling it a dismissed dialog
    // leaves a live Firestore listener that later flips the (already reset) dialog
    // state — e.g. the server EXPIRES the abandoned payment → the stale collector
    // shows "failed" over a fresh, successful attempt. Reset cancels it.
    private var moneyOutPollJob: kotlinx.coroutines.Job? = null

    /**
     * Buys a gift card for [phone]. On success the UI opens the returned HesabPay
     * URL; we then poll the payment until the webhook flips it to PAID (which
     * credits the recipient's wallet server-side) and report [GiftUiState.Sent].
     * The callable throws for an unknown recipient / bad amount → [Failed].
     */
    fun sendGiftCard(phone: String, amount: Long, message: String) {
        giftState = GiftUiState.Creating
        moneyOutPollJob?.cancel()
        moneyOutPollJob = viewModelScope.launch {
            paymentRepository.createGiftCard(phone, amount, message)
                .onSuccess { session ->
                    if (session.checkoutUrl.isBlank()) {
                        giftState = GiftUiState.Failed("no_url"); return@onSuccess
                    }
                    giftState = GiftUiState.OpenCheckout(session.checkoutUrl)
                    paymentRepository.observePaymentStatus(session.paymentId).collect { st ->
                        when (st) {
                            "PAID"   -> giftState = GiftUiState.Sent
                            "FAILED" -> giftState = GiftUiState.Failed("payment_failed")
                        }
                    }
                }
                .onFailure { e -> giftState = GiftUiState.Failed(e.message ?: "gift_failed") }
        }
    }

    fun resetGift() { moneyOutPollJob?.cancel(); giftState = GiftUiState.Idle }

    var tipState by mutableStateOf<TipUiState>(TipUiState.Idle)
        private set

    /**
     * Tips [amount] AFN on booking [appointmentId]. On success the UI opens the
     * HesabPay URL; we poll the payment until the webhook flips it to PAID (which
     * credits the provider server-side) and report [TipUiState.Sent].
     */
    fun sendTip(appointmentId: String, amount: Long) {
        tipState = TipUiState.Creating
        moneyOutPollJob?.cancel()
        moneyOutPollJob = viewModelScope.launch {
            paymentRepository.sendTip(appointmentId, amount)
                .onSuccess { session ->
                    if (session.checkoutUrl.isBlank()) {
                        tipState = TipUiState.Failed("no_url"); return@onSuccess
                    }
                    tipState = TipUiState.OpenCheckout(session.checkoutUrl)
                    paymentRepository.observePaymentStatus(session.paymentId).collect { st ->
                        when (st) {
                            "PAID"   -> tipState = TipUiState.Sent
                            "FAILED" -> tipState = TipUiState.Failed("payment_failed")
                        }
                    }
                }
                .onFailure { e -> tipState = TipUiState.Failed(e.message ?: "tip_failed") }
        }
    }

    fun resetTip() { moneyOutPollJob?.cancel(); tipState = TipUiState.Idle }

    // ── Wallet top-up flow ──────────────────────────────────────────────────────
    var walletState by mutableStateOf<WalletUiState>(WalletUiState.Idle)
        private set

    /**
     * Tops up the caller's own wallet by [amount] AFN. On success the UI opens the
     * returned HesabPay URL; we then poll the payment until the webhook flips it to
     * PAID (which credits referralCredit server-side) and report [WalletUiState.Done].
     */
    fun topUpWallet(amount: Long) {
        walletState = WalletUiState.Creating
        moneyOutPollJob?.cancel()
        moneyOutPollJob = viewModelScope.launch {
            paymentRepository.topUpWallet(amount)
                .onSuccess { session ->
                    if (session.checkoutUrl.isBlank()) {
                        walletState = WalletUiState.Failed("no_url"); return@onSuccess
                    }
                    walletState = WalletUiState.OpenCheckout(session.checkoutUrl)
                    paymentRepository.observePaymentStatus(session.paymentId).collect { st ->
                        when (st) {
                            "PAID"   -> walletState = WalletUiState.Done
                            "FAILED" -> walletState = WalletUiState.Failed("payment_failed")
                        }
                    }
                }
                .onFailure { e -> walletState = WalletUiState.Failed(e.message ?: "topup_failed") }
        }
    }

    fun resetWallet() { moneyOutPollJob?.cancel(); walletState = WalletUiState.Idle }

    private var paymentStatusJob: kotlinx.coroutines.Job? = null

    // ── Promo code (validated before booking) ───────────────────────────────────
    var promoInput   by mutableStateOf("")
    var promoChecking by mutableStateOf(false)
        private set
    var promoApplied by mutableStateOf<PromoPreview?>(null)
        private set
    var promoError   by mutableStateOf<String?>(null)
        private set

    /** Validates [promoInput] against the given salon service and, on success,
     *  stores the applied discount so the booking dialog can show it. */
    fun applyPromo(salonId: String, serviceNames: List<String>) {
        val code = promoInput.trim()
        if (code.isBlank()) return
        promoChecking = true
        promoError = null
        viewModelScope.launch {
            val preview = paymentRepository.previewPromo(code, salonId, serviceNames)
            promoChecking = false
            if (preview.valid) {
                promoApplied = preview
                promoError = null
            } else {
                promoApplied = null
                promoError = preview.errorMessage ?: "Invalid code"
            }
        }
    }

    fun clearPromo() {
        promoInput = ""
        promoApplied = null
        promoError = null
        promoChecking = false
    }

    var signOutTriggered by mutableStateOf(false)
        private set

    var cancelFailed by mutableStateOf(false)
        private set
    fun dismissCancelFailed() { cancelFailed = false }

    // Live identity-verification state, so the booking flow can require an
    // APPROVED customer before taking payment (see needsKycBeforeBooking).
    // Eagerly (not WhileSubscribed) because the gate reads .value directly at
    // the booking tap without collecting the flow — WhileSubscribed would leave
    // it stuck at the "NONE" seed and wrongly force even verified customers
    // through KYC. Started as null (unknown) so we can tell "not loaded yet"
    // apart from a genuine "NONE".
    val kycStatus: StateFlow<String?> =
        firestoreRepository.observeUser(customerId)
            // Map to the doc's kycStatus (a loaded UserDocument always has one —
            // it defaults to "NONE"). Crucially we do NOT coerce a null DOC into
            // "NONE": observeUser emits null on a snapshot error (e.g. a transient
            // permission-denied right after login, before the uid_map bridge
            // resolves), and treating that as a definitive "NONE" would wrongly
            // lock deals for — and bounce to KYC — an already-verified customer.
            // null therefore means "unknown", which the gates fail open on.
            .map { it?.kycStatus }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** This customer's own referral code (to share) — empty until loaded. */
    val referralCode: StateFlow<String> =
        firestoreRepository.observeUser(customerId)
            .map { it?.referralCode ?: "" }
            .catch { emit("") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** This customer's current referral credit balance (AFN). */
    val referralCredit: StateFlow<Long> =
        firestoreRepository.observeUser(customerId)
            .map { it?.referralCredit ?: 0L }
            .catch { emit(0L) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * True when the customer must verify their identity before they can book.
     * While the status is still loading (null) we do NOT block — the server's
     * createPaymentSession is not KYC-gated, so the worst case of letting an
     * unverified booking through here is caught at review, whereas wrongly
     * blocking a verified customer on a slow first load is a real annoyance.
     */
    fun needsKycBeforeBooking(): Boolean {
        val s = kycStatus.value ?: return false
        return s != "APPROVED"
    }

    var availableSlots by mutableStateOf<List<Long>>(emptyList())
        private set
    var slotsLoading by mutableStateOf(false)
        private set
    var noWorkingHours by mutableStateOf(false)
        private set

    // ── Customer profile editing ──────────────────────────────────────────────
    var editName by mutableStateOf("")
        private set
    var profileSaveSuccess by mutableStateOf(false)
        private set
    var isUploadingPhoto by mutableStateOf(false)
        private set

    init {
        // Reload the first page whenever anything the SERVER filters on changes.
        //
        // Search is debounced: without it every keystroke is a query, and the
        // results race — a slow response for "sha" can land after "shagh" and
        // overwrite it. The other inputs are discrete taps and need no delay,
        // which is why they are a separate collector rather than one debounced
        // stream that would also make tapping a chip feel sluggish.
        viewModelScope.launch {
            combine(
                _selectedCategoryIndex,
                _selectedNeighborhoodIndex,
                _showFavoritesOnly,
                favoriteIds,
                _sortMode,
            ) { _, _, _, _, _ -> Unit }
                .collect { refreshSalons() }
        }
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { refreshSalons() }
        }

        // Check current connectivity state
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        _isOffline.value = caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // One-time backfill: mirror any already-favorited salons (from before the
        // Firestore mirror existed) so their favoriters also receive offer pushes.
        // Firestore set() is idempotent, so re-running on later launches is cheap.
        viewModelScope.launch {
            val existing = favoritesRepository.favoriteIds.first()
            existing.forEach { salonId ->
                runCatching { firestoreRepository.setFavorite(customerId, salonId, true) }
            }
        }

        // Fetch current user's display name and profile photo
        viewModelScope.launch {
            runCatching { firestoreRepository.getUserById(customerId) }
                .getOrNull()
                ?.let {
                    _currentUserName.value = it.name
                    _currentUserPhone.value = it.phone
                    _currentUserEmail.value = it.email
                    // Prefer Storage URL; fall back to legacy Base64 for pre-migration photos.
                    _currentUserPhoto.value = it.profilePhotoUrl.ifBlank { it.profilePhotoBase64 }
                    editName = it.name

                    // One-time loyalty bonus once the profile is complete. The
                    // backend awards it at most once; we only bother calling when
                    // it isn't claimed yet and the profile actually looks complete.
                    val photo = it.profilePhotoUrl.ifBlank { it.profilePhotoBase64 }
                    if (!it.profileRewardClaimed &&
                        it.name.isNotBlank() && it.phone.isNotBlank() && photo.isNotBlank()) {
                        runCatching { paymentRepository.claimProfileReward() }
                    }
                }
        }

        // Detect appointment status changes and emit for the UI to show a notification
        viewModelScope.launch {
            myAppointments.collect { appointments ->
                val current = appointments.associateBy({ it.id }, { it.status })
                previousStatuses.forEach { (id, prevStatus) ->
                    val newStatus = current[id]
                    if (newStatus != null && newStatus != prevStatus) {
                        val appt = appointments.find { it.id == id }
                        if (appt != null) {
                            _bookingStatusChange.emit(
                                BookingStatusChange(
                                    salonName       = appt.salonName,
                                    newStatus       = newStatus,
                                    serviceName     = appt.serviceName,
                                    appointmentDate = appt.appointmentDate
                                )
                            )
                            if (newStatus == "CONFIRMED") scheduleReminders(appt)
                        }
                    }
                }
                previousStatuses = current
            }
        }

        // Detect waitlist slot-available transitions for push notifications
        viewModelScope.launch {
            myWaitlist.collect { entries ->
                val current = entries.associateBy({ it.id }, { it.status })
                previousWaitlistStatuses.forEach { (id, prevStatus) ->
                    val newStatus = current[id]
                    if (prevStatus == "WAITING" && newStatus == "SLOT_AVAILABLE") {
                        val entry = entries.find { it.id == id }
                        if (entry != null) _waitlistSlotAvailable.emit(entry.salonName)
                    }
                }
                previousWaitlistStatuses = current
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun selectCategory(index: Int)     { _selectedCategoryIndex.value = index }
    fun selectNeighborhood(index: Int) { _selectedNeighborhoodIndex.value = index }
    fun toggleFavoritesOnly()          { _showFavoritesOnly.value = !_showFavoritesOnly.value }
    fun setSearchQuery(q: String)      { _searchQuery.value = q }

    fun onEditNameChanged(v: String) { editName = v }

    fun saveCustomerProfile() {
        val trimmed = editName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.updateUserName(customerId, trimmed)
                _currentUserName.value = trimmed
                vaultRepository.log("CUSTOMER_PROFILE_UPDATED", "name=${trimmed.take(20)}")
                profileSaveSuccess = true
            }
        }
    }

    fun dismissProfileSaveSuccess() { profileSaveSuccess = false }

    fun uploadProfilePhoto(bytes: ByteArray) {
        viewModelScope.launch {
            isUploadingPhoto = true
            runCatching {
                val url = storageRepository.uploadUserPhoto(customerId, bytes)
                firestoreRepository.updateUserPhotoUrl(customerId, url)
                _currentUserPhoto.value = url
            }.onFailure { CrashReporter.recordNonFatal(it, "dashboard:uploadProfilePhoto") }
            isUploadingPhoto = false
        }
    }

    fun toggleFavorite(salonId: String) {
        val willBeFavorite = !favoriteIds.value.contains(salonId)
        viewModelScope.launch {
            // Room stays the offline source of truth for the UI; mirror to Firestore
            // so the server can push offers to a salon's favoriters.
            runCatching { favoritesRepository.toggle(salonId) }
            runCatching { firestoreRepository.setFavorite(customerId, salonId, willBeFavorite) }
        }
    }

    /**
     * Cancels a PENDING or CONFIRMED appointment via the cancelAppointment Cloud
     * Function. Every appointment in that state has already been paid, so the
     * function also flags the payment for a manual refund — a direct Firestore
     * status write can't do that safely, which is why this no longer touches
     * Firestore directly (see firestore.rules' appointments.update comment).
     */
    fun cancelAppointment(appointmentId: String) {
        viewModelScope.launch {
            val ok = paymentRepository.cancelAppointment(appointmentId)
            if (ok) {
                vaultRepository.log("APPOINTMENT_CANCELLED", "id=$appointmentId customerId=$customerId")
            } else {
                cancelFailed = true
            }
        }
    }

    fun joinWaitlist(salon: SalonDocument, dateMs: Long) {
        viewModelScope.launch {
            runCatching {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                firestoreRepository.addToWaitlist(
                    WaitlistEntry(
                        salonId       = salon.id,
                        salonName     = salon.salonName,
                        customerId    = customerId,
                        customerName  = _currentUserName.value,
                        requestedDate = cal.timeInMillis,
                        createdAt     = System.currentTimeMillis()
                    )
                )
                vaultRepository.log("WAITLIST_JOIN", "salonId=${salon.id}")
                waitlistJoinedSalonName = salon.salonName
            }
        }
    }

    fun leaveWaitlist(entryId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.removeFromWaitlist(entryId) }
        }
    }

    fun dismissWaitlistSlot(entryId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.dismissWaitlistEntry(entryId) }
        }
    }

    fun dismissWaitlistJoined() { waitlistJoinedSalonName = null }

    fun rescheduleAppointment(appointmentId: String, newDateMs: Long) {
        viewModelScope.launch {
            val ok = paymentRepository.rescheduleAppointment(appointmentId, newDateMs)
            if (ok) {
                vaultRepository.log("APPOINTMENT_RESCHEDULED", "id=$appointmentId date=$newDateMs")
            } else {
                // Reuses the generic action-failed dialog.
                cancelFailed = true
            }
        }
    }

    fun submitReview(
        appointmentId: String,
        salonId: String,
        rating: Int,
        comment: String,
        photos: List<ByteArray> = emptyList()
    ) {
        if (rating < 1) return
        viewModelScope.launch {
            runCatching {
                // Reserve an id purely for the photo storage path (reviews/{uid}/{id}/);
                // the review doc itself is created server-side by submitReview.
                val reviewId = firestoreRepository.newReviewId()
                val imageUrls = photos.take(3).mapIndexedNotNull { index, bytes ->
                    runCatching {
                        storageRepository.uploadReviewImage(customerId, reviewId, index, bytes)
                    }.getOrNull()
                }
                // Server-side create: binds the review to this served appointment
                // and blocks a second review of the same booking.
                firestoreRepository.submitReview(
                    appointmentId = appointmentId,
                    salonId       = salonId,
                    rating        = rating,
                    comment       = comment.trim(),
                    imageUrls     = imageUrls
                )
                vaultRepository.log(
                    "REVIEW_SUBMITTED",
                    "salonId=$salonId rating=$rating photos=${imageUrls.size}"
                )
                reviewThanksShown = true
            }
        }
    }

    fun dismissReviewThanks() { reviewThanksShown = false }

    private fun scheduleReminders(appt: AppointmentDocument) {
        val strings = StringResources.forLanguage(languageRepository.language.value)
        val title   = strings.reminderTitle
        val now     = System.currentTimeMillis()
        val wm      = WorkManager.getInstance(context)

        val delay24h = appt.appointmentDate - now - 24L * 60 * 60 * 1000
        if (delay24h > 0) {
            wm.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay24h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        ReminderWorker.KEY_TITLE to title,
                        ReminderWorker.KEY_BODY  to strings.remindedTomorrow(appt.salonName)
                    ))
                    .build()
            )
        }

        val delay1h = appt.appointmentDate - now - 60L * 60 * 1000
        if (delay1h > 0) {
            wm.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay1h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        ReminderWorker.KEY_TITLE to title,
                        ReminderWorker.KEY_BODY  to strings.remindedInHour(appt.salonName)
                    ))
                    .build()
            )
        }
    }

    /**
     * Starts the booking flow. For [paymentMethod] = "ONLINE" (the default) this
     * asks the backend to create the appointment (AWAITING_PAYMENT) plus a
     * HesabPay checkout session; the UI then opens [CheckoutSession.checkoutUrl]
     * and we poll the payment status until the webhook flips it to PAID (which
     * also releases the booking to the provider). For "CASH" the backend
     * confirms the booking immediately — there is nothing to open or poll.
     */
    // The last booking attempt, kept so a rejected booking can be retried
    // (pay cash / drop the promo / pick a new time) without the user re-entering
    // it. lastAttemptSalon is observable so the Failed dialog can rebuild the
    // date picker with the same salon + services.
    var lastAttemptSalon: SalonDocument? by mutableStateOf(null)
        private set
    private var lastAttemptServices: List<String> = emptyList()
    private var lastAttemptSlotMs: Long = 0L
    private var lastAttemptNotes: String = ""
    private var lastAttemptStaffId: String = ""
    private var lastAttemptPackageId: String = ""
    val lastAttemptServiceList: List<String> get() = lastAttemptServices
    val lastAttemptStaff: String   get() = lastAttemptStaffId
    val lastAttemptPackage: String get() = lastAttemptPackageId
    val lastAttemptSlot: Long      get() = lastAttemptSlotMs

    fun bookService(
        salon: SalonDocument,
        serviceNames: List<String>,
        appointmentDateMs: Long,
        notes: String = "",
        paymentMethod: String = "ONLINE",
        staffId: String = "",
        packageId: String = ""
    ) {
        // Funnel step 3: the moment of intent, logged before the network call so
        // it counts even when checkout then fails.
        Analytics.bookingStarted(
            salonId      = salon.id,
            serviceCount = serviceNames.size,
            cash         = paymentMethod == "CASH",
        )
        checkout = CheckoutUiState.Creating
        // Remember the attempt so a rejection can be retried (see the recovery
        // methods below) instead of forcing the user to start over.
        lastAttemptSalon     = salon
        lastAttemptServices  = serviceNames
        lastAttemptSlotMs    = appointmentDateMs
        lastAttemptNotes     = notes
        lastAttemptStaffId   = staffId
        lastAttemptPackageId = packageId
        // Only send a code that was actually validated for THIS service, so a
        // stale/mismatched code can't slip into the charge.
        val appliedCode = promoApplied?.code.orEmpty()
        viewModelScope.launch {
            val outcome = paymentRepository.createCheckout(
                salonId           = salon.id,
                serviceNames      = serviceNames,
                appointmentDateMs = appointmentDateMs,
                notes             = notes,
                email             = _currentUserEmail.value,
                method            = paymentMethod,
                promoCode         = appliedCode,
                staffId           = staffId,
                packageId         = packageId
            )
            if (outcome is CheckoutOutcome.Failure) {
                // Keep the attempt so the Failed dialog can offer a specific retry.
                checkout = CheckoutUiState.Failed(outcome.reason)
                return@launch
            }
            val session = (outcome as CheckoutOutcome.Success).session
            clearPromo()
            vaultRepository.log(
                "PAYMENT_STARTED",
                "salonId=${salon.id} service=${serviceNames.joinToString("، ")} amount=${session.amount} method=${session.method}"
            )
            if (session.method == "CASH") {
                checkout = CheckoutUiState.CashConfirmed(salon.salonName, session.amount)
                bookingConfirmSalonName  = salon.salonName
                bookingConfirmCashAmount = session.amount
            } else {
                checkout = CheckoutUiState.AwaitingPayment(session)
                observePayment(session.paymentId, salon.salonName)
            }
        }
    }

    /** Retry the last rejected booking as CASH — used when a discount made the
     *  online charge 0 (HesabPay can't charge 0). Keeps every other selection. */
    fun retryLastAsCash() {
        val salon = lastAttemptSalon ?: return
        bookService(salon, lastAttemptServices, lastAttemptSlotMs, lastAttemptNotes, "CASH", lastAttemptStaffId, lastAttemptPackageId)
    }

    /** Retry the last rejected booking after dropping the promo code — used when
     *  the code just hit its usage limit. Keeps every other selection. */
    fun retryLastWithoutPromo() {
        val salon = lastAttemptSalon ?: return
        clearPromo()
        bookService(salon, lastAttemptServices, lastAttemptSlotMs, lastAttemptNotes, "ONLINE", lastAttemptStaffId, lastAttemptPackageId)
    }

    private fun observePayment(paymentId: String, salonName: String) {
        paymentStatusJob?.cancel()
        paymentStatusJob = viewModelScope.launch {
            paymentRepository.observePaymentStatus(paymentId).collect { status ->
                when (status) {
                    "PAID" -> {
                        vaultRepository.log("PAYMENT_PAID", "paymentId=$paymentId")
                        checkout = CheckoutUiState.Paid(salonName)
                        bookingConfirmSalonName = salonName
                    }
                    "FAILED" -> checkout = CheckoutUiState.Failed("payment_failed")
                    else -> { /* PENDING — keep waiting */ }
                }
            }
        }
    }

    /** Called when the user backs out of the payment screen without paying. */
    fun cancelCheckout() {
        paymentStatusJob?.cancel()
        checkout = CheckoutUiState.Idle
    }

    fun dismissConfirmation() {
        bookingConfirmSalonName  = null
        bookingConfirmCashAmount = null
    }

    /**
     * Finds a salon by id for "book again".
     *
     * Checks what is loaded first, then asks Firestore. The old version searched
     * the fully-downloaded list, which paging makes unreliable in precisely the
     * case this exists for: an older booking, whose salon is unlikely to be on
     * the first page.
     */
    fun findSalon(salonId: String): SalonDocument? =
        _pagedSalons.value.firstOrNull { it.id == salonId }
            ?: _allAvailableSalons.value.firstOrNull { it.id == salonId }

    /** Fetches a salon that is not loaded, and adds it so the sheet can open. */
    fun ensureSalonLoaded(salonId: String, onReady: (SalonDocument?) -> Unit) {
        findSalon(salonId)?.let { onReady(it); return }
        viewModelScope.launch {
            val salon = firestoreRepository.getSalonById(salonId)
            if (salon != null && _pagedSalons.value.none { it.id == salon.id }) {
                _pagedSalons.value = _pagedSalons.value + salon
            }
            onReady(salon)
        }
    }

    /**
     * How many consecutive slots a booking of [serviceNames] occupies at [salon].
     * Kotlin mirror of the server-side serviceSlotSpan (functions/lib/slots.js):
     * each service takes its own minutes when set in durationPerService, else one
     * whole slot; the total is divided by the slot granularity and rounded up
     * (min 1). With no durations set this equals the service count — unchanged.
     */
    fun slotSpanFor(salon: SalonDocument, serviceNames: List<String>): Int {
        if (serviceNames.isEmpty()) return 1
        val step = salon.slotDurationMinutes.coerceAtLeast(1)
        val totalMinutes = serviceNames.sumOf { name ->
            val d = salon.durationPerService[name] ?: 0
            if (d > 0) d else step
        }
        return ((totalMinutes + step - 1) / step).coerceAtLeast(1)
    }

    fun loadSlotsForDate(salon: SalonDocument, dateMs: Long, selectedStaffId: String = "", slotSpan: Int = 1) {
        viewModelScope.launch {
            slotsLoading = true
            noWorkingHours = false
            val booked = runCatching {
                firestoreRepository.getBookedSlotsForSalon(salon.id, dateMs)
            }.getOrDefault(emptyList())
            val slots = computeSlots(salon, dateMs, booked, selectedStaffId, slotSpan)
            if (salon.workingHours.isEmpty()) noWorkingHours = true
            availableSlots = slots
            slotsLoading = false
        }
    }

    fun clearSlots() {
        availableSlots = emptyList()
        slotsLoading = false
        noWorkingHours = false
    }

    /**
     * Opens (or refreshes) a support ticket for this customer about [appt], so an
     * admin can pick it up. The conversation itself is the "support_{customerId}"
     * chat the screen then navigates to.
     */
    fun contactSupport(appt: AppointmentDocument) {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.upsertSupportTicket(
                    userId      = customerId,
                    userName    = _currentUserName.value,
                    userRole    = "CUSTOMER",
                    relatedInfo = "${appt.serviceName} · ${appt.salonName}"
                )
                vaultRepository.log("SUPPORT_CONTACT", "customer booking=${appt.id}")
            }
        }
    }

    private fun computeSlots(
        salon: SalonDocument,
        dateMs: Long,
        booked: List<FirestoreRepository.BookedSlot>,
        selectedStaffId: String,
        slotSpan: Int = 1
    ): List<Long> {
        // Days the provider blocked off (time-off/holiday) offer no slots.
        if (salon.blockedDates.contains(com.safebeauty.app.util.DateUtils.kabulDateKey(dateMs))) {
            return emptyList()
        }
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val wh = salon.workingHours.find { it.dayOfWeek == dayOfWeek } ?: return emptyList()
        if (!wh.isOpen) return emptyList()
        val slotDuration = salon.slotDurationMinutes.coerceAtLeast(30)
        // Capacity = number of bookable staff (a solo salon has capacity 1). A
        // slot is free when it has spare capacity — so a 3-stylist salon can take
        // three parallel bookings in the same time slot.
        val activeStaff = salon.activeStaff()
        val capacity = if (activeStaff.isEmpty()) 1 else activeStaff.size
        val bookedByTime: Map<Long, List<FirestoreRepository.BookedSlot>> = booked.groupBy { it.time }
        val slots = mutableListOf<Long>()
        val openCal = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, wh.openHour)
            set(Calendar.MINUTE, wh.openMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val closeMs = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, wh.closeHour)
            set(Calendar.MINUTE, wh.closeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        while (openCal.timeInMillis + slotDuration * 60_000L <= closeMs) {
            val slotMs = openCal.timeInMillis
            if (slotMs > now) {
                val atSlot = bookedByTime[slotMs].orEmpty()
                val free = when {
                    // A specific stylist was chosen: free unless that stylist is
                    // already booked at this time.
                    selectedStaffId.isNotEmpty() -> atSlot.none { it.staffId == selectedStaffId }
                    // Solo salon: any booking takes the single chair.
                    activeStaff.isEmpty()        -> atSlot.isEmpty()
                    // "Any available": free while a chair is still open.
                    else                         -> atSlot.size < capacity
                }
                if (free) slots.add(slotMs)
            }
            openCal.add(Calendar.MINUTE, slotDuration)
        }
        // A multi-service / group booking needs [slotSpan] back-to-back free slots,
        // so a start time only qualifies when every slot it would occupy is also
        // free (and still within opening hours). This mirrors the server-side
        // expansion in lib/slots.js so the customer can't start a long booking that
        // would run into an existing appointment or past closing time.
        if (slotSpan <= 1) return slots
        val freeSet = slots.toHashSet()
        val stepMs  = slotDuration * 60_000L
        return slots.filter { start ->
            (0 until slotSpan).all { i -> freeSet.contains(start + i * stepMs) }
        }
    }

    fun signOut() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Customer signed out") }
        signOutTriggered = true
    }

    fun resetSignOut() { signOutTriggered = false }
}
