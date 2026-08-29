package com.safebeauty.app.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.model.LoggedInUser
import com.safebeauty.app.data.model.UserRole
import com.safebeauty.app.data.repository.LanguageRepository
import com.safebeauty.app.ui.theme.AppLanguage
import com.safebeauty.app.data.repository.VaultRepository
import com.safebeauty.app.security.PinHasher
import com.safebeauty.app.util.PhoneUtils
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
    private val languageRepository: LanguageRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance()

    /**
     * Why a sign-in did not work.
     *
     * Every failure read "Wrong phone number or password", including the ones
     * that were nothing of the sort. A woman on a dropped connection was told
     * her password was wrong, so she tried it again, and again — and the tenth
     * attempt in fifteen minutes locks her out for the rest of the window, which
     * she is also told is a wrong password. The advice the message gives is the
     * one thing that makes her situation worse.
     */
    enum class FailureReason { WRONG_CREDENTIALS, NO_CONNECTION, TOO_MANY_ATTEMPTS, SERVER_ERROR }

    sealed class AuthState {
        object Idle           : AuthState()
        object Authenticating : AuthState()
        data class Success(val user: LoggedInUser) : AuthState()
        data class Failure(val reason: FailureReason = FailureReason.WRONG_CREDENTIALS) : AuthState()
    }

    var authState: AuthState by mutableStateOf(AuthState.Idle)
        private set

    fun authenticate(phoneRaw: String, password: String) {
        if (authState is AuthState.Authenticating) return

        viewModelScope.launch {
            authState = AuthState.Authenticating

            runCatching {
                // Password verification happens server-side (authenticateWithPassword)
                // keyed by phone, so the credential table is never downloaded to the
                // device. We send only the phone + password.
                val phone = PhoneUtils.normalizeForLogin(phoneRaw)
                val result = functions
                    .getHttpsCallable("authenticateWithPassword")
                    .call(hashMapOf("phone" to phone, "password" to password))
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
                        // Blank (not just null) also defaults to APPROVED: the
                        // server returns status as `u.status || ""`, so a legacy /
                        // manually-created account with no status field arrives as
                        // "" and would otherwise be routed to the "under review"
                        // screen instead of its dashboard.
                        val status  = (map["status"] as? String)?.takeIf { it.isNotBlank() } ?: "APPROVED"
                        val rejectionReason = map["rejectionReason"] as? String ?: ""
                        val kycStatus = map["kycStatus"]   as? String ?: "NONE"

                        // Derive the Firebase Auth password from the password + salt
                        // and sign in (unchanged auth mechanism — only the lookup moved).
                        val authPassword = pinHasher.deriveAuthPassword(password, salt)
                        firebaseAuth.signIn(email, authPassword).getOrThrow()

                        // Bridge Firebase Auth's uid to this account's app-level uid so
                        // firestore.rules' me() can resolve identity correctly (the two
                        // schemes are different — see resolveAppUser in Cloud Functions).
                        runCatching {
                            functions.getHttpsCallable("syncUidMap")
                                .call(hashMapOf("appUid" to uid))
                                .await()
                        }

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
                        // Tell the server which language to write notifications in.
                        // Sent on every login so a language change is picked up
                        // without needing its own sync path.
                        runCatching {
                            val lang = when (languageRepository.language.value) {
                                AppLanguage.DARI   -> "fa"
                                AppLanguage.PASHTO -> "ps"
                                AppLanguage.ENGLISH -> "en"
                            }
                            firestoreRepository.updateLanguage(uid, lang)
                        }

                        authState = AuthState.Success(
                            LoggedInUser(
                                uid = uid, name = name, role = role,
                                status = status, rejectionReason = rejectionReason,
                                kycStatus = kycStatus
                            )
                        )
                    }

                    // The server looked, and there is no such account or the
                    // password does not match. This is the only case that message
                    // was ever true for.
                    else -> authState = AuthState.Failure(FailureReason.WRONG_CREDENTIALS)
                }
            }.onFailure { e ->
                authState = AuthState.Failure(failureReasonFor(e))
            }
        }
    }

    fun resetState() {
        authState = AuthState.Idle
    }

    /**
     * What actually went wrong, from the exception the callable threw.
     *
     * UNAVAILABLE and DEADLINE_EXCEEDED are the connection — common here, and
     * the reason the old blanket message did real harm. RESOURCE_EXHAUSTED is
     * authenticateWithPassword's own rate limit (ten attempts per number per
     * fifteen minutes), which is reached precisely by someone retrying a
     * password that was never the problem. UNAUTHENTICATED comes from Firebase
     * Auth refusing the derived password, which genuinely is the credential.
     */
    private fun failureReasonFor(e: Throwable): FailureReason {
        val fx = e as? FirebaseFunctionsException
        if (fx != null) {
            return when (fx.code) {
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> FailureReason.NO_CONNECTION
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> FailureReason.TOO_MANY_ATTEMPTS
                FirebaseFunctionsException.Code.UNAUTHENTICATED,
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.NOT_FOUND -> FailureReason.WRONG_CREDENTIALS
                else -> FailureReason.SERVER_ERROR
            }
        }
        // Firebase Auth's own refusal of the derived password, and anything else
        // that names the network. A bare IOException on this screen is almost
        // always the connection, not the credential.
        val name = e.javaClass.simpleName
        return when {
            name.contains("InvalidUserException") ||
            name.contains("InvalidCredentials")    -> FailureReason.WRONG_CREDENTIALS
            name.contains("UnknownHost") ||
            name.contains("Timeout") ||
            name.contains("IOException") ||
            name.contains("NetworkException")      -> FailureReason.NO_CONNECTION
            else                                   -> FailureReason.SERVER_ERROR
        }
    }
}
