package com.security.stealthapp.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.security.stealthapp.data.firebase.AppointmentDocument
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.GalleryImageDocument
import com.security.stealthapp.data.firebase.ReviewDocument
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.LoyaltyTier
import com.security.stealthapp.data.firebase.WaitlistEntry
import com.security.stealthapp.data.firebase.WorkingHours
import java.util.Calendar
import com.security.stealthapp.data.repository.FavoritesRepository
import com.security.stealthapp.data.repository.LanguageRepository
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.ui.theme.StringResources
import com.security.stealthapp.workers.ReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Internal English keys used for Firestore filtering — independent of display language.
private val CATEGORY_KEYS = listOf("All", "Hair", "Makeup", "Nails", "Skincare", "Eyebrows")
private val NEIGHBORHOOD_KEYS = listOf(
    "All Neighborhoods",
    "District 1 – Kabul Center",
    "District 3 – Khair Khana",
    "District 6 – Karte Seh",
    "District 9 – Dasht-e Barchi",
    "District 11 – Qala-e Wahed",
    "District 13 – Afshar"
)

data class BookingStatusChange(val salonName: String, val newStatus: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository,
    private val languageRepository: LanguageRepository,
    private val favoritesRepository: FavoritesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val customerId: String = checkNotNull(savedStateHandle["userId"])

    val categoryCount     = CATEGORY_KEYS.size
    val neighborhoodCount = NEIGHBORHOOD_KEYS.size

    private val _selectedCategoryIndex      = MutableStateFlow(0)
    private val _selectedNeighborhoodIndex  = MutableStateFlow(0)
    private val _currentUserName            = MutableStateFlow("")
    private val _currentUserPhone           = MutableStateFlow("")
    private val _isOffline                  = MutableStateFlow(false)
    private val _showFavoritesOnly          = MutableStateFlow(false)
    private val _searchQuery                = MutableStateFlow("")
    private val _bookingStatusChange        = MutableSharedFlow<BookingStatusChange>(extraBufferCapacity = 4)
    private val _waitlistSlotAvailable      = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var previousStatuses: Map<String, String>         = emptyMap()
    private var previousWaitlistStatuses: Map<String, String> = emptyMap()

    val selectedCategoryIndex: StateFlow<Int>          = _selectedCategoryIndex
    val selectedNeighborhoodIndex: StateFlow<Int>      = _selectedNeighborhoodIndex
    val currentUserName: StateFlow<String>             = _currentUserName
    val isOffline: StateFlow<Boolean>                  = _isOffline
    val showFavoritesOnly: StateFlow<Boolean>          = _showFavoritesOnly
    val searchQuery: StateFlow<String>                 = _searchQuery
    val bookingStatusChange: SharedFlow<BookingStatusChange> = _bookingStatusChange
    val waitlistSlotAvailable: SharedFlow<String>      = _waitlistSlotAvailable

    val favoriteIds: StateFlow<Set<String>> = favoritesRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var reviewThanksShown by mutableStateOf(false)
        private set

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOffline.value = false }
        override fun onLost(network: Network)      { _isOffline.value = true }
    }

    private val _allAvailableSalons: StateFlow<List<SalonDocument>> =
        firestoreRepository.observeAvailableSalons()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredSalons: StateFlow<List<SalonDocument>> = combine(
        combine(
            _allAvailableSalons,
            _selectedCategoryIndex,
            _selectedNeighborhoodIndex,
            favoriteIds,
            _showFavoritesOnly
        ) { salons, catIdx, hoodIdx, favorites, favOnly ->
            val category     = CATEGORY_KEYS.getOrElse(catIdx) { "All" }
            val neighborhood = NEIGHBORHOOD_KEYS.getOrElse(hoodIdx) { "All Neighborhoods" }
            salons.filter { salon ->
                val catMatch  = category == "All" ||
                    salon.services.any { it.contains(category, ignoreCase = true) }
                val hoodMatch = neighborhood == "All Neighborhoods" ||
                    salon.district == neighborhood
                val favMatch  = !favOnly || favorites.contains(salon.id)
                catMatch && hoodMatch && favMatch
            }
        },
        _searchQuery
    ) { preFilt, query ->
        if (query.isBlank()) preFilt
        else preFilt.filter { it.salonName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myAppointments: StateFlow<List<AppointmentDocument>> =
        firestoreRepository.observeForCustomer(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myWaitlist: StateFlow<List<WaitlistEntry>> =
        firestoreRepository.observeMyWaitlist(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Salons scored by how well they match the customer's booking history.
    // Requires ≥1 past appointment; shows up to 5 recommendations.
    val recommendedSalons: StateFlow<List<SalonDocument>> = combine(
        _allAvailableSalons, myAppointments
    ) { salons, appointments ->
        if (appointments.isEmpty()) return@combine emptyList()
        val serviceFreq   = appointments.groupingBy { it.serviceName }.eachCount()
        val districtFreq  = appointments
            .mapNotNull { appt -> salons.find { it.id == appt.salonId }?.district }
            .groupingBy { it }.eachCount()
        val topDistrict   = districtFreq.maxByOrNull { it.value }?.key
        salons
            .map { salon ->
                val serviceScore  = salon.services.sumOf { (serviceFreq[it] ?: 0) * 2 }
                val districtScore = if (salon.district == topDistrict) 1 else 0
                val ratingBonus   = if (salon.rating >= 4.5) 1 else 0
                salon to (serviceScore + districtScore + ratingBonus)
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(5)
            .map { (salon, _) -> salon }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loyaltyPoints: StateFlow<Int> =
        firestoreRepository.observeUserLoyaltyPoints(customerId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val loyaltyTier: StateFlow<LoyaltyTier> = loyaltyPoints
        .map { pts ->
            when {
                pts >= 150 -> LoyaltyTier.VIP
                pts >= 50  -> LoyaltyTier.REGULAR
                else       -> LoyaltyTier.NEWCOMER
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoyaltyTier.NEWCOMER)

    private val _activeSalonId = MutableStateFlow("")

    val reviewsForSalon: StateFlow<List<ReviewDocument>> = _activeSalonId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else firestoreRepository.observeReviewsForSalon(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val galleryForSalon: StateFlow<List<GalleryImageDocument>> = _activeSalonId
        .flatMapLatest { id ->
            if (id.isEmpty()) flowOf(emptyList())
            else firestoreRepository.observeGalleryForSalon(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActiveSalon(id: String) { _activeSalonId.value = id }

    var bookingConfirmSalonName  by mutableStateOf<String?>(null)
        private set
    var waitlistJoinedSalonName by mutableStateOf<String?>(null)
        private set

    var lockTriggered by mutableStateOf(false)
        private set

    var availableSlots by mutableStateOf<List<Long>>(emptyList())
        private set
    var slotsLoading by mutableStateOf(false)
        private set
    var noWorkingHours by mutableStateOf(false)
        private set

    // ── Customer profile editing ──────────────────────────────────────────────
    var editName by mutableStateOf("")
        private set
    var profileSaveSuccess by mutableStateOf(false)
        private set

    init {
        // Check current connectivity state
        val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        _isOffline.value = caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // Fetch current user's display name
        viewModelScope.launch {
            runCatching { firestoreRepository.getUserById(customerId) }
                .getOrNull()
                ?.let {
                    _currentUserName.value = it.name
                    _currentUserPhone.value = it.phone
                    editName = it.name
                }
        }

        // Detect appointment status changes and emit for the UI to show a notification
        viewModelScope.launch {
            myAppointments.collect { appointments ->
                val current = appointments.associateBy({ it.id }, { it.status })
                previousStatuses.forEach { (id, prevStatus) ->
                    val newStatus = current[id]
                    if (newStatus != null && newStatus != prevStatus) {
                        val appt = appointments.find { it.id == id }
                        if (appt != null) {
                            _bookingStatusChange.emit(BookingStatusChange(appt.salonName, newStatus))
                            if (newStatus == "CONFIRMED") scheduleReminders(appt)
                        }
                    }
                }
                previousStatuses = current
            }
        }

        // Detect waitlist slot-available transitions for push notifications
        viewModelScope.launch {
            myWaitlist.collect { entries ->
                val current = entries.associateBy({ it.id }, { it.status })
                previousWaitlistStatuses.forEach { (id, prevStatus) ->
                    val newStatus = current[id]
                    if (prevStatus == "WAITING" && newStatus == "SLOT_AVAILABLE") {
                        val entry = entries.find { it.id == id }
                        if (entry != null) _waitlistSlotAvailable.emit(entry.salonName)
                    }
                }
                previousWaitlistStatuses = current
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun selectCategory(index: Int)     { _selectedCategoryIndex.value = index }
    fun selectNeighborhood(index: Int) { _selectedNeighborhoodIndex.value = index }
    fun toggleFavoritesOnly()          { _showFavoritesOnly.value = !_showFavoritesOnly.value }
    fun setSearchQuery(q: String)      { _searchQuery.value = q }

    fun onEditNameChanged(v: String) { editName = v }

    fun saveCustomerProfile() {
        val trimmed = editName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.updateUserName(customerId, trimmed)
                _currentUserName.value = trimmed
                vaultRepository.log("CUSTOMER_PROFILE_UPDATED", "name=${trimmed.take(20)}")
                profileSaveSuccess = true
            }
        }
    }

    fun dismissProfileSaveSuccess() { profileSaveSuccess = false }

    fun toggleFavorite(salonId: String) {
        viewModelScope.launch { runCatching { favoritesRepository.toggle(salonId) } }
    }

    fun cancelAppointment(appointmentId: String) {
        viewModelScope.launch {
            runCatching {
                val appt = myAppointments.value.find { it.id == appointmentId }
                firestoreRepository.updateAppointmentStatus(appointmentId, "CANCELLED")
                if (appt != null) {
                    runCatching { firestoreRepository.notifyFirstWaiting(appt.salonId, appt.appointmentDate) }
                }
                vaultRepository.log("APPOINTMENT_CANCELLED", "id=$appointmentId customerId=$customerId")
            }
        }
    }

    fun joinWaitlist(salon: SalonDocument, dateMs: Long) {
        viewModelScope.launch {
            runCatching {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                firestoreRepository.addToWaitlist(
                    WaitlistEntry(
                        salonId       = salon.id,
                        salonName     = salon.salonName,
                        customerId    = customerId,
                        customerName  = _currentUserName.value,
                        requestedDate = cal.timeInMillis,
                        createdAt     = System.currentTimeMillis()
                    )
                )
                vaultRepository.log("WAITLIST_JOIN", "salonId=${salon.id}")
                waitlistJoinedSalonName = salon.salonName
            }
        }
    }

    fun leaveWaitlist(entryId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.removeFromWaitlist(entryId) }
        }
    }

    fun dismissWaitlistSlot(entryId: String) {
        viewModelScope.launch {
            runCatching { firestoreRepository.dismissWaitlistEntry(entryId) }
        }
    }

    fun dismissWaitlistJoined() { waitlistJoinedSalonName = null }

    fun rescheduleAppointment(appointmentId: String, newDateMs: Long) {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.rescheduleAppointment(appointmentId, newDateMs)
                vaultRepository.log("APPOINTMENT_RESCHEDULED", "id=$appointmentId date=$newDateMs")
            }
        }
    }

    fun submitReview(salonId: String, rating: Int, comment: String) {
        if (rating < 1) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.addReview(
                    ReviewDocument(
                        salonId      = salonId,
                        customerId   = customerId,
                        customerName = _currentUserName.value,
                        rating       = rating,
                        comment      = comment.trim(),
                        createdAt    = System.currentTimeMillis()
                    )
                )
                vaultRepository.log("REVIEW_SUBMITTED", "salonId=$salonId rating=$rating")
                reviewThanksShown = true
            }
        }
    }

    fun dismissReviewThanks() { reviewThanksShown = false }

    private fun scheduleReminders(appt: AppointmentDocument) {
        val strings = StringResources.forLanguage(languageRepository.language.value)
        val title   = strings.reminderTitle
        val now     = System.currentTimeMillis()
        val wm      = WorkManager.getInstance(context)

        val delay24h = appt.appointmentDate - now - 24L * 60 * 60 * 1000
        if (delay24h > 0) {
            wm.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay24h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        ReminderWorker.KEY_TITLE to title,
                        ReminderWorker.KEY_BODY  to strings.remindedTomorrow(appt.salonName)
                    ))
                    .build()
            )
        }

        val delay1h = appt.appointmentDate - now - 60L * 60 * 1000
        if (delay1h > 0) {
            wm.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delay1h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        ReminderWorker.KEY_TITLE to title,
                        ReminderWorker.KEY_BODY  to strings.remindedInHour(appt.salonName)
                    ))
                    .build()
            )
        }
    }

    fun bookService(salon: SalonDocument, serviceName: String, appointmentDateMs: Long) {
        viewModelScope.launch {
            runCatching {
                firestoreRepository.createAppointment(
                    AppointmentDocument(
                        customerId      = customerId,
                        customerName    = _currentUserName.value,
                        customerPhone   = _currentUserPhone.value,
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
                bookingConfirmSalonName = salon.salonName
            }
        }
    }

    fun dismissConfirmation() { bookingConfirmSalonName = null }

    fun loadSlotsForDate(salon: SalonDocument, dateMs: Long) {
        viewModelScope.launch {
            slotsLoading = true
            noWorkingHours = false
            val booked = runCatching {
                firestoreRepository.getBookedSlotsForSalon(salon.id, dateMs)
            }.getOrDefault(emptyList())
            val slots = computeSlots(salon, dateMs, booked)
            if (salon.workingHours.isEmpty()) noWorkingHours = true
            availableSlots = slots
            slotsLoading = false
        }
    }

    fun clearSlots() {
        availableSlots = emptyList()
        slotsLoading = false
        noWorkingHours = false
    }

    private fun computeSlots(salon: SalonDocument, dateMs: Long, booked: List<Long>): List<Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val wh = salon.workingHours.find { it.dayOfWeek == dayOfWeek } ?: return emptyList()
        if (!wh.isOpen) return emptyList()
        val slotDuration = salon.slotDurationMinutes.coerceAtLeast(30)
        val bookedSet = booked.toSet()
        val slots = mutableListOf<Long>()
        val openCal = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, wh.openHour)
            set(Calendar.MINUTE, wh.openMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val closeMs = Calendar.getInstance().apply {
            timeInMillis = dateMs
            set(Calendar.HOUR_OF_DAY, wh.closeHour)
            set(Calendar.MINUTE, wh.closeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        while (openCal.timeInMillis + slotDuration * 60_000L <= closeMs) {
            val slotMs = openCal.timeInMillis
            if (slotMs > now && !bookedSet.contains(slotMs)) slots.add(slotMs)
            openCal.add(Calendar.MINUTE, slotDuration)
        }
        return slots
    }

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Customer locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
