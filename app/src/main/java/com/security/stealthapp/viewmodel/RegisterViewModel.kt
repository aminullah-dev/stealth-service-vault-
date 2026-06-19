package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val firebaseAuth: FirebaseAuthManager,
    private val pinHasher: PinHasher
) : ViewModel() {

    sealed class RegisterState {
        object Idle       : RegisterState()
        object Loading    : RegisterState()
        data class CustomerSuccess(val name: String) : RegisterState()
        object ProviderPending : RegisterState()   // needs admin approval
        data class Error(val message: String) : RegisterState()
    }

    // ── Form fields ───────────────────────────────────────────────────────────

    var name         by mutableStateOf("")
    var phone        by mutableStateOf("")
    var pin          by mutableStateOf("")
    var confirmPin   by mutableStateOf("")
    var isProvider   by mutableStateOf(false)

    var salonName    by mutableStateOf("")
    var district     by mutableStateOf("")
    var serviceInput by mutableStateOf("")
    var services     by mutableStateOf<List<String>>(emptyList())

    var state: RegisterState by mutableStateOf(RegisterState.Idle)
        private set

    // ── Field actions ─────────────────────────────────────────────────────────

    fun addService() {
        val s = serviceInput.trim()
        if (s.isNotBlank() && !services.contains(s)) {
            services = services + s
            serviceInput = ""
        }
    }

    fun removeService(s: String) { services = services.filter { it != s } }

    fun dismissState() { state = RegisterState.Idle }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validate(): String? {
        if (name.isBlank())            return "Name is required"
        if (phone.isBlank())           return "Phone number is required"
        if (!pin.all { it.isDigit() }) return "PIN must contain digits only"
        if (pin.length < 6)            return "PIN must be at least 6 digits"
        if (isWeakPin(pin))            return "PIN is too easy to guess. Avoid sequences like 123456 or repeated digits like 000000."
        if (pin != confirmPin)         return "PINs do not match"
        if (isProvider && salonName.isBlank()) return "Salon name is required"
        if (isProvider && district.isBlank())  return "District is required"
        if (isProvider && services.isEmpty())  return "Add at least one service"
        return null
    }

    private fun isWeakPin(pin: String): Boolean {
        if (pin.all { it == pin[0] }) return true
        val digits = pin.map { it.digitToInt() }
        if (digits.zipWithNext().all { (a, b) -> b - a == 1 }) return true
        if (digits.zipWithNext().all { (a, b) -> a - b == 1 }) return true
        return false
    }

    // ── Registration ──────────────────────────────────────────────────────────

    fun register() {
        val error = validate()
        if (error != null) { state = RegisterState.Error(error); return }

        viewModelScope.launch {
            state = RegisterState.Loading

            runCatching {
                val uid           = UUID.randomUUID().toString()
                val salt          = pinHasher.generateSalt()
                val pinHash       = pinHasher.hash(pin, salt)
                val authPassword  = pinHasher.deriveAuthPassword(pin, salt)
                val firebaseEmail = "${uid.replace("-", "")}@sb.app"
                val role          = if (isProvider) "PROVIDER" else "CUSTOMER"
                val status        = if (isProvider) "PENDING" else "APPROVED"

                firebaseAuth.createAccount(firebaseEmail, authPassword).getOrThrow()

                firestoreRepository.createUser(
                    UserDocument(
                        uid           = uid,
                        name          = name.trim(),
                        phone         = phone.trim(),
                        role          = role,
                        pinHash       = pinHash,
                        salt          = salt,
                        status        = status,
                        firebaseEmail = firebaseEmail,
                        createdAt     = System.currentTimeMillis()
                    )
                )

                if (isProvider) {
                    firestoreRepository.createSalon(
                        SalonDocument(
                            providerId   = uid,
                            providerName = name.trim(),
                            salonName    = salonName.trim(),
                            district     = district.trim(),
                            services     = services,
                            isAvailable  = false,  // starts offline until admin approves
                            rating       = 0.0
                        )
                    )
                    state = RegisterState.ProviderPending
                } else {
                    state = RegisterState.CustomerSuccess(name.trim())
                }
            }.onFailure { e ->
                state = RegisterState.Error(e.message ?: "Registration failed. Try again.")
            }
        }
    }
}
