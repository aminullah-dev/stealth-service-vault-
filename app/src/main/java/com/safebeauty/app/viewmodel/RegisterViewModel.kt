package com.safebeauty.app.viewmodel

import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.security.PinHasher
import com.safebeauty.app.util.CrashReporter
import com.safebeauty.app.util.PhoneUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
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
        PHONE_CHECK_FAILED, PHONE_EXISTS, EMAIL_EXISTS, REGISTRATION_FAILED,
        // The account EXISTS. She must not register again — that comes back
        // PHONE_EXISTS and reads as a contradiction — she signs in.
        REGISTERED_NOW_SIGN_IN
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

            // One call. The device derives the password material and hands the
            // whole registration to the server, which creates the credential,
            // the profile and (for a provider) the salon inside a single
            // invocation — and cleans up after itself if any of it fails.
            //
            // It used to be three calls from here: create the Auth account,
            // write users/{uid}, and delete the account again if the write
            // failed. The last of those needed the connection whose loss was
            // the reason it was running, and its Result was discarded. 99 of
            // the first 117 Auth accounts ended up with no profile and no
            // activity of any kind behind them — registrations that stopped
            // between the first call and the second.
            //
            // The password itself does not travel: PinHasher still runs here,
            // and what goes over the wire is the salt, the stored hash and the
            // derived auth password — the same three values that already went
            // to Firebase Auth and to updatePinHash.
            val salt         = pinHasher.generateSalt()
            val pinHash      = pinHasher.hash(password, salt)
            val authPassword = pinHasher.deriveAuthPassword(password, salt)

            val result = runCatching {
                functions.getHttpsCallable("registerAccount").call(
                    hashMapOf(
                        "name"         to name.trim(),
                        "phone"        to PhoneUtils.normalizeAfghan(phone),
                        "email"        to email.trim(),
                        "role"         to if (isProvider) "PROVIDER" else "CUSTOMER",
                        "salt"         to salt,
                        "pinHash"      to pinHash,
                        "authPassword" to authPassword,
                        "referredBy"   to referralCodeInput.trim().uppercase(),
                        "salonName"    to if (isProvider) salonName.trim() else "",
                        "district"     to if (isProvider) district.trim() else "",
                        "services"     to if (isProvider) services else emptyList()
                    )
                ).await().getData() as? Map<*, *>
            }.getOrElse { e ->
                // Both collisions arrive as "already-exists", and telling her
                // the wrong one is worse than telling her nothing: someone whose
                // email is taken but whose phone is free will go and change the
                // phone number, which was the field that worked. The server says
                // which in details.field for exactly this reason.
                val ffe   = e as? FirebaseFunctionsException
                val taken = ffe?.code == FirebaseFunctionsException.Code.ALREADY_EXISTS
                val field = (ffe?.details as? Map<*, *>)?.get("field") as? String
                val reason = when {
                    taken && field == "email" -> ErrorReason.EMAIL_EXISTS
                    taken                     -> ErrorReason.PHONE_EXISTS
                    else                      -> ErrorReason.REGISTRATION_FAILED
                }
                if (!taken) CrashReporter.recordNonFatal(e, "register:callable")
                state = RegisterState.Error(reason)
                return@launch
            }

            val firebaseEmail = result?.get("firebaseEmail") as? String
            if (firebaseEmail.isNullOrBlank()) {
                CrashReporter.recordNonFatal(
                    IllegalStateException("registerAccount returned no firebaseEmail"),
                    "register:no-email"
                )
                state = RegisterState.Error(ErrorReason.REGISTRATION_FAILED)
                return@launch
            }

            // Signing in is the only step left on the device, and it is the one
            // step that is safe to fail: the account is complete and correct on
            // the server, so a person whose connection drops here opens the app
            // and logs in normally rather than being stranded half-registered.
            //
            // Safe to fail, but NOT safe to report as success. This recorded the
            // failure and then set a success state anyway, so she was shown
            // "welcome" and dropped into an app where Firebase held no
            // credential: every rule check fails isSignedIn(), every callable is
            // unauthenticated, nothing loads, and nothing says why. She is told
            // instead, and told the one thing that matters — the account exists,
            // so sign in rather than registering again.
            val signedIn = firebaseAuth.signIn(firebaseEmail, authPassword)
                .onFailure { CrashReporter.recordNonFatal(it, "register:sign-in") }
                .isSuccess
            if (!signedIn) {
                state = RegisterState.Error(ErrorReason.REGISTERED_NOW_SIGN_IN)
                return@launch
            }

            // The bridge firestore.rules resolves me() through. registerAccount
            // writes it server-side too, but that write is best-effort and its
            // own comment defers the retry to "login" — and registration is the
            // one path that never logs in afterwards. Without this, a failed
            // bridge write leaves her signed in with every personal read denied.
            // Best-effort here for the same reason it is on the login path.
            val appUid = result.get("uid") as? String
            if (!appUid.isNullOrBlank()) {
                runCatching {
                    functions.getHttpsCallable("syncUidMap")
                        .call(hashMapOf("appUid" to appUid))
                        .await()
                }
            }

            state = if (isProvider) RegisterState.ProviderPending
                    else RegisterState.CustomerSuccess(name.trim())
        }
    }
}
