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
        PHONE_REQUIRED, PIN_REQUIRED, PIN_TOO_SHORT, PIN_MISMATCH, NOT_FOUND, RESET_FAILED
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

                // Reset Firebase Auth password using the oobCode from the email link
                auth.confirmPasswordReset(oobCode, newAuthPassword).getOrThrow()

                // Sign in with the new credentials to establish a session
                auth.signIn(firebaseEmail, newAuthPassword).getOrThrow()

                // Sync the Firestore PIN hash server-side. A direct client write
                // here can be denied by the security rules (this fresh session has
                // no uid_map entry yet) AFTER the Auth password was already reset,
                // which would desync the two and lock the account out. The function
                // resolves the caller by auth-token email and repopulates uid_map.
                functions
                    .getHttpsCallable("updatePinHash")
                    .call(hashMapOf("pinHash" to newHash, "salt" to newSalt))
                    .await()

                newPin = ""; confirmPin = ""
                state = State.Success
            }.onFailure {
                if (state == State.Loading) {
                    state = State.Error(ErrorReason.RESET_FAILED)
                }
            }
        }
    }

}
