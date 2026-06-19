package com.security.stealthapp.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.model.LoggedInUser
import com.security.stealthapp.data.model.UserRole
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val firebaseAuth: FirebaseAuthManager,
    private val pinHasher: PinHasher,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed class AuthState {
        object Idle           : AuthState()
        object Authenticating : AuthState()
        data class Success(val user: LoggedInUser) : AuthState()
        object Failure        : AuthState()
    }

    var authState: AuthState by mutableStateOf(AuthState.Idle)
        private set

    fun authenticate(pin: String) {
        if (authState is AuthState.Authenticating) return

        viewModelScope.launch {
            authState = AuthState.Authenticating

            runCatching {
                val users   = firestoreRepository.getAllUsersForAuth()
                val matched = users.firstOrNull { u ->
                    u.status == "APPROVED" &&
                    pinHasher.verify(pin, u.salt, u.pinHash)
                }

                if (matched != null) {
                    // Sign in to Firebase with derived auth password
                    val authPassword = pinHasher.deriveAuthPassword(pin, matched.salt)
                    firebaseAuth.signIn(matched.firebaseEmail, authPassword)
                        .getOrThrow()

                    val role = when (matched.role) {
                        "PROVIDER" -> UserRole.PROVIDER
                        "ADMIN"    -> UserRole.ADMIN
                        else       -> UserRole.CUSTOMER
                    }
                    val user = LoggedInUser(uid = matched.uid, name = matched.name, role = role)
                    vaultRepository.log("AUTH_SUCCESS", "uid=${matched.uid} role=${matched.role}")

                    // Upload the locally-cached FCM token (if any) to Firestore
                    val fcmToken = context
                        .getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                        .getString("fcm_token", null)
                    if (!fcmToken.isNullOrBlank()) {
                        runCatching { firestoreRepository.updateFcmToken(matched.uid, fcmToken) }
                    }

                    authState = AuthState.Success(user)
                } else {
                    authState = AuthState.Idle // silent fail
                }
            }.onFailure {
                authState = AuthState.Idle // network error → silent fail
            }
        }
    }

    fun resetState() {
        authState = AuthState.Idle
    }
}
