package com.security.stealthapp.data.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        auth.currentUser?.updatePassword(newPassword)?.await()
            ?: error("No authenticated user")
    }

    fun signOut() = auth.signOut()
}
