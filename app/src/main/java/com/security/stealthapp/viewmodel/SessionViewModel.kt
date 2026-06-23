package com.security.stealthapp.viewmodel

import androidx.lifecycle.ViewModel
import com.security.stealthapp.security.SessionManager
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
