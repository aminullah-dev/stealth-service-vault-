package com.safebeauty.app.security

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
        /** How long a checkout may plausibly take before the flag is stale. */
        private const val EXTERNAL_GRACE_MS = 30 * 60 * 1000L
    }

    private val _shouldLock = MutableStateFlow(false)
    val shouldLock: StateFlow<Boolean> = _shouldLock.asStateFlow()

    @Volatile private var lastInteractionMs = System.currentTimeMillis()

    /** When the customer left for an external checkout, or 0 when none is open. */
    @Volatile private var externalPaymentStartedMs = 0L

    fun onUserInteraction() {
        lastInteractionMs = System.currentTimeMillis()
    }

    /**
     * The customer has left for HesabPay's checkout in a browser.
     *
     * Paying takes minutes — reading a page, entering a number, waiting for a
     * confirmation — and every one of them counts as idle to a timer that only
     * watches touches inside this app. Coming back to the login screen with the
     * payment dialog gone is the worst possible moment for the lock to fire: the
     * money may already have moved.
     *
     * Not an exemption from the lock, a deferral of it. If the customer never
     * comes back within [EXTERNAL_GRACE_MS] the flag is stale and the ordinary
     * rule applies — a phone left on a checkout page all afternoon is exactly
     * the phone the lock exists for.
     */
    fun beginExternalPayment() {
        externalPaymentStartedMs = System.currentTimeMillis()
    }

    fun onAppForeground() {
        val now = System.currentTimeMillis()
        val started = externalPaymentStartedMs
        if (started > 0L) {
            externalPaymentStartedMs = 0L
            if (now - started <= EXTERNAL_GRACE_MS) {
                // Returning from checkout is a deliberate return, not idleness.
                lastInteractionMs = now
                return
            }
        }
        if (now - lastInteractionMs > TIMEOUT_MS) _shouldLock.value = true
    }

    fun onLoggedIn() {
        externalPaymentStartedMs = 0L
        lastInteractionMs = System.currentTimeMillis()
        _shouldLock.value = false
    }

    // Time is the input this class reasons about, and a test cannot wait twenty
    // minutes to supply it. Visible for testing only; nothing in the app calls
    // either of these.
    internal fun setLastInteractionForTest(ms: Long) { lastInteractionMs = ms }
    internal fun setExternalPaymentStartedForTest(ms: Long) { externalPaymentStartedMs = ms }

    fun onLockHandled() {
        _shouldLock.value = false
    }
}
