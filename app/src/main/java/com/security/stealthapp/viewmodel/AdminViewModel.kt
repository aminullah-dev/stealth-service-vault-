package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.PaymentRepository
import com.security.stealthapp.data.firebase.PayoutDocument
import com.security.stealthapp.data.firebase.ProviderBalance
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemStats(
    val totalUsers: Int = 0,
    val providers: Int = 0,
    val customers: Int = 0,
    val pendingApprovals: Int = 0,
    val totalSalons: Int = 0,
    val suspendedUsers: Int = 0
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val paymentRepository: PaymentRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val pendingProviders: StateFlow<List<UserDocument>> =
        firestoreRepository.observePendingProviders()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allUsers: StateFlow<List<UserDocument>> =
        firestoreRepository.observeAllUsers()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSalons: StateFlow<List<SalonDocument>> =
        firestoreRepository.observeAllSalons()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<SystemStats> = combine(allUsers, allSalons) { users, salons ->
        SystemStats(
            totalUsers       = users.size,
            providers        = users.count { it.role == "PROVIDER" },
            customers        = users.count { it.role == "CUSTOMER" },
            pendingApprovals = users.count { it.status == "PENDING" },
            totalSalons      = salons.size,
            suspendedUsers   = users.count { it.status == "SUSPENDED" }
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

    /** Provider balances joined with provider display names for the UI. */
    val providerBalances: StateFlow<List<ProviderBalance>> =
        combine(firestoreRepository.observeProviderBalances(), allUsers) { balances, users ->
            val nameById = users.associate { it.uid to it.name }
            balances.map { it.copy(providerName = nameById[it.providerId] ?: it.providerId) }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Payout history joined with provider names, most recent first. */
    val payouts: StateFlow<List<PayoutDocument>> =
        combine(firestoreRepository.observePayouts(), allUsers) { payouts, users ->
            val nameById = users.associate { it.uid to it.name }
            payouts.map { it.copy(providerName = nameById[it.providerId] ?: it.providerId) }
        }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Refund requests joined with customer + salon names, most recent first. */
    private val refundRequestsJoined: StateFlow<List<com.security.stealthapp.data.firebase.RefundRequestDocument>> =
        combine(firestoreRepository.observeRefundRequests(), allUsers, allSalons) { refunds, users, salons ->
            val nameById  = users.associate { it.uid to it.name }
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

    val pendingRefundRequests: StateFlow<List<com.security.stealthapp.data.firebase.RefundRequestDocument>> =
        refundRequestsJoined
            .map { list -> list.filter { it.status == "PENDING" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    var lockTriggered by mutableStateOf(false)
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

    fun rejectProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "REJECTED")
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

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Admin locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
