package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.db.entities.Appointment
import com.security.stealthapp.data.db.entities.Salon
import com.security.stealthapp.data.repository.AppointmentRepository
import com.security.stealthapp.data.repository.SalonRepository
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
    private val salonRepository: SalonRepository,
    private val appointmentRepository: AppointmentRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    // Injected from the navigation back-stack entry.
    private val customerId: String = checkNotNull(savedStateHandle["userId"])

    // ── Static filter options ─────────────────────────────────────────────────

    val categories = listOf("All", "Hair", "Makeup", "Nails", "Skincare", "Eyebrows")

    val neighborhoods = listOf(
        "All Neighborhoods",
        "District 1 – Kabul Center",
        "District 3 – Khair Khana",
        "District 6 – Karte Seh",
        "District 9 – Dasht-e Barchi",
        "District 11 – Qala-e Wahed",
        "District 13 – Afshar"
    )

    // ── Filter state — backed by MutableStateFlow so combine() can react ─────

    private val _selectedCategory    = MutableStateFlow("All")
    private val _selectedNeighborhood = MutableStateFlow("All Neighborhoods")

    val selectedCategory: StateFlow<String>     = _selectedCategory
    val selectedNeighborhood: StateFlow<String> = _selectedNeighborhood

    // ── Live data from Room/SQLCipher ─────────────────────────────────────────

    /**
     * Reactive triple-combine: emits a new filtered list whenever
     *  (a) a provider toggles their availability,
     *  (b) the customer changes the category chip, or
     *  (c) the customer changes the neighborhood dropdown.
     */
    val filteredSalons: StateFlow<List<Salon>> = combine(
        salonRepository.availableSalons,
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

    /** The current customer's own booking history. */
    val myAppointments: StateFlow<List<Appointment>> =
        appointmentRepository.observeForCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── UI state ──────────────────────────────────────────────────────────────

    var bookingConfirmation by mutableStateOf<String?>(null)
        private set

    var lockTriggered by mutableStateOf(false)
        private set

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectCategory(category: String) { _selectedCategory.value = category }

    fun selectNeighborhood(neighborhood: String) { _selectedNeighborhood.value = neighborhood }

    fun bookService(salon: Salon, serviceName: String) {
        viewModelScope.launch {
            appointmentRepository.createAppointment(
                Appointment(
                    customerId      = customerId,
                    salonId         = salon.id,
                    serviceName     = serviceName,
                    appointmentDate = System.currentTimeMillis() + 24L * 60 * 60 * 1000
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

    fun dismissConfirmation() { bookingConfirmation = null }

    fun triggerLock() {
        viewModelScope.launch {
            vaultRepository.log("VAULT_LOCK", "Customer locked vault")
        }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
