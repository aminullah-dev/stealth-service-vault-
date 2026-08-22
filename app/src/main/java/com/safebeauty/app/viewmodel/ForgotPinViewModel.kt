package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ForgotPinViewModel @Inject constructor(
    private val auth: FirebaseAuthManager
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    // The reason an attempt failed. The screen maps each to a localized string
    // (LocalStrings) — the ViewModel must never hold a user-facing English literal,
    // or the error renders in English inside an otherwise Dari/Pashto screen.
    enum class ErrorReason { PHONE_REQUIRED, NOT_FOUND, SEND_FAILED }

    sealed class State {
        object Idle      : State()
        object Loading   : State()
        object EmailSent : State()
        object NoEmail   : State()
        data class Error(val reason: ErrorReason) : State()
    }

    var phone by mutableStateOf("")
    var state: State by mutableStateOf(State.Idle)
        private set

    fun dismissState() { state = State.Idle }

    fun sendResetLink() {
        val p = phone.trim()
        if (p.isBlank()) { state = State.Error(ErrorReason.PHONE_REQUIRED); return }

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

                val firebaseEmail = map["firebaseEmail"] as? String ?: ""
                val email         = map["email"]         as? String ?: ""

                // Only new-style accounts (real email = firebaseEmail) support email reset
                if (email.isBlank() || firebaseEmail.endsWith("@sb.app")) {
                    state = State.NoEmail
                    return@runCatching
                }

                auth.sendPasswordResetEmail(firebaseEmail).getOrThrow()
                state = State.EmailSent
            }.onFailure {
                if (state == State.Loading) {
                    state = State.Error(ErrorReason.SEND_FAILED)
                }
            }
        }
    }
}
