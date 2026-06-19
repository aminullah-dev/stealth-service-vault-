package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.firebase.ChatMessage
import com.security.stealthapp.data.firebase.FirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    val myUserId: String               = checkNotNull(savedStateHandle["myUserId"])
    val myName: String                 = checkNotNull(savedStateHandle["myName"])
    val otherName: String              = checkNotNull(savedStateHandle["otherName"])

    val messages: StateFlow<List<ChatMessage>> = firestoreRepository
        .observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var draft by mutableStateOf("")
        private set

    fun onDraftChanged(value: String) { draft = value }

    fun send() {
        val content = draft.trim()
        if (content.isEmpty()) return
        draft = ""
        viewModelScope.launch {
            runCatching {
                firestoreRepository.sendChatMessage(
                    ChatMessage(
                        conversationId = conversationId,
                        senderId       = myUserId,
                        senderName     = myName,
                        content        = content,
                        timestamp      = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
