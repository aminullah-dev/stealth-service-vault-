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

    // The reason an attempt failed. The screen maps each to a localized string
    // (LocalStrings) — the ViewModel must never hold a user-facing English
    // literal, or the error renders in English inside a Dari/Pashto screen.
    enum class ErrorReason {
        NAME_REQUIRED, PHONE_REQUIRED, PHONE_INVALID, EMAIL_INVALID,
        PIN_TOO_SHORT, PIN_MISMATCH,
        SALON_NAME_REQUIRED, DISTRICT_REQUIRED, SERVICES_REQUIRED,
        PHONE_CHECK_FAILED, PHONE_EXISTS, REGISTRATION_FAILED
    }

    sealed class RegisterState {
        object Idle       : RegisterState()
        object Loading    : RegisterState()
        data class CustomerSuccess(val name: String) : RegisterState()
        object ProviderPending : RegisterState()   // needs admin approval
        data class Error(val reason: ErrorReason) : RegisterState()
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

    private fun validate(): ErrorReason? {
        if (name.isBlank())            return ErrorReason.NAME_REQUIRED
        if (phone.isBlank())           return ErrorReason.PHONE_REQUIRED
        // Self-registration is customer/provider only, and those must be Afghan
        // (+93) numbers. Admin accounts (any country) are created out-of-band.
        if (!PhoneUtils.isValidAfghan(phone))
            return ErrorReason.PHONE_INVALID
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return ErrorReason.EMAIL_INVALID
        if (password.length < 6)       return ErrorReason.PIN_TOO_SHORT
        if (password != confirmPassword) return ErrorReason.PIN_MISMATCH
        if (isProvider && salonName.isBlank()) return ErrorReason.SALON_NAME_REQUIRED
        if (isProvider && district.isBlank())  return ErrorReason.DISTRICT_REQUIRED
        if (isProvider && services.isEmpty())  return ErrorReason.SERVICES_REQUIRED
        return null
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers the account: validate the form, confirm the phone number isn't
     * already taken (server-side, since the phone is the login identifier), then
     * create the account. Registration is phone + password — there is no SMS OTP
     * step (phone ownership isn't verified via SMS).
     */
    fun startRegistration() {
        // In-flight guard: a fast double-tap would otherwise launch two coroutines
        // that both pass the phone-uniqueness check before either user doc exists,
        // creating two accounts sharing one phone (login then resolves an arbitrary
        // one).
        if (state is RegisterState.Loading) return
        val error = validate()
        if (error != null) { state = RegisterState.Error(error); return }

        viewModelScope.launch {
            state = RegisterState.Loading
            val normalizedPhone = PhoneUtils.normalizeAfghan(phone)
            // The phone is the login identifier, so it must be unique. This MUST be
            // checked server-side: the users collection is not client-listable, so
            // the old client query was silently denied and always "passed".
            val exists = runCatching {
                val r = functions.getHttpsCallable("lookupAccountByPhone")
                    .call(hashMapOf("phone" to normalizedPhone))
                    .await()
                (r.getData() as? Map<*, *>)?.get("found") == true
            }.getOrElse {
                state = RegisterState.Error(ErrorReason.PHONE_CHECK_FAILED)
                return@launch
            }
            if (exists) {
                state = RegisterState.Error(ErrorReason.PHONE_EXISTS)
                return@launch
            }

            runCatching {
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

                // Once the Auth account exists, any failure of the following steps
                // must roll it back — otherwise an orphaned Auth account (no user
                // doc) permanently bricks the person: every retry hits "email
                // already in use" and login-by-phone finds nothing.
                try {
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
                            referredBy    = referredBy,
                            // Written before the salon call, so if that call never
                            // lands the details survive on the account and the next
                            // sign-in can finish the job. See ProviderViewModel.
                            pendingSalonName     = if (isProvider) salonName.trim() else "",
                            pendingSalonDistrict = if (isProvider) district.trim() else "",
                            pendingSalonServices = if (isProvider) services else emptyList()
                        )
                    )

                    if (isProvider) {
                        // Salon creation is server-side (createProviderSalon): the
                        // providerId must be the authoritative app-level uid, and at
                        // registration the uid_map bridge isn't populated yet, so a
                        // direct client write can't pass the security rules.
                        //
                        // Deliberately outside the rollback. A failure here used to
                        // delete the Auth account and leave the users document
                        // behind, and that pair is unrecoverable: the phone now has
                        // an account so registration refuses it, and there is no Auth
                        // credential behind it so signing in cannot work either. The
                        // number is burned, and the person cannot tell why.
                        //
                        // The account itself is complete and correct by this point;
                        // only the salon is missing, and the details for it are on
                        // the document. So this retries — the usual cause is a
                        // dropped connection, and createProviderSalon returns the
                        // existing salon rather than making a second one — and then
                        // gets out of the way. ProviderViewModel finishes it at her
                        // next sign-in if all three attempts failed.
                        runCatching { createSalonWithRetry(salonName.trim(), district.trim(), services) }
                        state = RegisterState.ProviderPending
                    } else {
                        state = RegisterState.CustomerSuccess(name.trim())
                    }
                } catch (e: Exception) {
                    firebaseAuth.deleteCurrentUser()
                    throw e
                }
            }.onFailure { e ->
                state = RegisterState.Error(ErrorReason.REGISTRATION_FAILED)
            }
        }
    }

    /**
     * Three attempts, widening the gap between them.
     *
     * The one call in registration that reaches the network after the account is
     * already real, made on a connection that in Kabul or Herat may simply stop
     * for a few seconds. One attempt made that a permanent outcome.
     */
    private suspend fun createSalonWithRetry(
        salonName: String,
        district: String,
        services: List<String>,
    ) {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                functions
                    .getHttpsCallable("createProviderSalon")
                    .call(hashMapOf(
                        "salonName" to salonName,
                        "district"  to district,
                        "services"  to services
                    ))
                    .await()
                return
            } catch (e: Exception) {
                lastError = e
                if (attempt < 2) kotlinx.coroutines.delay(1_000L * (attempt + 1))
            }
        }
        com.safebeauty.app.util.CrashReporter.recordNonFatal(
            lastError ?: Exception("createProviderSalon failed"),
            "createProviderSalon failed after 3 attempts; the salon will be created at next sign-in"
        )
        throw lastError ?: Exception("createProviderSalon failed")
    }
}
