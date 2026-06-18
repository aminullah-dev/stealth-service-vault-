package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.db.entities.BookingEntry
import com.security.stealthapp.data.db.entities.BookingStatus
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Domain model ─────────────────────────────────────────────────────────────

data class ServiceProvider(
    val id: String,
    val name: String,
    val category: String,
    val neighborhood: String,
    val rating: Float,
    val isAvailable: Boolean,
    val speciality: String
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    // ── Static reference data ─────────────────────────────────────────────────

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

    private val allProviders: List<ServiceProvider> = listOf(
        ServiceProvider("p01", "Maryam Studio",    "Hair",     "District 3 – Khair Khana",   4.9f, true,  "Cuts & Colour"),
        ServiceProvider("p02", "Zahra Beauty",     "Makeup",   "District 6 – Karte Seh",     4.8f, true,  "Bridal & Party"),
        ServiceProvider("p03", "Fatima Nails",     "Nails",    "District 9 – Dasht-e Barchi",4.7f, false, "Gel & Acrylic"),
        ServiceProvider("p04", "Hana Skin Care",   "Skincare", "District 1 – Kabul Center",  4.6f, true,  "Facials & Peels"),
        ServiceProvider("p05", "Parisa Brows",     "Eyebrows", "District 11 – Qala-e Wahed", 4.9f, true,  "Threading & Henna"),
        ServiceProvider("p06", "Leyla Hair Art",   "Hair",     "District 9 – Dasht-e Barchi",4.8f, true,  "Braids & Keratin"),
        ServiceProvider("p07", "Sana Glam Studio", "Makeup",   "District 13 – Afshar",       4.5f, false, "Natural & HD"),
        ServiceProvider("p08", "Neda Nails Pro",   "Nails",    "District 3 – Khair Khana",   4.7f, true,  "Nail Art"),
        ServiceProvider("p09", "Rosa Skin Clinic", "Skincare", "District 6 – Karte Seh",     4.8f, true,  "Hydra & Laser"),
        ServiceProvider("p10", "Darya Brows",      "Eyebrows", "District 1 – Kabul Center",  4.6f, true,  "Micro-blading"),
        ServiceProvider("p11", "Shirin Hair",      "Hair",     "District 13 – Afshar",       4.7f, true,  "Highlights"),
        ServiceProvider("p12", "Noora Beauty",     "Makeup",   "District 9 – Dasht-e Barchi",4.9f, true,  "Airbrush Makeup")
    )

    // ── Observable state ──────────────────────────────────────────────────────

    var selectedCategory by mutableStateOf("All")
        private set

    var selectedNeighborhood by mutableStateOf("All Neighborhoods")
        private set

    var bookingConfirmation by mutableStateOf<String?>(null)
        private set

    var lockTriggered by mutableStateOf(false)
        private set

    val activeBookings: StateFlow<List<BookingEntry>> = repository.activeBookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredProviders: List<ServiceProvider>
        get() = allProviders.filter { p ->
            (selectedCategory    == "All"              || p.category     == selectedCategory) &&
            (selectedNeighborhood == "All Neighborhoods" || p.neighborhood == selectedNeighborhood)
        }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun selectCategory(category: String) {
        selectedCategory = category
    }

    fun selectNeighborhood(neighborhood: String) {
        selectedNeighborhood = neighborhood
    }

    fun bookProvider(provider: ServiceProvider) {
        viewModelScope.launch {
            repository.createBooking(
                BookingEntry(
                    serviceCategory = provider.category,
                    providerName    = provider.name,
                    neighborhood    = provider.neighborhood,
                    scheduledTime   = System.currentTimeMillis() + 24 * 60 * 60 * 1000L,
                    status          = BookingStatus.PENDING.name
                )
            )
            repository.log(
                "BOOKING_CREATED",
                "provider=${provider.name} category=${provider.category}"
            )
            bookingConfirmation =
                "Request sent to ${provider.name}. She will contact you discreetly within 24 h."
        }
    }

    fun dismissConfirmation() {
        bookingConfirmation = null
    }

    fun triggerLock() {
        viewModelScope.launch { repository.log("VAULT_LOCK", "Returned to disguise screen") }
        lockTriggered = true
    }

    fun resetLockTrigger() {
        lockTriggered = false
    }
}
