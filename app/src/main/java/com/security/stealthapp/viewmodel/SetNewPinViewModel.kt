package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SetNewPinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FirestoreRepository,
    private val auth: FirebaseAuthManager,
    private val pinHasher: PinHasher
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    val oobCode: String = checkNotNull(savedStateHandle["oobCode"])

    sealed class State {
        object Idle    : State()
        object Loading : State()
        object Success : State()
        data class Error(val message: String) : State()
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

        if (p.isBlank())                       { state = State.Error("Phone number is required"); return }
        if (np.isBlank())                      { state = State.Error("New PIN is required"); return }
        if (!np.all { it.isDigit() })          { state = State.Error("PIN must contain digits only"); return }
        if (np.length < 6)                     { state = State.Error("PIN must be at least 6 digits"); return }
        if (np != cp)                          { state = State.Error("PINs do not match"); return }
        if (isWeakPin(np))                     { state = State.Error("PIN is too easy to guess. Avoid sequences like 123456 or repeated digits."); return }

        viewModelScope.launch {
            state = State.Loading
            runCatching {
                val result = functions
                    .getHttpsCallable("lookupAccountByPhone")
                    .call(hashMapOf("phone" to p))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val map = result.getData() as? Map<String, Any?> ?: emptyMap()
                if (map["found"] != true) error("No account found for this phone number")
                val uid           = map["uid"]           as? String ?: error("No account found")
                val firebaseEmail = map["firebaseEmail"] as? String ?: ""

                val newSalt         = pinHasher.generateSalt()
                val newHash         = pinHasher.hash(np, newSalt)
                val newAuthPassword = pinHasher.deriveAuthPassword(np, newSalt)

                // Reset Firebase Auth password using the oobCode from the email link
                auth.confirmPasswordReset(oobCode, newAuthPassword).getOrThrow()

                // Sign in with the new credentials to establish a session
                auth.signIn(firebaseEmail, newAuthPassword).getOrThrow()

                // Sync Firestore PIN hash
                repo.updateUserPin(uid, newHash, newSalt)

                newPin = ""; confirmPin = ""
                state = State.Success
            }.onFailure { e ->
                if (state == State.Loading) {
                    state = State.Error(e.message ?: "Failed to reset PIN. The link may have expired.")
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
