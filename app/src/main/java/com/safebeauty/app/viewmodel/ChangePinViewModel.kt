package com.safebeauty.app.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.security.BiometricVault
import com.safebeauty.app.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FirestoreRepository,
    private val auth: FirebaseAuthManager,
    private val pinHasher: PinHasher,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle["userId"])

    private val functions = FirebaseFunctions.getInstance()

    sealed class State {
        object Idle    : State()
        object Loading : State()
        object Success : State()
        data class Error(val message: String) : State()
    }

    var currentPin by mutableStateOf("")
    var newPin     by mutableStateOf("")
    var confirmPin by mutableStateOf("")
    var state: State by mutableStateOf(State.Idle)
        private set

    fun dismissState() { state = State.Idle }

    fun changePin() {
        val curPin = currentPin.trim()
        val nPin   = newPin.trim()
        val cPin   = confirmPin.trim()

        if (curPin.isBlank() || nPin.isBlank() || cPin.isBlank()) {
            state = State.Error("All fields are required"); return
        }
        if (nPin.length < 6) {
            state = State.Error("New password must be at least 6 characters"); return
        }
        if (nPin == curPin) {
            state = State.Error("New password must be different from the current one"); return
        }
        if (nPin != cPin) {
            state = State.Error("Passwords do not match"); return
        }

        viewModelScope.launch {
            state = State.Loading
            runCatching {
                val user = repo.getUserById(userId) ?: error("User not found")

                if (!pinHasher.verify(curPin, user.salt, user.pinHash)) {
                    state = State.Error("Current password is incorrect")
                    return@runCatching
                }

                val oldAuthPassword  = pinHasher.deriveAuthPassword(curPin, user.salt)
                val newSalt          = pinHasher.generateSalt()
                val newHash          = pinHasher.hash(nPin, newSalt)
                val newAuthPassword  = pinHasher.deriveAuthPassword(nPin, newSalt)
                // Proof of the current password, not the password itself — the
                // server compares this against the stored pinHash. Same
                // principle as registerAccount: what travels is derived.
                val currentPinHash   = pinHasher.hash(curPin, user.salt)

                // Reauthentication stays, and is the security boundary. It proves
                // to Firebase that the CURRENT password was just typed —
                // currentPinHash below cannot prove that, because the rules let
                // the owner read her own document and the stored pinHash with
                // it, so a signed-in session can read that value and send it
                // back. Without this, a picked-up unlocked phone could lock the
                // owner out of her own account without knowing the password.
                //
                // The forced token refresh is not optional: reauthenticating
                // updates auth_time on the account, but the callable SDK sends
                // the cached ID token, which can still be the one minted at
                // sign-in. changePassword refuses a stale auth_time.
                auth.reauthenticate(user.firebaseEmail, oldAuthPassword).getOrThrow()
                auth.refreshIdToken().getOrThrow()

                // One call, because the ordering is not something a handset can
                // own. This used to be updatePassword() and then updatePinHash():
                // when the second failed, the Auth password was new and the
                // stored hash was old, and BOTH passwords stopped working — the
                // new one fails the hash check, and the old one passes it, is
                // handed the old salt, derives the old Auth password and is
                // refused by Firebase. The values needed to finish were local to
                // this coroutine, so there was nothing to retry with; the only
                // way back into the account was an admin reset.
                //
                // changePassword writes Firestore first and rolls it back if the
                // Auth update fails, which it can do because it has just read
                // the values it would put back. See domains/identity.js.
                functions
                    .getHttpsCallable("changePassword")
                    .call(
                        hashMapOf(
                            "currentPinHash"  to currentPinHash,
                            "newSalt"         to newSalt,
                            "newPinHash"      to newHash,
                            "newAuthPassword" to newAuthPassword
                        )
                    )
                    .await()

                // The credential on this device is now the old one. Refreshing it
                // here keeps her signed in; without it the session runs on a
                // token that outlives the password behind it and drops her at
                // some arbitrary later moment with no explanation. Best-effort:
                // the password IS changed by this point, and failing here must
                // not report that it was not.
                auth.signIn(user.firebaseEmail, newAuthPassword)

                // The stored biometric secret is now stale — clear it so the user is
                // re-offered fast-unlock with the new password on next login.
                BiometricVault.disable(context)

                currentPin = ""; newPin = ""; confirmPin = ""
                state = State.Success
            }.onFailure { e ->
                if (state == State.Loading) {
                    state = State.Error(e.message ?: "Failed to change password. Try again.")
                }
            }
        }
    }

}
