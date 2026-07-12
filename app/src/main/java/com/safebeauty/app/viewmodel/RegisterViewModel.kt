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
import com.safebeauty.app.util.PhoneUtils
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

    var name            by mutableStateOf("")
    var phone           by mutableStateOf("")
    var email           by mutableStateOf("")
    var password        by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var referralCodeInput by mutableStateOf("")   // optional: a friend's code
    var isProvider      by mutableStateOf(false)

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
        // Self-registration is customer/provider only, and those must be Afghan
        // (+93) numbers. Admin accounts (any country) are created out-of-band.
        if (!PhoneUtils.isValidAfghan(phone))
            return "Enter a valid Afghan phone number (e.g. 0700123456)"
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return "Please enter a valid email address"
        if (password.length < 6)       return "Password must be at least 6 characters"
        if (password != confirmPassword) return "Passwords do not match"
        if (isProvider && salonName.isBlank()) return "Salon name is required"
        if (isProvider && district.isBlank())  return "District is required"
        if (isProvider && services.isEmpty())  return "Add at least one service"
        return null
    }

    // ── Registration ──────────────────────────────────────────────────────────

    fun register() {
        val error = validate()
        if (error != null) { state = RegisterState.Error(error); return }

        viewModelScope.launch {
            state = RegisterState.Loading

            runCatching {
                val normalizedPhone = PhoneUtils.normalizeAfghan(phone)
                // The phone is the login identifier now, so it must be unique.
                if (firestoreRepository.phoneExists(normalizedPhone)) {
                    state = RegisterState.Error("An account with this phone number already exists.")
                    return@launch
                }

                val uid           = UUID.randomUUID().toString()
                val salt          = pinHasher.generateSalt()
                // pinHash/salt now hash the chosen PASSWORD (same PBKDF2 machinery
                // as before; only the human-facing credential changed).
                val pinHash       = pinHasher.hash(password, salt)
                val authPassword  = pinHasher.deriveAuthPassword(password, salt)
                // Real email → Firebase Auth email (enables password recovery via
                // email). Synthetic fallback for users who skip the optional field.
                // Lowercased because Firebase Auth normalizes emails to lowercase.
                val firebaseEmail = email.trim().lowercase().ifBlank { "${uid.replace("-", "")}@sb.app" }
                val role          = if (isProvider) "PROVIDER" else "CUSTOMER"
                val status        = if (isProvider) "PENDING" else "APPROVED"
                // This user's own shareable referral code, derived from their uid
                // (unique). referralCredit stays 0 — it's granted only server-side
                // (reviewKyc) once identity is verified, so it can't be self-seeded.
                val referralCode  = "SB" + uid.replace("-", "").take(6).uppercase()
                val referredBy    = referralCodeInput.trim().uppercase()
                    .takeIf { it != referralCode }   // can't refer yourself
                    .orEmpty()

                firebaseAuth.createAccount(firebaseEmail, authPassword).getOrThrow()

                firestoreRepository.createUser(
                    UserDocument(
                        uid           = uid,
                        name          = name.trim(),
                        phone         = normalizedPhone,
                        email         = email.trim(),
                        role          = role,
                        pinHash       = pinHash,
                        salt          = salt,
                        status        = status,
                        firebaseEmail = firebaseEmail,
                        createdAt     = System.currentTimeMillis(),
                        referralCode  = referralCode,
                        referredBy    = referredBy
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
