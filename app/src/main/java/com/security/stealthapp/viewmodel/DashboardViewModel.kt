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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["userId"])

    val categories    = listOf("All", "Hair", "Makeup", "Nails", "Skincare", "Eyebrows")
    val neighborhoods = listOf(
        "All Neighborhoods",
        "District 1 – Kabul Center",
        "District 3 – Khair Khana",
        "District 6 – Karte Seh",
        "District 9 – Dasht-e Barchi",
        "District 11 – Qala-e Wahed",
        "District 13 – Afshar"
    )

    private val _selectedCategory     = MutableStateFlow("All")
    private val _selectedNeighborhood = MutableStateFlow("All Neighborhoods")

    val selectedCategory: StateFlow<String>     = _selectedCategory
    val selectedNeighborhood: StateFlow<String> = _selectedNeighborhood

    /** Triple-combine: re-filters whenever provider toggles availability, or customer changes filters. */
    val filteredSalons: StateFlow<List<SalonDocument>> = combine(
        firestoreRepository.observeAvailableSalons(),
        _selectedCategory,
        _selectedNeighborhood
    ) { salons, category, neighborhood ->
        salons.filter { salon ->
            val catMatch  = category == "All" ||
                salon.services.any { it.contains(category, ignoreCase = true) }
            val hoodMatch = neighborhood == "All Neighborhoods" ||
                salon.district == neighborhood
            catMatch && hoodMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myAppointments: StateFlow<List<AppointmentDocument>> =
        firestoreRepository.observeForCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var bookingConfirmation by mutableStateOf<String?>(null)
        private set

    var lockTriggered by mutableStateOf(false)
        private set

    fun selectCategory(cat: String) { _selectedCategory.value = cat }
    fun selectNeighborhood(hood: String) { _selectedNeighborhood.value = hood }

    /**
     * Creates an appointment in Firestore with the selected date+time.
     * [appointmentDateMs] is the epoch millis chosen via the date/time picker.
     */
    fun bookService(salon: SalonDocument, serviceName: String, appointmentDateMs: Long) {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.createAppointment(
                    AppointmentDocument(
                        customerId      = customerId,
                        salonId         = salon.id,
                        salonName       = salon.salonName,
                        serviceName     = serviceName,
                        appointmentDate = appointmentDateMs,
                        createdAt       = System.currentTimeMillis()
                    )
                )
                vaultRepository.log(
                    "APPOINTMENT_CREATED",
                    "salonId=${salon.id} service=$serviceName"
                )
                bookingConfirmation =
                    "Request sent to ${salon.salonName}. She will contact you discreetly."
            }
        }
    }

    fun dismissConfirmation() { bookingConfirmation = null }

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Customer locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
