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

/**
 * Self-service account deletion, required by Google Play's User Data policy for
 * any app that creates accounts in-app.
 *
 * The client cannot delete its own user document — the security rules leave no
 * such path — so everything happens in the `requestAccountDeletion` callable:
 * it cancels live bookings, strips personal fields from the financial records
 * the salons must keep, removes the KYC images, deletes users/{uid} + uid_map,
 * and finally closes the Firebase Auth account.
 *
 * Once that returns we sign out locally so the now-orphaned session can't linger.
 */
@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val auth: FirebaseAuthManager
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    sealed class State {
        object Idle    : State()
        object Loading : State()
        object Deleted : State()
        object Failed  : State()
    }

    var state: State by mutableStateOf(State.Idle)
        private set

    fun dismissState() { state = State.Idle }

    fun deleteAccount() {
        if (state is State.Loading) return
        viewModelScope.launch {
            state = State.Loading
            runCatching {
                functions.getHttpsCallable("requestAccountDeletion")
                    .call(hashMapOf<String, Any>())
                    .await()
            }.onSuccess {
                // The Auth user is already gone server-side; this clears the
                // cached local session so the app returns to a signed-out state.
                runCatching { auth.signOut() }
                state = State.Deleted
            }.onFailure {
                state = State.Failed
            }
        }
    }
}
