package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.AppointmentDocument
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProviderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val providerId: String = checkNotNull(savedStateHandle["userId"])

    val salon: StateFlow<SalonDocument?> =
        firestoreRepository.observeSalonByProvider(providerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Optimistic override: set immediately on toggle, cleared when Firestore confirms.
    private val _availableOverride = MutableStateFlow<Boolean?>(null)

    val isAvailable: StateFlow<Boolean> = combine(salon, _availableOverride) { s, override ->
        override ?: (s?.isAvailable ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pendingAppointments: StateFlow<List<AppointmentDocument>> = salon
        .flatMapLatest { s ->
            if (s != null) firestoreRepository.observePendingForSalon(s.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Profile-edit UI state ─────────────────────────────────────────────────

    var editDistrict     by mutableStateOf("")
    var editServices     by mutableStateOf<List<String>>(emptyList())
    var newServiceDraft  by mutableStateOf("")
    var showSaveSuccess  by mutableStateOf(false)
    var lockTriggered    by mutableStateOf(false)

    init {
        viewModelScope.launch {
            salon.collect { s ->
                if (s != null && editDistrict.isEmpty()) {
                    editDistrict = s.district
                    editServices = s.services
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

    fun saveProfile() {
        val current = salon.value ?: return
        viewModelScope.launch {
            firestoreRepository.updateSalon(
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
