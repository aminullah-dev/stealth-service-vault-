package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.BroadcastDocument
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.SalonDocument
import com.security.stealthapp.data.firebase.UserDocument
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemStats(
    val totalUsers: Int = 0,
    val providers: Int = 0,
    val customers: Int = 0,
    val pendingApprovals: Int = 0,
    val totalSalons: Int = 0,
    val suspendedUsers: Int = 0
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    val pendingProviders: StateFlow<List<UserDocument>> =
        firestoreRepository.observePendingProviders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allUsers: StateFlow<List<UserDocument>> =
        firestoreRepository.observeAllUsers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSalons: StateFlow<List<SalonDocument>> =
        firestoreRepository.observeAllSalons()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<SystemStats> = combine(allUsers, allSalons) { users, salons ->
        SystemStats(
            totalUsers       = users.size,
            providers        = users.count { it.role == "PROVIDER" },
            customers        = users.count { it.role == "CUSTOMER" },
            pendingApprovals = users.count { it.status == "PENDING" },
            totalSalons      = salons.size,
            suspendedUsers   = users.count { it.status == "SUSPENDED" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SystemStats())

    val broadcasts: StateFlow<List<BroadcastDocument>> =
        firestoreRepository.observeBroadcasts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var broadcastText by mutableStateOf("")
    var lockTriggered by mutableStateOf(false)
        private set

    fun suspendUser(uid: String) {
        viewModelScope.launch {
            firestoreRepository.suspendUser(uid)
            vaultRepository.log("ADMIN_SUSPEND", "uid=$uid")
        }
    }

    fun unsuspendUser(uid: String) {
        viewModelScope.launch {
            firestoreRepository.unsuspendUser(uid)
            vaultRepository.log("ADMIN_UNSUSPEND", "uid=$uid")
        }
    }

    fun approveProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "APPROVED")
            vaultRepository.log("ADMIN_APPROVE", "uid=$uid")
        }
    }

    fun rejectProvider(uid: String) {
        viewModelScope.launch {
            firestoreRepository.setUserStatus(uid, "REJECTED")
            vaultRepository.log("ADMIN_REJECT", "uid=$uid")
        }
    }

    fun deleteUser(uid: String, isProvider: Boolean) {
        viewModelScope.launch {
            firestoreRepository.deleteUser(uid)
            if (isProvider) firestoreRepository.deleteSalonByProvider(uid)
            vaultRepository.log("ADMIN_DELETE_USER", "uid=$uid")
        }
    }

    fun sendBroadcast() {
        val msg = broadcastText.trim()
        if (msg.isBlank()) return
        viewModelScope.launch {
            runCatching {
                firestoreRepository.sendBroadcast(
                    BroadcastDocument(message = msg, createdAt = System.currentTimeMillis())
                )
                broadcastText = ""
                vaultRepository.log("ADMIN_BROADCAST", "msg=${msg.take(40)}")
            }
        }
    }

    fun triggerLock() {
        viewModelScope.launch { vaultRepository.log("VAULT_LOCK", "Admin locked vault") }
        lockTriggered = true
    }

    fun resetLockTrigger() { lockTriggered = false }
}
