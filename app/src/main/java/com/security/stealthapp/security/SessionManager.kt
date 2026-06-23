package com.security.stealthapp.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks user inactivity and emits a lock signal after TIMEOUT_MS of idle time.
 *
 * Flow:
 *  - MainActivity.onUserInteraction() → onUserInteraction() resets the timer.
 *  - ProcessLifecycleOwner ON_START     → onAppForeground() checks elapsed idle time.
 *  - If elapsed > TIMEOUT_MS            → shouldLock emits true.
 *  - AppNavGraph collects shouldLock    → navigates to Login and calls onLockHandled().
 */
@Singleton
class SessionManager @Inject constructor() {

    companion object {
        private const val TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val _shouldLock = MutableStateFlow(false)
    val shouldLock: StateFlow<Boolean> = _shouldLock.asStateFlow()

    @Volatile private var lastInteractionMs = System.currentTimeMillis()

    fun onUserInteraction() {
        lastInteractionMs = System.currentTimeMillis()
    }

    fun onAppForeground() {
        val idleMs = System.currentTimeMillis() - lastInteractionMs
        if (idleMs > TIMEOUT_MS) _shouldLock.value = true
    }

    fun onLoggedIn() {
        lastInteractionMs = System.currentTimeMillis()
        _shouldLock.value = false
    }

    fun onLockHandled() {
        _shouldLock.value = false
    }
}
