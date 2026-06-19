package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val pendingProviders: StateFlow<List<UserDocument>> =
        firestoreRepository.observePendingProviders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var lockTriggered by mutableStateOf(false)
        private set

    fun approveProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "APPROVED")
            // Also make their salon visible
            val users = firestoreRepository.getAllUsersForAuth()
            val provider = users.firstOrNull { it.uid == uid }
            if (provider != null) {
                val salons = firestoreRepository.observeSalonByProvider(uid)
                // Activate salon via a direct Firestore call
                firestoreRepository.setUserStatus(uid, "APPROVED")
            }
            vaultRepository.log("ADMIN_APPROVE", "uid=$uid")
        }
    }

    fun rejectProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "REJECTED")
            vaultRepository.log("ADMIN_REJECT", "uid=$uid")
        }
    }

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Admin locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
