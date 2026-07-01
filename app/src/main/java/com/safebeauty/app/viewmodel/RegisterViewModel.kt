package com.safebeauty.app.viewmodel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.UserDocument
import com.safebeauty.app.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val firebaseAuth: FirebaseAuthManager,
    private val pinHasher: PinHasher
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

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
    var email        by mutableStateOf("")
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
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return "Please enter a valid email address"
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
                // Real email → Firebase Auth email (enables PIN recovery via email).
                // Synthetic fallback for users who skip the optional email field.
                // Lowercased because Firebase Auth normalizes emails to lowercase —
                // the server resolves this account by the auth token's email, so a
                // mixed-case value stored here would never match it again.
                val firebaseEmail = email.trim().lowercase().ifBlank { "${uid.replace("-", "")}@sb.app" }
                val role          = if (isProvider) "PROVIDER" else "CUSTOMER"
                val status        = if (isProvider) "PENDING" else "APPROVED"

                firebaseAuth.createAccount(firebaseEmail, authPassword).getOrThrow()

                firestoreRepository.createUser(
                    UserDocument(
                        uid           = uid,
                        name          = name.trim(),
                        phone         = phone.trim(),
                        email         = email.trim(),
                        role          = role,
                        pinHash       = pinHash,
                        salt          = salt,
                        status        = status,
                        firebaseEmail = firebaseEmail,
                        createdAt     = System.currentTimeMillis()
                    )
                )

                if (isProvider) {
                    // Salon creation is server-side (createProviderSalon): the
                    // providerId must be the authoritative app-level uid, and at
                    // registration the uid_map bridge isn't populated yet, so a
                    // direct client write can't pass the security rules.
                    functions
                        .getHttpsCallable("createProviderSalon")
                        .call(hashMapOf(
                            "salonName" to salonName.trim(),
                            "district"  to district.trim(),
                            "services"  to services
                        ))
                        .await()
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
