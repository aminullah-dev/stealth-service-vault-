package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.db.dao.UserDao
import com.security.stealthapp.data.db.entities.User
import com.security.stealthapp.data.repository.VaultRepository
import com.security.stealthapp.security.PinHasher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles PIN authentication against the encrypted Room database.
 *
 * Flow:
 *  1. DisguiseScreen calls [authenticate] when the search field contains 4 digits.
 *  2. This VM iterates over all stored users and applies PBKDF2 verification.
 *  3. On match: [authState] becomes [AuthState.Success] and carries the [User].
 *  4. On no-match: [authState] silently resets to [Idle] — no visible feedback
 *     so the app continues to look like a plain notepad.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userDao: UserDao,
    private val pinHasher: PinHasher,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    sealed class AuthState {
        object Idle          : AuthState()
        object Authenticating : AuthState()
        data class Success(val user: User) : AuthState()
        object Failure       : AuthState()   // consumed internally, never shown in UI
    }

    var authState: AuthState by mutableStateOf(AuthState.Idle)
        private set

    fun authenticate(pin: String) {
        if (authState is AuthState.Authenticating) return // prevent re-entrant calls

        viewModelScope.launch {
            authState = AuthState.Authenticating

            val users = userDao.getAllUsersSync()
            val matched = users.firstOrNull { user ->
                pinHasher.verify(pin, user.salt, user.pinHash)
            }

            authState = if (matched != null) {
                vaultRepository.log("AUTH_SUCCESS", "userId=${matched.id} role=${matched.role}")
                AuthState.Success(matched)
            } else {
                // Silently return to Idle — do NOT display any error message.
                AuthState.Idle
            }
        }
    }

    fun resetState() {
        authState = AuthState.Idle
    }
}
