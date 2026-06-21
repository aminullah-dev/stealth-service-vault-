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
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.WorkingHours
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderAnalytics(
    val total: Int = 0,
    val confirmed: Int = 0,
    val pending: Int = 0,
    val cancelled: Int = 0,
    val byService: Map<String, Int> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val providerId: String = checkNotNull(savedStateHandle["userId"])

    val salon: StateFlow<SalonDocument?> =
        firestoreRepository.observeSalonByProvider(providerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Optimistic override: set immediately on toggle, cleared when Firestore confirms.
    private val _availableOverride = MutableStateFlow<Boolean?>(null)

    val isAvailable: StateFlow<Boolean> = combine(salon, _availableOverride) { s, override ->
        override ?: (s?.isAvailable ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingAppointments: StateFlow<List<AppointmentDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observePendingForSalon(s.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allAppointments: StateFlow<List<AppointmentDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeAllForSalon(s.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analytics: StateFlow<ProviderAnalytics> = allAppointments
        .map { appointments ->
            ProviderAnalytics(
                total      = appointments.size,
                confirmed  = appointments.count { it.status == "CONFIRMED" },
                pending    = appointments.count { it.status == "PENDING" },
                cancelled  = appointments.count { it.status == "CANCELLED" },
                byService  = appointments.groupingBy { it.serviceName }.eachCount()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProviderAnalytics())

    // ── Portfolio gallery ─────────────────────────────────────────────────────

    val gallery: StateFlow<List<GalleryImageDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observeGalleryForSalon(s.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isUploadingPhoto by mutableStateOf(false)
        private set
    var photoError by mutableStateOf<String?>(null)
        private set

    /** Stores an already-compressed Base64 photo against the current salon. */
    fun addGalleryImage(base64: String) {
        val salonId = salon.value?.id ?: return
        viewModelScope.launch {
            isUploadingPhoto = true
            runCatching {
                firestoreRepository.addGalleryImage(
                    GalleryImageDocument(
                        salonId     = salonId,
                        imageBase64 = base64,
                        createdAt   = System.currentTimeMillis()
                    )
                )
            }
            isUploadingPhoto = false
        }
    }

    fun deleteGalleryImage(imageId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.deleteGalleryImage(imageId) }
        }
    }

    fun setUploading(value: Boolean) { isUploadingPhoto = value }
    fun setPhotoError(message: String?) { photoError = message }

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

    init {
        viewModelScope.launch {
            salon.collect { s ->
                if (s != null && editDistrict.isEmpty()) {
                    editDistrict = s.district
                    editServices = s.services
                    editWorkingHours = s.workingHours.ifEmpty { defaultWorkingHours() }
                    editSlotDuration = s.slotDurationMinutes.takeIf { it > 0 } ?: 60
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
            firestoreRepository.updateAppointmentStatus(apptId, "CONFIRMED")
            vaultRepository.log("APPOINTMENT_CONFIRMED", "id=$apptId")
        }
    }

    fun declineAppointment(apptId: String) {
        viewModelScope.launch {
            firestoreRepository.updateAppointmentStatus(apptId, "CANCELLED")
            vaultRepository.log("APPOINTMENT_CANCELLED", "id=$apptId")
        }
    }

    fun onDistrictChanged(v: String)      { editDistrict = v }
    fun onNewServiceDraftChanged(v: String) { newServiceDraft = v }

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
                    district = editDistrict,
                    services = editServices,
                    workingHours = editWorkingHours,
                    slotDurationMinutes = editSlotDuration
                )
            )
            vaultRepository.log("PROFILE_UPDATED", "district=$editDistrict")
            showSaveSuccess = true
        }
    }

    fun dismissSaveSuccess() { showSaveSuccess = false }

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Provider locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
