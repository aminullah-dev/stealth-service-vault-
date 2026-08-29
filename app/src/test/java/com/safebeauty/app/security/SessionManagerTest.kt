package com.safebeauty.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five-minute idle lock, and the one case where idleness is not idleness.
 *
 * The lock only sees touches inside this app. Paying through HesabPay happens
 * in a browser — reading the page, entering a number, waiting for a
 * confirmation — and every minute of it looks idle from here. Firing the lock
 * on return is the worst possible moment: the payment dialog is gone and the
 * money may already have moved.
 */
class SessionManagerTest {

    @Test
    fun `a short absence does not lock`() {
        val sm = SessionManager()
        sm.onLoggedIn()
        sm.onAppForeground()
        assertFalse(sm.shouldLock.value)
    }

    @Test
    fun `returning from checkout does not lock, however long it took`() {
        val sm = SessionManager()
        sm.onLoggedIn()
        sm.beginExternalPayment()
        // Simulated by making the last interaction old enough to trip the timer;
        // without the checkout flag this same state locks.
        sm.setLastInteractionForTest(System.currentTimeMillis() - 20 * 60 * 1000L)
        sm.onAppForeground()
        assertFalse("a customer returning from paying must not land on Login", sm.shouldLock.value)
    }

    @Test
    fun `the same absence without a checkout does lock`() {
        val sm = SessionManager()
        sm.onLoggedIn()
        sm.setLastInteractionForTest(System.currentTimeMillis() - 20 * 60 * 1000L)
        sm.onAppForeground()
        assertTrue(sm.shouldLock.value)
    }

    @Test
    fun `a stale checkout flag stops excusing the absence`() {
        // A phone left on a checkout page all afternoon is exactly the phone the
        // lock exists for. The flag defers the lock; it does not remove it.
        val sm = SessionManager()
        sm.onLoggedIn()
        sm.beginExternalPayment()
        sm.setExternalPaymentStartedForTest(System.currentTimeMillis() - 45 * 60 * 1000L)
        sm.setLastInteractionForTest(System.currentTimeMillis() - 45 * 60 * 1000L)
        sm.onAppForeground()
        assertTrue(sm.shouldLock.value)
    }

    @Test
    fun `the flag is consumed, so one checkout excuses one return`() {
        val sm = SessionManager()
        sm.onLoggedIn()
        sm.beginExternalPayment()
        sm.setLastInteractionForTest(System.currentTimeMillis() - 20 * 60 * 1000L)
        sm.onAppForeground()
        assertFalse(sm.shouldLock.value)

        // Second return, no new checkout: the ordinary rule applies again.
        sm.setLastInteractionForTest(System.currentTimeMillis() - 20 * 60 * 1000L)
        sm.onAppForeground()
        assertTrue(sm.shouldLock.value)
    }
}
