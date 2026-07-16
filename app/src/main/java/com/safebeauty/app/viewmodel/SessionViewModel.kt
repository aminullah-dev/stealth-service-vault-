package com.safebeauty.app.viewmodel

import androidx.lifecycle.ViewModel
import com.safebeauty.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    val shouldLock: StateFlow<Boolean> = sessionManager.shouldLock
    fun onLoggedIn()    = sessionManager.onLoggedIn()
    fun onLockHandled() = sessionManager.onLockHandled()
}
