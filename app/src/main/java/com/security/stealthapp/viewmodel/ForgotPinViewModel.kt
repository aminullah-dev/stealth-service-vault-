package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ForgotPinViewModel @Inject constructor(
    private val auth: FirebaseAuthManager
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    sealed class State {
        object Idle      : State()
        object Loading   : State()
        object EmailSent : State()
        object NoEmail   : State()
        data class Error(val message: String) : State()
    }

    var phone by mutableStateOf("")
    var state: State by mutableStateOf(State.Idle)
        private set

    fun dismissState() { state = State.Idle }

    fun sendResetLink() {
        val p = phone.trim()
        if (p.isBlank()) { state = State.Error("Phone number is required"); return }

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
                    state = State.Error("No account found for this phone number")
                    return@runCatching
                }

                val firebaseEmail = map["firebaseEmail"] as? String ?: ""
                val email         = map["email"]         as? String ?: ""

                // Only new-style accounts (real email = firebaseEmail) support email reset
                if (email.isBlank() || firebaseEmail.endsWith("@sb.app")) {
                    state = State.NoEmail
                    return@runCatching
                }

                auth.sendPasswordResetEmail(firebaseEmail).getOrThrow()
                state = State.EmailSent
            }.onFailure { e ->
                if (state == State.Loading) {
                    state = State.Error(e.message ?: "Could not send reset link. Try again.")
                }
            }
        }
    }
}
