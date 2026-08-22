package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.CustomerReportDocument
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.PaymentRepository
import com.safebeauty.app.data.firebase.PayoutDocument
import com.safebeauty.app.data.firebase.PromoDocument
import com.safebeauty.app.data.firebase.ProviderBalance
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.UserDocument
import com.safebeauty.app.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Stats tab's six numbers.
 *
 * Nullable because they are now counted on the server, and a count can fail.
 * Null means "could not ask" and renders as a dash; zero means "none". Folding
 * the first into the second would show an admin a confident 0 registered users
 * when the truth is that the query did not run.
 */
data class SystemStats(
    val totalUsers: Int? = null,
    val providers: Int? = null,
    val customers: Int? = null,
    val pendingApprovals: Int? = null,
    val totalSalons: Int? = null,
    val suspendedUsers: Int? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val paymentRepository: PaymentRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    /** The signed-in admin's app uid (from the nav route) — used as sender id in support chats. */
    val adminId: String = savedStateHandle.get<String>("userId") ?: ""

    // Each "*Loaded" flag flips true on the underlying listener's FIRST emission
    // (success or error) so the UI can tell "still loading" apart from a
    // genuinely empty result — both used to render the exact same empty state.
    private val _approvalsLoaded = MutableStateFlow(false)
    val approvalsLoaded: StateFlow<Boolean> = _approvalsLoaded.asStateFlow()
    val pendingProviders: StateFlow<List<UserDocument>> =
        firestoreRepository.observePendingProviders()
            .onEach { _approvalsLoaded.value = true }
            .catch { _approvalsLoaded.value = true; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _kycLoaded = MutableStateFlow(false)
    val kycLoaded: StateFlow<Boolean> = _kycLoaded.asStateFlow()
    /** Users awaiting identity-verification review. */
    val kycPending: StateFlow<List<UserDocument>> =
        firestoreRepository.observeKycPending()
            .onEach { _kycLoaded.value = true }
            .catch { _kycLoaded.value = true; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var kycReviewInProgress by mutableStateOf<String?>(null)
        private set

    /** Admin approves a user's identity verification. */
    fun approveKyc(uid: String) {
        if (kycReviewInProgress != null) return
        kycReviewInProgress = uid
        viewModelScope.launch {
            paymentRepository.reviewKyc(uid, approve = true)
            kycReviewInProgress = null
            vaultRepository.log("ADMIN_KYC_APPROVE", "uid=$uid")
        }
    }

    /** Admin rejects a user's identity verification with a reason. */
    fun rejectKyc(uid: String, reason: String) {
        if (kycReviewInProgress != null) return
        kycReviewInProgress = uid
        viewModelScope.launch {
            paymentRepository.reviewKyc(uid, approve = false, rejectionReason = reason.trim())
            kycReviewInProgress = null
            vaultRepository.log("ADMIN_KYC_REJECT", "uid=$uid")
        }
    }

    // ── Users: paged, filtered and counted on the server ────────────────────
    //
    // This used to be one StateFlow over a listener on the entire users
    // collection, which the Users tab then searched and filtered in memory, and
    // which three other screens joined against for display names. That works
    // exactly until the platform has more users than a phone wants to download.
    //
    // Each of those four jobs now asks for what it needs: a page, a count, or
    // the specific people on screen.

    private val _usersLoaded = MutableStateFlow(false)
    val usersLoaded: StateFlow<Boolean> = _usersLoaded.asStateFlow()

    private val _users = MutableStateFlow<List<UserDocument>>(emptyList())
    val users: StateFlow<List<UserDocument>> = _users.asStateFlow()

    private val _userFilter = MutableStateFlow(FirestoreRepository.UserFilter())
    val userFilter: StateFlow<FirestoreRepository.UserFilter> = _userFilter.asStateFlow()

    private val _endOfUsers = MutableStateFlow(false)
    val endOfUsers: StateFlow<Boolean> = _endOfUsers.asStateFlow()

    private var userCursor: com.google.firebase.firestore.DocumentSnapshot? = null
    private var loadingUsers = false

    /**
     * Apply a role filter and/or a search term, and start again from the first
     * page. Both are answered by the server: the search resolves a phone number
     * through the same derived key login uses, or matches a name prefix.
     */
    fun setUserFilter(role: String? = _userFilter.value.role, search: String = _userFilter.value.search) {
        val next = FirestoreRepository.UserFilter(role = role, search = search)
        if (next == _userFilter.value && (_users.value.isNotEmpty() || loadingUsers)) return
        _userFilter.value = next
        refreshUsers()
    }

    fun refreshUsers() {
        viewModelScope.launch {
            loadingUsers = true
            val page = firestoreRepository.usersPage(_userFilter.value)
            userCursor        = page.cursor
            _users.value      = page.users
            _endOfUsers.value = page.endReached
            _usersLoaded.value = true
            loadingUsers = false
            refreshUserCounts()
        }
    }

    /**
     * Fetch the next page. Ignored while one is in flight or the end is reached,
     * so the list can call this freely as the admin scrolls without stampeding.
     */
    fun loadMoreUsers() {
        if (loadingUsers || _endOfUsers.value) return
        val cursor = userCursor ?: return
        viewModelScope.launch {
            loadingUsers = true
            val page = firestoreRepository.usersPage(_userFilter.value, cursor)
            userCursor        = page.cursor ?: cursor
            val seen = _users.value.map { it.uid }.toSet()
            _users.value      = _users.value + page.users.filterNot { it.uid in seen }
            _endOfUsers.value = page.endReached
            loadingUsers = false
        }
    }

    private val _userCounts = MutableStateFlow(FirestoreRepository.UserCounts())

    private fun refreshUserCounts() {
        viewModelScope.launch { _userCounts.value = firestoreRepository.userCounts() }
    }

    init {
        // The listener this replaces started as soon as the dashboard collected
        // it, so the Stats tab had its numbers whether or not the Users tab had
        // ever been opened. Without this, an admin who goes straight to Stats
        // waits on a spinner that never resolves.
        refreshUsers()
    }

    private val _salonsLoaded = MutableStateFlow(false)
    val allSalons: StateFlow<List<SalonDocument>> =
        firestoreRepository.observeAllSalons()
            .onEach { _salonsLoaded.value = true }
            .catch { _salonsLoaded.value = true; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True once both users and salons have settled — drives the Stats tab's loading state. */
    val statsLoaded: StateFlow<Boolean> =
        combine(_usersLoaded, _salonsLoaded) { u, s -> u && s }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Counted by the server rather than by counting a downloaded list. The salon
    // total still comes from observeAllSalons, which is bounded by the number of
    // salons on the platform rather than by the number of customers.
    val stats: StateFlow<SystemStats> = combine(_userCounts, allSalons) { c, salons ->
        SystemStats(
            totalUsers       = c.total,
            providers        = c.providers,
            customers        = c.customers,
            pendingApprovals = c.pending,
            totalSalons      = salons.size,
            suspendedUsers   = c.suspended
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStats())

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Finance: commission + provider payout ledger ────────────────────────────

    val commissionPercent: StateFlow<Double> =
        firestoreRepository.observeCommissionPercent()
            .catch { emit(10.0) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 10.0)

    /**
     * Provider balances joined with provider display names + payout destination.
     *
     * The names are fetched for the providers actually in the ledger, rather
     * than by holding every user on the platform in memory to look a few of
     * them up. Same for payouts and refunds below.
     */
    val providerBalances: StateFlow<List<ProviderBalance>> =
        firestoreRepository.observeProviderBalances().map { balances ->
            val userById = firestoreRepository.usersByIds(balances.map { it.providerId })
            balances.map {
                val u = userById[it.providerId]
                it.copy(
                    providerName       = u?.name ?: it.providerId,
                    hesabAccountNumber = u?.hesabAccountNumber ?: ""
                )
            }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Payout history joined with provider names, most recent first. */
    val payouts: StateFlow<List<PayoutDocument>> =
        firestoreRepository.observePayouts().map { payouts ->
            val nameById = firestoreRepository.usersByIds(payouts.map { it.providerId })
                .mapValues { (_, u) -> u.name }
            payouts.map { it.copy(providerName = nameById[it.providerId] ?: it.providerId) }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Refund requests joined with customer + salon names, most recent first. */
    private val refundRequestsJoined: StateFlow<List<com.safebeauty.app.data.firebase.RefundRequestDocument>> =
        combine(firestoreRepository.observePendingRefundRequests(), allSalons) { refunds, salons ->
            // combine's transform is itself a suspend function, so the lookup
            // happens here rather than in an extra map stage.
            val nameById  = firestoreRepository.usersByIds(refunds.map { it.customerId })
                .mapValues { (_, u) -> u.name }
            val salonById = salons.associate { it.id to it.salonName }
            refunds.map {
                it.copy(
                    customerName = nameById[it.customerId] ?: it.customerId,
                    salonName    = salonById[it.salonId] ?: it.salonId
                )
            }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The PENDING filter moved to the query, so this is now just the joined list.
    val pendingRefundRequests: StateFlow<List<com.safebeauty.app.data.firebase.RefundRequestDocument>> =
        refundRequestsJoined

    var commissionInput by mutableStateOf("")
    var commissionSaved by mutableStateOf(false)
        private set
    var commissionFailed by mutableStateOf(false)
        private set

    // providerId currently being paid out (drives the per-row spinner); null = idle.
    var payoutInProgress by mutableStateOf<String?>(null)
        private set
    var payoutResult by mutableStateOf<Long?>(null)   // amount just paid (for the toast/dialog)
        private set
    var payoutFailed by mutableStateOf(false)
        private set

    // refundRequestId currently being processed; null = idle.
    var refundInProgress by mutableStateOf<String?>(null)
        private set
    var refundResult by mutableStateOf(false)
        private set
    var refundFailed by mutableStateOf(false)
        private set

    var broadcastText by mutableStateOf("")
    var signOutTriggered by mutableStateOf(false)
        private set

    /** Parses [commissionInput] (0–100) and persists it; ignores invalid input. */
    fun saveCommission() {
        val percent = commissionInput.trim().toDoubleOrNull() ?: return
        if (percent < 0.0 || percent > 100.0) return
        viewModelScope.launch {
            runCatching { firestoreRepository.setCommissionPercent(percent) }
                .onSuccess {
                    commissionSaved = true
                    vaultRepository.log("ADMIN_SET_COMMISSION", "percent=$percent")
                }
                .onFailure { commissionFailed = true }
        }
    }

    fun dismissCommissionSaved() { commissionSaved = false }
    fun dismissCommissionFailed() { commissionFailed = false }

    /** Records that [providerId] has been paid; the webhook ledger resets to 0. */
    fun payoutProvider(providerId: String) {
        if (payoutInProgress != null) return
        payoutInProgress = providerId
        viewModelScope.launch {
            val amount = paymentRepository.recordProviderPayout(providerId)
            payoutInProgress = null
            if (amount != null) {
                payoutResult = amount
                vaultRepository.log("ADMIN_PAYOUT", "providerId=$providerId amount=$amount")
            } else {
                payoutFailed = true
            }
        }
    }

    fun dismissPayoutResult() { payoutResult = null }
    fun dismissPayoutFailed() { payoutFailed = false }

    /** Marks a refund request as processed (admin sent the money back manually). */
    fun processRefund(refundRequestId: String) {
        if (refundInProgress != null) return
        refundInProgress = refundRequestId
        viewModelScope.launch {
            val ok = paymentRepository.recordRefundProcessed(refundRequestId)
            refundInProgress = null
            if (ok) {
                refundResult = true
                vaultRepository.log("ADMIN_REFUND_PROCESSED", "refundRequestId=$refundRequestId")
            } else {
                refundFailed = true
            }
        }
    }

    fun dismissRefundResult() { refundResult = false }
    fun dismissRefundFailed() { refundFailed = false }

    // ── Promo codes ─────────────────────────────────────────────────────────────

    val promoCodes: StateFlow<List<PromoDocument>> =
        firestoreRepository.observePromoCodes()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Create-form state.
    var promoCodeInput      by mutableStateOf("")
    var promoPercentInput   by mutableStateOf("")   // percentage discount
    var promoAmountInput    by mutableStateOf("")   // OR fixed AFN discount
    var promoMaxUsesInput   by mutableStateOf("")   // 0/blank = unlimited
    var promoSaving         by mutableStateOf(false)
        private set
    var promoSaved          by mutableStateOf(false)
        private set
    var promoErrorMsg       by mutableStateOf<String?>(null)
        private set

    fun savePromo() {
        val code = promoCodeInput.trim().uppercase()
        val pct  = promoPercentInput.trim().toIntOrNull() ?: 0
        val amt  = promoAmountInput.trim().toLongOrNull() ?: 0L
        val max  = promoMaxUsesInput.trim().toIntOrNull() ?: 0
        if (code.length < 3) { promoErrorMsg = "Code must be at least 3 characters"; return }
        if (pct <= 0 && amt <= 0L) { promoErrorMsg = "Set a percentage or a fixed amount"; return }
        promoSaving = true
        promoErrorMsg = null
        viewModelScope.launch {
            val ok = paymentRepository.upsertPromoCode(
                code = code, discountPercent = pct, discountAmount = amt,
                maxUses = max, expiresAt = 0L, active = true
            )
            promoSaving = false
            if (ok) {
                promoSaved = true
                promoCodeInput = ""; promoPercentInput = ""; promoAmountInput = ""; promoMaxUsesInput = ""
                vaultRepository.log("ADMIN_PROMO_CREATE", "code=$code pct=$pct amt=$amt")
            } else {
                promoErrorMsg = "Could not save the code. It may be invalid."
            }
        }
    }

    fun togglePromo(code: String, active: Boolean) {
        viewModelScope.launch {
            paymentRepository.setPromoActive(code, active)
            vaultRepository.log("ADMIN_PROMO_TOGGLE", "code=$code active=$active")
        }
    }

    fun dismissPromoSaved() { promoSaved = false }
    fun dismissPromoError()  { promoErrorMsg = null }

    // ── Flagged customer reports (providers escalating misconduct) ───────────────

    /** Open misconduct reports providers have escalated to admin, newest first. */
    val flaggedReports: StateFlow<List<CustomerReportDocument>> =
        firestoreRepository.observeFlaggedReports()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // reportId currently being resolved (drives the per-row spinner); null = idle.
    var reportInProgress by mutableStateOf<String?>(null)
        private set

    /**
     * Resolves a flagged report: dismisses it, or (when [suspend] is true)
     * suspends the reported customer. Server-side flips the report to REVIEWED
     * so it leaves the open-reports queue.
     */
    fun resolveReport(reportId: String, suspend: Boolean) {
        if (reportInProgress != null) return
        reportInProgress = reportId
        viewModelScope.launch {
            paymentRepository.resolveCustomerReport(reportId, suspend)
            reportInProgress = null
            vaultRepository.log("ADMIN_REPORT_RESOLVE", "reportId=$reportId suspend=$suspend")
        }
    }

    // ── Support tickets (users contacting the admin about a booking) ─────────────

    /** Open support tickets, newest first. */
    val supportTickets: StateFlow<List<com.safebeauty.app.data.firebase.SupportTicket>> =
        firestoreRepository.observeOpenSupportTickets()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called when the admin opens a ticket's thread — clears its unread flag. */
    fun markSupportRead(userId: String) {
        viewModelScope.launch { firestoreRepository.markSupportTicketRead(userId) }
    }

    /** Closes a resolved support ticket so it leaves the inbox. */
    fun closeSupportTicket(userId: String) {
        viewModelScope.launch {
            firestoreRepository.closeSupportTicket(userId)
            vaultRepository.log("ADMIN_SUPPORT_CLOSE", "userId=$userId")
        }
    }

    fun suspendUser(uid: String) {
        viewModelScope.launch {
            firestoreRepository.suspendUser(uid)
            vaultRepository.log("ADMIN_SUSPEND", "uid=$uid")
        }
    }

    fun unsuspendUser(uid: String) {
        viewModelScope.launch {
            firestoreRepository.unsuspendUser(uid)
            vaultRepository.log("ADMIN_UNSUSPEND", "uid=$uid")
        }
    }

    fun approveProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "APPROVED")
            vaultRepository.log("ADMIN_APPROVE", "uid=$uid")
        }
    }

    fun rejectProvider(uid: String, reason: String) {
        viewModelScope.launch {
            firestoreRepository.rejectProvider(uid, reason.trim())
            vaultRepository.log("ADMIN_REJECT", "uid=$uid")
        }
    }

    fun deleteUser(uid: String, isProvider: Boolean) {
        viewModelScope.launch {
            firestoreRepository.deleteUser(uid)
            if (isProvider) firestoreRepository.deleteSalonByProvider(uid)
            vaultRepository.log("ADMIN_DELETE_USER", "uid=$uid")
        }
    }

    fun verifySalon(salonId: String, verified: Boolean) {
        viewModelScope.launch {
            runCatching { firestoreRepository.setSalonVerified(salonId, verified) }
            vaultRepository.log("ADMIN_VERIFY_SALON", "salonId=$salonId verified=$verified")
        }
    }

    fun sendBroadcast() {
        val msg = broadcastText.trim()
        if (msg.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.sendBroadcast(
                    BroadcastDocument(message = msg, createdAt = System.currentTimeMillis())
                )
                broadcastText = ""
                vaultRepository.log("ADMIN_BROADCAST", "msg=${msg.take(40)}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Admin signed out") }
        signOutTriggered = true
    }

    fun resetSignOut() { signOutTriggered = false }
}
