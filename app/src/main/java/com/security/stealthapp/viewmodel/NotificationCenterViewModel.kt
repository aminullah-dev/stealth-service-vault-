package com.security.stealthapp.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.FirestoreRepository
import com.security.stealthapp.data.firebase.NotificationDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FirestoreRepository
) : ViewModel() {

    val userId: String = checkNotNull(savedStateHandle["userId"])

    val notifications: StateFlow<List<NotificationDocument>> =
        repo.observeNotifications(userId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> =
        notifications
            .map { list -> list.count { !it.isRead } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markRead(notificationId: String) {
        viewModelScope.launch { repo.markNotificationRead(notificationId) }
    }

    fun markAllRead() {
        viewModelScope.launch { repo.markAllNotificationsRead(userId) }
    }

    fun delete(notificationId: String) {
        viewModelScope.launch { repo.deleteNotification(notificationId) }
    }
}
