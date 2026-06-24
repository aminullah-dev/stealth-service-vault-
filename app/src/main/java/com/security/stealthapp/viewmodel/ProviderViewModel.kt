package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.AppointmentDocument
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.GalleryImageDocument
import com.security.stealthapp.data.firebase.NotificationDocument
import com.security.stealthapp.data.firebase.ReviewDocument
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.StorageRepository
import com.security.stealthapp.data.firebase.WorkingHours
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.util.CrashReporter
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
                confirmed         = appointments.count { it.status == "CONFIRMED" },
                pending           = appointments.count { it.status == "PENDING" },
                cancelled         = appointments.count { it.status == "CANCELLED" },
                byService         = appointments.groupingBy { it.serviceName }.eachCount(),
                confirmedByService = appointments
                    .filter { it.status == "CONFIRMED" }
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

    // ── Profile-edit UI state ─────────────────────────────────────────────────

    var editDistrict     by mutableStateOf("")
    var editServices     by mutableStateOf<List<String>>(emptyList())
    var newServiceDraft  by mutableStateOf("")
    var showSaveSuccess  by mutableStateOf(false)
    var lockTriggered    by mutableStateOf(false)
    var editWorkingHours by mutableStateOf<List<WorkingHours>>(emptyList())
        private set
    var editSlotDuration by mutableStateOf(60)
        private set
    var editPrices       by mutableStateOf<Map<String, Int>>(emptyMap())

    init {
        viewModelScope.launch {
            salon.collect { s ->
                if (s != null && editDistrict.isEmpty()) {
                    editDistrict = s.district
                    editServices = s.services
                    editWorkingHours = s.workingHours.ifEmpty { defaultWorkingHours() }
                    editSlotDuration = s.slotDurationMinutes.takeIf { it > 0 } ?: 60
                    editPrices = s.pricePerService
                }
            }
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

    fun acceptAppointment(apptId: String) {
        viewModelScope.launch {
            val appt = allAppointments.value.find { it.id == apptId }
            firestoreRepository.updateAppointmentStatus(apptId, "CONFIRMED")
            salon.value?.id?.let { salonId ->
                runCatching { firestoreRepository.incrementConfirmedCount(salonId) }
            }
            if (appt != null) {
                runCatching { firestoreRepository.incrementLoyaltyPoints(appt.customerId) }
                runCatching {
                    firestoreRepository.createNotification(
                        NotificationDocument(
                            recipientId = appt.customerId,
                            type        = "BOOKING_CONFIRMED",
                            title       = "Booking Confirmed",
                            body        = "${appt.serviceName} at ${appt.salonName}",
                            createdAt   = System.currentTimeMillis(),
                            relatedId   = apptId
                        )
                    )
                }
            }
            vaultRepository.log("APPOINTMENT_CONFIRMED", "id=$apptId")
        }
    }

    fun declineAppointment(apptId: String) {
        viewModelScope.launch {
            val appt = allAppointments.value.find { it.id == apptId }
            firestoreRepository.updateAppointmentStatus(apptId, "CANCELLED")
            val salonId = salon.value?.id
            if (appt != null && salonId != null) {
                runCatching { firestoreRepository.notifyFirstWaiting(salonId, appt.appointmentDate) }
                runCatching {
                    firestoreRepository.createNotification(
                        NotificationDocument(
                            recipientId = appt.customerId,
                            type        = "BOOKING_CANCELLED",
                            title       = "Booking Declined",
                            body        = "${appt.serviceName} at ${appt.salonName}",
                            createdAt   = System.currentTimeMillis(),
                            relatedId   = apptId
                        )
                    )
                }
            }
            vaultRepository.log("APPOINTMENT_CANCELLED", "id=$apptId")
        }
    }

    fun onDistrictChanged(v: String)        { editDistrict = v }
    fun onNewServiceDraftChanged(v: String) { newServiceDraft = v }
    fun setPriceForService(service: String, price: Int) {
        editPrices = editPrices + (service to price)
    }

    fun addService() {
        val s = newServiceDraft.trim()
        if (s.isNotBlank() && !editServices.contains(s)) {
            editServices   = editServices + s
            newServiceDraft = ""
        }
    }

    fun removeService(s: String) { editServices = editServices.filter { it != s } }

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
            firestoreRepository.updateSalon(
                current.copy(
                    district            = editDistrict,
                    services            = editServices,
                    workingHours        = editWorkingHours,
                    slotDurationMinutes = editSlotDuration,
                    pricePerService     = editPrices
                )
            )
            vaultRepository.log("PROFILE_UPDATED", "district=$editDistrict")
            showSaveSuccess = true
        }
    }

    fun dismissSaveSuccess() { showSaveSuccess = false }

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

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Provider locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
