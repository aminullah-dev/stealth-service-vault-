package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.AppointmentDocument
import com.safebeauty.app.data.firebase.BroadcastDocument
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.GalleryImageDocument
import com.safebeauty.app.data.firebase.OfferDocument
import com.safebeauty.app.data.firebase.PaymentRepository
import com.safebeauty.app.data.firebase.ReviewDocument
import com.safebeauty.app.data.firebase.SalonDocument
import com.safebeauty.app.data.firebase.ServicePackage
import com.safebeauty.app.data.firebase.StaffMember
import com.safebeauty.app.data.firebase.StorageRepository
import com.safebeauty.app.data.firebase.WorkingHours
import com.safebeauty.app.data.repository.VaultRepository
import com.safebeauty.app.util.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderAnalytics(
    val total: Int = 0,
    val confirmed: Int = 0,
    val pending: Int = 0,
    val cancelled: Int = 0,
    val byService: Map<String, Int> = emptyMap(),
    val confirmedByService: Map<String, Int> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val storageRepository: StorageRepository,
    private val paymentRepository: PaymentRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val providerId: String = checkNotNull(savedStateHandle["userId"])

    val salon: StateFlow<SalonDocument?> =
        firestoreRepository.observeSalonByProvider(providerId)
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Optimistic override: set immediately on toggle, cleared when Firestore confirms.
    private val _availableOverride = MutableStateFlow<Boolean?>(null)

    val isAvailable: StateFlow<Boolean> = combine(salon, _availableOverride) { s, override ->
        override ?: (s?.isAvailable ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingAppointments: StateFlow<List<AppointmentDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observePendingForSalon(s.id)
            else flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAppointments: StateFlow<List<AppointmentDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeAllForSalon(s.id)
            else flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reviews: StateFlow<List<ReviewDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeReviewsForSalon(s.id)
            else flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analytics: StateFlow<ProviderAnalytics> = allAppointments
        .map { appointments ->
            ProviderAnalytics(
                total             = appointments.size,
                // COMPLETED is a finished CONFIRMED booking (a scheduled function
                // flips past ones over), so it still counts as an accepted booking.
                confirmed         = appointments.count { it.status == "CONFIRMED" || it.status == "COMPLETED" },
                pending           = appointments.count { it.status == "PENDING" },
                cancelled         = appointments.count { it.status == "CANCELLED" },
                byService         = appointments.groupingBy { it.serviceName }.eachCount(),
                confirmedByService = appointments
                    .filter { it.status == "CONFIRMED" || it.status == "COMPLETED" }
                    .groupingBy { it.serviceName }.eachCount()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderAnalytics())

    val estimatedRevenue: StateFlow<Int> = combine(analytics, salon) { a, s ->
        val prices = s?.pricePerService ?: emptyMap()
        a.confirmedByService.entries.sumOf { (service, count) ->
            (prices[service] ?: 0) * count
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ── Real earnings (actual paid/owed amounts, distinct from the price-based
    // estimate above) ────────────────────────────────────────────────────────

    /** What the platform currently owes this provider (net of commission). */
    val owedBalance: StateFlow<Long> =
        firestoreRepository.observeProviderBalance(providerId)
            .catch { emit(0L) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** This provider's own payout history, most recent first. */
    val myPayouts: StateFlow<List<com.safebeauty.app.data.firebase.PayoutDocument>> =
        firestoreRepository.observePayoutsForProvider(providerId)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Portfolio gallery ─────────────────────────────────────────────────────

    val gallery: StateFlow<List<GalleryImageDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeGalleryForSalon(s.id)
            else flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isUploadingPhoto by mutableStateOf(false)
    var photoError by mutableStateOf<String?>(null)

    /** Uploads [bytes] to Firebase Storage then writes the URL to Firestore. */
    fun addGalleryImage(bytes: ByteArray) {
        val salonId = salon.value?.id ?: return
        viewModelScope.launch {
            isUploadingPhoto = true
            runCatching {
                // Reserve the Firestore doc ID first so the Storage path matches.
                val docId = firestoreRepository.newGalleryDocId()
                val storagePath = "salon_gallery/$salonId/$docId.jpg"
                val url = storageRepository.uploadGalleryImage(salonId, docId, bytes)
                firestoreRepository.addGalleryImage(
                    GalleryImageDocument(
                        id          = docId,
                        salonId     = salonId,
                        imageUrl    = url,
                        storagePath = storagePath,
                        createdAt   = System.currentTimeMillis()
                    )
                )
            }.onFailure { CrashReporter.recordNonFatal(it, "provider:addGalleryImage") }
            isUploadingPhoto = false
        }
    }

    fun deleteGalleryImage(imageId: String) {
        // If this photo was the salon cover, drop it (persisted on the next save)
        // so the card falls back to the gradient instead of a broken image.
        if (gallery.value.find { it.id == imageId }?.imageUrl == editCoverImageUrl) {
            editCoverImageUrl = ""
        }
        viewModelScope.launch {
            // Look up the storagePath before deleting the Firestore doc.
            val storagePath = firestoreRepository.getGalleryImageStoragePath(imageId)
            runCatching { firestoreRepository.deleteGalleryImage(imageId) }
                .onFailure { CrashReporter.recordNonFatal(it, "provider:deleteGalleryImage:firestore") }
            if (storagePath.isNotBlank()) {
                storageRepository.deleteFile(storagePath)
            }
        }
    }

    // ── Salon offers (provider-posted promotions; informational) ──────────────

    val offers: StateFlow<List<OfferDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeOffersForSalon(s.id)
            else flowOf(emptyList())
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addOffer(title: String, description: String, discountPercent: Int) {
        val s = salon.value ?: return
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.upsertOffer(
                    OfferDocument(
                        salonId         = s.id,
                        providerId      = providerId,
                        salonName       = s.salonName,
                        title           = clean,
                        description     = description.trim(),
                        discountPercent = discountPercent.coerceIn(0, 100),
                        active          = true,
                        createdAt       = System.currentTimeMillis()
                    )
                )
            }.onFailure { CrashReporter.recordNonFatal(it, "provider:addOffer") }
        }
    }

    fun toggleOffer(offerId: String, active: Boolean) {
        viewModelScope.launch {
            runCatching { firestoreRepository.setOfferActive(offerId, active) }
                .onFailure { CrashReporter.recordNonFatal(it, "provider:toggleOffer") }
        }
    }

    fun deleteOffer(offerId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.deleteOffer(offerId) }
                .onFailure { CrashReporter.recordNonFatal(it, "provider:deleteOffer") }
        }
    }

    // ── Profile-edit UI state ─────────────────────────────────────────────────

    var editDistrict     by mutableStateOf("")
    var editServices     by mutableStateOf<List<String>>(emptyList())
    var newServiceDraft  by mutableStateOf("")
    var showSaveSuccess  by mutableStateOf(false)
    var showSaveError    by mutableStateOf(false)
    var signOutTriggered    by mutableStateOf(false)
    var editWorkingHours by mutableStateOf<List<WorkingHours>>(emptyList())
        private set
    var editSlotDuration by mutableStateOf(60)
        private set
    var editPrices       by mutableStateOf<Map<String, Int>>(emptyMap())
    var editDurations    by mutableStateOf<Map<String, Int>>(emptyMap())
    var editBlockedDates by mutableStateOf<List<String>>(emptyList())
    var editLastMinuteEnabled by mutableStateOf(false)
    var editLastMinutePercent by mutableStateOf(0)
    var editLastMinuteWindow  by mutableStateOf(0)
    var editPackages by mutableStateOf<List<ServicePackage>>(emptyList())
    var editHesabAccountNumber by mutableStateOf("")
    // Salon location (0/0 = not set yet).
    var editLatitude     by mutableStateOf(0.0)
        private set
    var editLongitude    by mutableStateOf(0.0)
        private set
    // Staff (stylists) roster editing.
    var editStaff        by mutableStateOf<List<StaffMember>>(emptyList())
    // The provider-chosen browse-card cover (one of the portfolio image URLs).
    // Persisted on the next saveProfile.
    var editCoverImageUrl by mutableStateOf("")
        private set
    var newStaffName     by mutableStateOf("")
    var newStaffSpecialty by mutableStateOf("")

    init {
        viewModelScope.launch {
            salon.collect { s ->
                if (s != null && editDistrict.isEmpty()) {
                    editDistrict = s.district
                    editServices = s.services
                    editWorkingHours = s.workingHours.ifEmpty { defaultWorkingHours() }
                    editSlotDuration = s.slotDurationMinutes.takeIf { it > 0 } ?: 60
                    editPrices = s.pricePerService
                    editDurations = s.durationPerService
                    editBlockedDates = s.blockedDates
                    editLastMinuteEnabled = s.lastMinuteEnabled
                    editLastMinutePercent = s.lastMinutePercent
                    editLastMinuteWindow  = s.lastMinuteWindowHours
                    editPackages = s.packages
                    editStaff = s.staff
                    editCoverImageUrl = s.coverImageUrl
                    editLatitude = s.latitude
                    editLongitude = s.longitude
                }
            }
        }
        viewModelScope.launch {
            runCatching { firestoreRepository.getUserById(providerId) }
                .getOrNull()
                ?.let { editHesabAccountNumber = it.hesabAccountNumber }
        }
    }

    fun toggleAvailability() {
        val current = salon.value ?: return
        val next = !isAvailable.value
        _availableOverride.value = next          // immediate UI feedback
        viewModelScope.launch {
            runCatching {
                firestoreRepository.setAvailability(current.id, next)
                vaultRepository.log("PROVIDER_TOGGLE", "isAvailable=$next")
            }.onFailure {
                _availableOverride.value = !next  // revert on error
            }.onSuccess {
                _availableOverride.value = null   // let Firestore value take over
            }
        }
    }

    /**
     * Confirms a PENDING appointment via the confirmAppointment Cloud Function,
     * which atomically flips the status, bumps the salon's confirmed count,
     * awards the customer's loyalty points, and notifies them — the old four
     * separate client writes could partially fail and drift out of sync.
     */
    fun acceptAppointment(apptId: String) {
        viewModelScope.launch {
            val ok = paymentRepository.confirmAppointment(apptId)
            if (ok) {
                vaultRepository.log("APPOINTMENT_CONFIRMED", "id=$apptId")
            } else {
                showSaveError = true
            }
        }
    }

    /**
     * Declines a PENDING appointment via the providerDeclineAppointment Cloud
     * Function. The booking is already paid (PENDING only exists after the
     * webhook confirms payment), so the function also flags it for a manual
     * refund and notifies the customer + releases the slot to the waitlist —
     * a direct Firestore status write could not do any of that safely.
     */
    fun declineAppointment(apptId: String) {
        viewModelScope.launch {
            val ok = paymentRepository.providerDeclineAppointment(apptId)
            if (ok) {
                vaultRepository.log("APPOINTMENT_CANCELLED", "id=$apptId")
            } else {
                showSaveError = true
            }
        }
    }

    fun onDistrictChanged(v: String)        { editDistrict = v }
    fun onHesabAccountNumberChanged(v: String) { editHesabAccountNumber = v }
    fun onNewServiceDraftChanged(v: String) { newServiceDraft = v }
    fun setPriceForService(service: String, price: Int) {
        editPrices = editPrices + (service to price)
    }
    fun setDurationForService(service: String, minutes: Int) {
        editDurations = if (minutes > 0) editDurations + (service to minutes)
                        else editDurations - service
    }
    /** Add or remove a day off. [dateKey] is a Kabul-local "yyyy-MM-dd" string. */
    fun toggleBlockedDate(dateKey: String) {
        editBlockedDates = if (editBlockedDates.contains(dateKey))
            editBlockedDates - dateKey
        else
            (editBlockedDates + dateKey).sorted()
    }

    fun addService() {
        val s = newServiceDraft.trim()
        if (s.isNotBlank() && !editServices.contains(s)) {
            editServices   = editServices + s
            newServiceDraft = ""
        }
    }

    fun removeService(s: String) { editServices = editServices.filter { it != s } }

    // ── Staff roster ──────────────────────────────────────────────────────────
    fun onNewStaffNameChanged(v: String)      { newStaffName = v }
    fun onNewStaffSpecialtyChanged(v: String) { newStaffSpecialty = v }

    fun addStaff() {
        val name = newStaffName.trim()
        if (name.isBlank()) return
        editStaff = editStaff + StaffMember(
            id        = java.util.UUID.randomUUID().toString(),
            name      = name,
            specialty = newStaffSpecialty.trim(),
            active    = true
        )
        newStaffName = ""
        newStaffSpecialty = ""
    }

    fun removeStaff(id: String) { editStaff = editStaff.filter { it.id != id } }

    // ── Packages (discounted service bundles) ───────────────────────────────────
    fun addPackage(name: String, services: List<String>, percent: Int) {
        if (services.isEmpty() || percent <= 0) return
        editPackages = editPackages + ServicePackage(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            services = services,
            discountPercent = percent.coerceIn(1, 100)
        )
    }
    fun removePackage(id: String) { editPackages = editPackages.filter { it.id != id } }

    /**
     * Uploads a portfolio photo for staff [staffId] and appends the URL to that
     * member in [editStaff] (persisted on the next saveProfile). Capped at 4. A
     * timestamp keeps the Storage path unique (the rule forbids overwrites).
     */
    fun addStaffPhoto(staffId: String, bytes: ByteArray) {
        val salonId = salon.value?.id ?: return
        val member = editStaff.find { it.id == staffId } ?: return
        if (member.photoUrls.size >= 4) return
        viewModelScope.launch {
            runCatching {
                val index = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                val url = storageRepository.uploadStaffPhoto(salonId, staffId, index, bytes)
                editStaff = editStaff.map {
                    if (it.id == staffId) it.copy(photoUrls = it.photoUrls + url) else it
                }
            }.onFailure { CrashReporter.recordNonFatal(it, "provider:addStaffPhoto") }
        }
    }

    fun removeStaffPhoto(staffId: String, url: String) {
        editStaff = editStaff.map {
            if (it.id == staffId) it.copy(photoUrls = it.photoUrls - url) else it
        }
    }

    /** Marks a portfolio photo (by its Storage URL) as the salon's browse-card
     *  cover. Persisted on the next saveProfile. */
    fun setCoverImage(url: String) { editCoverImageUrl = url }

    /** Records the salon's pinned location (from the provider's device GPS). */
    fun setLocation(lat: Double, lng: Double) {
        editLatitude = lat
        editLongitude = lng
    }

    fun toggleStaffActive(id: String) {
        editStaff = editStaff.map { if (it.id == id) it.copy(active = !it.active) else it }
    }

    fun toggleDayOpen(dayOfWeek: Int) {
        editWorkingHours = editWorkingHours.map {
            if (it.dayOfWeek == dayOfWeek) it.copy(isOpen = !it.isOpen) else it
        }
    }

    fun setDayOpenTime(dayOfWeek: Int, hour: Int, minute: Int) {
        editWorkingHours = editWorkingHours.map {
            if (it.dayOfWeek == dayOfWeek) it.copy(openHour = hour, openMinute = minute) else it
        }
    }

    fun setDayCloseTime(dayOfWeek: Int, hour: Int, minute: Int) {
        editWorkingHours = editWorkingHours.map {
            if (it.dayOfWeek == dayOfWeek) it.copy(closeHour = hour, closeMinute = minute) else it
        }
    }

    fun setSlotDuration(minutes: Int) { editSlotDuration = minutes }

    private fun defaultWorkingHours(): List<WorkingHours> = listOf(
        WorkingHours(dayOfWeek = 7, isOpen = true,  openHour = 9, closeHour = 18),  // Saturday
        WorkingHours(dayOfWeek = 1, isOpen = true,  openHour = 9, closeHour = 18),  // Sunday
        WorkingHours(dayOfWeek = 2, isOpen = true,  openHour = 9, closeHour = 18),  // Monday
        WorkingHours(dayOfWeek = 3, isOpen = true,  openHour = 9, closeHour = 18),  // Tuesday
        WorkingHours(dayOfWeek = 4, isOpen = true,  openHour = 9, closeHour = 18),  // Wednesday
        WorkingHours(dayOfWeek = 5, isOpen = true,  openHour = 9, closeHour = 18),  // Thursday
        WorkingHours(dayOfWeek = 6, isOpen = false, openHour = 9, closeHour = 13),  // Friday (off)
    )

    fun saveProfile() {
        val current = salon.value ?: return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.updateSalon(
                    current.copy(
                        district            = editDistrict,
                        services            = editServices,
                        workingHours        = editWorkingHours,
                        slotDurationMinutes = editSlotDuration,
                        pricePerService     = editPrices,
                        durationPerService  = editDurations,
                        blockedDates        = editBlockedDates,
                        lastMinuteEnabled     = editLastMinuteEnabled,
                        lastMinutePercent     = editLastMinutePercent.coerceIn(0, 100),
                        lastMinuteWindowHours = editLastMinuteWindow.coerceIn(0, 168),
                        packages            = editPackages,
                        staff               = editStaff,
                        coverImageUrl       = editCoverImageUrl,
                        latitude            = editLatitude,
                        longitude           = editLongitude
                    )
                )
                firestoreRepository.updateHesabAccountNumber(providerId, editHesabAccountNumber)
            }.onSuccess {
                vaultRepository.log("PROFILE_UPDATED", "district=$editDistrict")
                showSaveSuccess = true
            }.onFailure {
                // Surface the failure instead of silently claiming success.
                showSaveError = true
            }
        }
    }

    fun dismissSaveSuccess() { showSaveSuccess = false }
    fun dismissSaveError()   { showSaveError = false }

    fun replyToReview(reviewId: String, reply: String) {
        val trimmed = reply.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.replyToReview(reviewId, trimmed)
                vaultRepository.log("REVIEW_REPLIED", "id=$reviewId")
            }
        }
    }

    /**
     * Opens (or refreshes) a support ticket for this provider about [appt]. The
     * conversation is the "support_{providerId}" chat the screen navigates to.
     */
    fun contactSupport(appt: AppointmentDocument) {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.upsertSupportTicket(
                    userId      = providerId,
                    userName    = salon.value?.salonName ?: salon.value?.providerName ?: "",
                    userRole    = "PROVIDER",
                    relatedInfo = "${appt.serviceName} · ${appt.customerName}"
                )
                vaultRepository.log("SUPPORT_CONTACT", "provider booking=${appt.id}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Provider signed out") }
        signOutTriggered = true
    }

    fun resetSignOut() { signOutTriggered = false }

    // ── Two-way ratings: provider rates / reports a customer ──────────────────

    /** The appointment currently open in the rate-customer dialog (null = closed). */
    var ratingTarget by mutableStateOf<AppointmentDocument?>(null)
        private set
    var isSubmittingReport by mutableStateOf(false)
        private set
    var showReportDone by mutableStateOf(false)

    fun openRatingDialog(appt: AppointmentDocument) { ratingTarget = appt }
    fun dismissRatingDialog() { ratingTarget = null }
    fun dismissReportDone() { showReportDone = false }

    /**
     * Submits provider feedback about the customer on [appointmentId] via the
     * reportCustomer Cloud Function (rating 1–5, optional no-show / misconduct
     * flag). The server enforces one report per appointment and freezes the
     * customer's reputation aggregates against client tampering.
     */
    fun submitCustomerReport(
        appointmentId: String,
        rating: Int,
        noShow: Boolean,
        flagged: Boolean,
        comment: String
    ) {
        viewModelScope.launch {
            isSubmittingReport = true
            val ok = paymentRepository.reportCustomer(appointmentId, rating, noShow, flagged, comment)
            isSubmittingReport = false
            if (ok) {
                vaultRepository.log("CUSTOMER_REPORTED", "appt=$appointmentId rating=$rating noShow=$noShow flagged=$flagged")
                ratingTarget = null
                showReportDone = true
            } else {
                showSaveError = true
            }
        }
    }
}
