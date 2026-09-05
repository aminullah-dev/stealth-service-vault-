package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import com.safebeauty.app.util.CrashReporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SetNewPinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: FirebaseAuthManager,
    private val pinHasher: PinHasher
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    val oobCode: String = checkNotNull(savedStateHandle["oobCode"])

    // The reason an attempt failed. The screen maps each to a localized string
    // (LocalStrings) — the ViewModel must never hold a user-facing English literal,
    // or the error renders in English inside an otherwise Dari/Pashto screen.
    enum class ErrorReason {
        PHONE_REQUIRED, PIN_REQUIRED, PIN_TOO_SHORT, PIN_MISMATCH, NOT_FOUND, RESET_FAILED,
        // The Auth password DID change and the link is spent; only the Firestore
        // half failed. Distinct from RESET_FAILED because the recovery and the
        // truth are both different — her old password is gone either way.
        RESET_HALF_DONE
    }

    sealed class State {
        object Idle    : State()
        object Loading : State()
        object Success : State()
        data class Error(val reason: ErrorReason) : State()
    }

    var phone      by mutableStateOf("")
    var newPin     by mutableStateOf("")
    var confirmPin by mutableStateOf("")
    var state: State by mutableStateOf(State.Idle)
        private set

    fun dismissState() { state = State.Idle }

    fun resetPin() {
        val p  = phone.trim()
        val np = newPin.trim()
        val cp = confirmPin.trim()

        if (p.isBlank())  { state = State.Error(ErrorReason.PHONE_REQUIRED); return }
        if (np.isBlank()) { state = State.Error(ErrorReason.PIN_REQUIRED); return }
        if (np.length < 6){ state = State.Error(ErrorReason.PIN_TOO_SHORT); return }
        if (np != cp)     { state = State.Error(ErrorReason.PIN_MISMATCH); return }

        viewModelScope.launch {
            state = State.Loading
            // Whether the point of no return is behind us. What she should
            // be told afterwards is not the same sentence.
            var passwordAlreadyChanged = false
            runCatching {
                val result = functions
                    .getHttpsCallable("lookupAccountByPhone")
                    .call(hashMapOf("phone" to p))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val map = result.getData() as? Map<String, Any?> ?: emptyMap()
                if (map["found"] != true) {
                    state = State.Error(ErrorReason.NOT_FOUND)
                    return@runCatching
                }
                val firebaseEmail = (map["firebaseEmail"] as? String).orEmpty()
                if (firebaseEmail.isBlank()) {
                    state = State.Error(ErrorReason.NOT_FOUND)
                    return@runCatching
                }

                val newSalt         = pinHasher.generateSalt()
                val newHash         = pinHasher.hash(np, newSalt)
                val newAuthPassword = pinHasher.deriveAuthPassword(np, newSalt)

                // Reset Firebase Auth password using the oobCode from the email link.
                //
                // This is the point of no return, and it is why this flow cannot
                // use changePassword's ordering. confirmPasswordReset consumes a
                // one-time code and cannot be undone, so "write Firestore first
                // and roll back Auth" is not available here — the order is forced,
                // and everything after this line has to be recoverable instead.
                auth.confirmPasswordReset(oobCode, newAuthPassword).getOrThrow()
                passwordAlreadyChanged = true

                // Sign in with the new credentials to establish a session.
                // Retried for the same reason updatePinHash below is: this is
                // past the point of no return too, and a dropped connection here
                // costs her the account just as completely as one two lines
                // further down.
                var session = auth.signIn(firebaseEmail, newAuthPassword)
                var signInTry = 1
                while (session.isFailure && signInTry < 3) {
                    signInTry++
                    delay(700L * (signInTry - 1))
                    session = auth.signIn(firebaseEmail, newAuthPassword)
                }
                session.getOrThrow()

                // Sync the Firestore PIN hash server-side. A direct client write
                // here can be denied by the security rules (this fresh session has
                // no uid_map entry yet) AFTER the Auth password was already reset,
                // which would desync the two and lock the account out. The function
                // resolves the caller by auth-token email and repopulates uid_map.
                //
                // Retried, because the failure that actually happens here is a
                // dropped connection on the last of four network calls — and by
                // this point her old password is already gone, so giving up on
                // the first refusal spends an account to save two seconds.
                var synced = false
                var attempt = 0
                while (!synced) {
                    attempt++
                    val r = runCatching {
                        functions
                            .getHttpsCallable("updatePinHash")
                            .call(hashMapOf("pinHash" to newHash, "salt" to newSalt))
                            .await()
                    }
                    if (r.isSuccess) { synced = true; break }
                    if (attempt >= 3) {
                        CrashReporter.recordNonFatal(
                            r.exceptionOrNull() ?: IllegalStateException("updatePinHash failed"),
                            "reset:pin-hash"
                        )
                        break
                    }
                    delay(700L * attempt)
                }
                if (!synced) {
                    // Her password HAS changed and the link is spent, so the old
                    // one will not work either. Saying "reset failed" here — which
                    // this screen renders as "the link is invalid" — sends her
                    // back to a password that is already gone.
                    state = State.Error(ErrorReason.RESET_HALF_DONE)
                    return@runCatching
                }

                newPin = ""; confirmPin = ""
                state = State.Success
            }.onFailure {
                if (state == State.Loading) {
                    state = State.Error(
                        if (passwordAlreadyChanged) ErrorReason.RESET_HALF_DONE
                        else ErrorReason.RESET_FAILED
                    )
                }
            }
        }
    }

}
