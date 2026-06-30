package com.security.stealthapp.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.security.stealthapp.data.firebase.FirebaseAuthManager
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.model.LoggedInUser
import com.security.stealthapp.data.model.UserRole
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.security.BiometricVault
import com.security.stealthapp.security.DatabaseKeyManager
import com.security.stealthapp.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val firebaseAuth: FirebaseAuthManager,
    private val pinHasher: PinHasher,
    private val vaultRepository: VaultRepository,
    private val databaseKeyManager: DatabaseKeyManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    sealed class AuthState {
        object Idle           : AuthState()
        object Authenticating : AuthState()
        data class Success(val user: LoggedInUser) : AuthState()
        object DecoyMode      : AuthState()   // decoy PIN entered → wipe + fake notepad
        object Failure        : AuthState()
    }

    var authState: AuthState by mutableStateOf(AuthState.Idle)
        private set

    fun authenticate(pin: String) {
        if (authState is AuthState.Authenticating) return

        viewModelScope.launch {
            authState = AuthState.Authenticating

            runCatching {
                // PIN verification happens server-side now — the credential table
                // is never downloaded to the device. We send only the PIN.
                val result = functions
                    .getHttpsCallable("authenticateWithPin")
                    .call(hashMapOf("pin" to pin))
                    .await()

                @Suppress("UNCHECKED_CAST")
                val map = result.getData() as? Map<String, Any?> ?: emptyMap()

                when (map["mode"] as? String) {
                    "REAL" -> {
                        val uid     = map["uid"]           as? String ?: ""
                        val name    = map["name"]          as? String ?: ""
                        val email   = map["firebaseEmail"] as? String ?: ""
                        val salt    = map["salt"]          as? String ?: ""
                        val roleStr = map["role"]          as? String ?: "CUSTOMER"

                        // Derive the Firebase Auth password from the PIN + salt and
                        // sign in (unchanged auth mechanism — only the lookup moved).
                        val authPassword = pinHasher.deriveAuthPassword(pin, salt)
                        firebaseAuth.signIn(email, authPassword).getOrThrow()

                        val role = when (roleStr) {
                            "PROVIDER" -> UserRole.PROVIDER
                            "ADMIN"    -> UserRole.ADMIN
                            else       -> UserRole.CUSTOMER
                        }
                        vaultRepository.log("AUTH_SUCCESS", "uid=$uid role=$roleStr")

                        val fcmToken = context
                            .getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                            .getString("fcm_token", null)
                        if (!fcmToken.isNullOrBlank()) {
                            runCatching { firestoreRepository.updateFcmToken(uid, fcmToken) }
                        }

                        authState = AuthState.Success(
                            LoggedInUser(uid = uid, name = name, role = role)
                        )
                    }

                    "DECOY" -> {
                        // Duress: the server has already wiped the cloud account.
                        // Now erase everything on the device — local DB rows, the
                        // SQLCipher key (cryptographic wipe), and any biometric PIN —
                        // BEFORE showing the fake notepad. No log is written, so the
                        // decoy path is forensically indistinguishable from a normal
                        // login. Order matters: clear DB rows while the key still
                        // exists, then destroy the key.
                        runCatching { vaultRepository.nukeAllData() }
                        runCatching { BiometricVault.disable(context) }
                        runCatching { databaseKeyManager.nukePassphrase() }
                        authState = AuthState.DecoyMode
                    }

                    else -> authState = AuthState.Idle // silent fail
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
