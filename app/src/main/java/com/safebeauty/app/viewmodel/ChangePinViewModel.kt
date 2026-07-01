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
        if (!nPin.all { it.isDigit() }) {
            state = State.Error("PIN must contain digits only"); return
        }
        if (nPin.length < 6) {
            state = State.Error("New PIN must be at least 6 digits"); return
        }
        if (nPin == curPin) {
            state = State.Error("New PIN must be different from the current PIN"); return
        }
        if (nPin != cPin) {
            state = State.Error("New PINs do not match"); return
        }
        if (isWeakPin(nPin)) {
            state = State.Error("PIN is too easy to guess. Avoid sequences like 123456 or repeated digits."); return
        }

        viewModelScope.launch {
            state = State.Loading
            runCatching {
                val user = repo.getUserById(userId) ?: error("User not found")

                if (!pinHasher.verify(curPin, user.salt, user.pinHash)) {
                    state = State.Error("Current PIN is incorrect")
                    return@runCatching
                }

                val oldAuthPassword  = pinHasher.deriveAuthPassword(curPin, user.salt)
                val newSalt          = pinHasher.generateSalt()
                val newHash          = pinHasher.hash(nPin, newSalt)
                val newAuthPassword  = pinHasher.deriveAuthPassword(nPin, newSalt)

                auth.reauthenticate(user.firebaseEmail, oldAuthPassword).getOrThrow()
                auth.updatePassword(newAuthPassword).getOrThrow()
                // Server-side (see updatePinHash in Cloud Functions): keeps the
                // pinHash write from ever being rules-denied after the Auth
                // password has already changed, which would lock the account out.
                functions
                    .getHttpsCallable("updatePinHash")
                    .call(hashMapOf("pinHash" to newHash, "salt" to newSalt))
                    .await()

                // The stored biometric PIN is now stale — clear it so the user is
                // re-offered fast-unlock with the new PIN on next login.
                BiometricVault.disable(context)

                currentPin = ""; newPin = ""; confirmPin = ""
                state = State.Success
            }.onFailure { e ->
                if (state == State.Loading) {
                    state = State.Error(e.message ?: "Failed to change PIN. Try again.")
                }
            }
        }
    }

    private fun isWeakPin(pin: String): Boolean {
        if (pin.all { it == pin[0] }) return true
        val d = pin.map { it.digitToInt() }
        if (d.zipWithNext().all { (a, b) -> b - a == 1 }) return true
        if (d.zipWithNext().all { (a, b) -> a - b == 1 }) return true
        return false
    }
}
