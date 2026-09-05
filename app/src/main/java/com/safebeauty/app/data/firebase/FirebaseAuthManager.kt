package com.safebeauty.app.data.firebase

import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase Auth returned null user")
    }

    suspend fun createAccount(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Firebase Auth returned null user on creation")
    }

    suspend fun reauthenticate(email: String, password: String): Result<Unit> = runCatching {
        val credential = EmailAuthProvider.getCredential(email, password)
        auth.currentUser?.reauthenticate(credential)?.await()
            ?: error("No authenticated user")
    }

    /**
     * Mints a new ID token so a reauthentication that just happened is visible
     * to Cloud Functions.
     *
     * Reauthenticating updates the account's `auth_time`, but the callable SDK
     * sends whatever ID token is cached — which can still be the one minted at
     * sign-in hours ago. changePassword refuses a stale `auth_time`, so without
     * this the user reauthenticates successfully and is then told to sign in
     * again.
     */
    suspend fun refreshIdToken(): Result<Unit> = runCatching {
        auth.currentUser?.getIdToken(true)?.await() ?: error("No authenticated user")
        Unit
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.currentUser?.updatePassword(newPassword)?.await()
            ?: error("No authenticated user")
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://safebeauty.firebaseapp.com")
            .setHandleCodeInApp(true)
            .setAndroidPackageName("com.security.stealthapp", true, null)
            .build()
        auth.sendPasswordResetEmail(email, settings).await()
    }

    suspend fun confirmPasswordReset(oobCode: String, newPassword: String): Result<Unit> = runCatching {
        auth.confirmPasswordReset(oobCode, newPassword).await()
    }

    /**
     * Attaches a verified phone credential (from an SMS OTP) to the currently
     * signed-in account. Used at registration to *prove* the person owns the phone
     * number: a wrong or expired code makes this throw, so the caller can roll back
     * the half-created account. On success the phone is permanently linked, so no
     * orphan phone-only user is ever left behind.
     */
    suspend fun linkPhoneCredential(credential: PhoneAuthCredential): Result<Unit> = runCatching {
        auth.currentUser?.linkWithCredential(credential)?.await()
            ?: error("No authenticated user")
    }

    /** Deletes the currently signed-in account (rollback when phone linking fails). */
    suspend fun deleteCurrentUser(): Result<Unit> = runCatching {
        auth.currentUser?.delete()?.await()
            ?: error("No authenticated user")
    }

    fun signOut() = auth.signOut()
}
