package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.db.entities.Salon
import com.security.stealthapp.data.repository.AppointmentDetail
import com.security.stealthapp.data.repository.AppointmentRepository
import com.security.stealthapp.data.repository.SalonRepository
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val salonRepository: SalonRepository,
    private val appointmentRepository: AppointmentRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val providerId: String = checkNotNull(savedStateHandle["userId"])

    // ── Live salon data from Room ─────────────────────────────────────────────

    val salon: StateFlow<Salon?> = salonRepository.observeSalonByProvider(providerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Derived from [salon] so it always reflects the DB truth.
     * When [toggleAvailability] writes to Room, this updates automatically.
     */
    val isAvailable: StateFlow<Boolean> = salon
        .map { it?.isAvailable ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Switches the appointment query whenever the salon ID changes.
     * [flatMapLatest] cancels the upstream collector when a new salon emits.
     */
    val pendingAppointments: StateFlow<List<AppointmentDetail>> = salon
        .flatMapLatest { s ->
            if (s != null) {
                appointmentRepository.observePendingDetailsForSalon(s.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Profile-edit UI state ─────────────────────────────────────────────────

    var editDistrict by mutableStateOf("")
        private set

    var editServices by mutableStateOf<List<String>>(emptyList())
        private set

    var newServiceDraft by mutableStateOf("")
        private set

    var showSaveSuccess by mutableStateOf(false)
        private set

    var lockTriggered by mutableStateOf(false)
        private set

    init {
        // Populate editable fields as soon as the salon loads.
        viewModelScope.launch {
            salon.collect { s ->
                if (s != null && editDistrict.isEmpty()) {
                    editDistrict  = s.district
                    editServices  = s.services
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Atomic single-column DB update; Room Flow re-emits [isAvailable] and
     * [SalonRepository.availableSalons] propagates the change to every
     * CustomerDashboard that is currently collecting it.
     */
    fun toggleAvailability() {
        val next = !isAvailable.value
        viewModelScope.launch {
            salonRepository.setAvailability(providerId, next)
            vaultRepository.log("PROVIDER_TOGGLE", "isAvailable=$next")
        }
    }

    fun acceptAppointment(appointmentId: String) {
        viewModelScope.launch {
            appointmentRepository.confirm(appointmentId)
            vaultRepository.log("APPOINTMENT_CONFIRMED", "id=$appointmentId")
        }
    }

    fun declineAppointment(appointmentId: String) {
        viewModelScope.launch {
            appointmentRepository.cancel(appointmentId)
            vaultRepository.log("APPOINTMENT_CANCELLED", "id=$appointmentId")
        }
    }

    fun onDistrictChanged(value: String)  { editDistrict = value }
    fun onNewServiceDraftChanged(v: String) { newServiceDraft = v }

    fun addService() {
        val trimmed = newServiceDraft.trim()
        if (trimmed.isNotBlank() && !editServices.contains(trimmed)) {
            editServices    = editServices + trimmed
            newServiceDraft = ""
        }
    }

    fun removeService(service: String) {
        editServices = editServices.filter { it != service }
    }

    fun saveProfile() {
        val current = salon.value ?: return
        viewModelScope.launch {
            salonRepository.updateSalon(
                current.copy(district = editDistrict, services = editServices)
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
