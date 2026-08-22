package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.ChatMessage
import com.safebeauty.app.data.firebase.FirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    // A conversation tied to an ended (completed/cancelled) booking is a
    // read-only archive: the history stays visible but no new messages can be
    // sent. Defaults to writable when the flag is absent.
    val readOnly: Boolean              = savedStateHandle.get<Boolean>("active") == false

    // The live tail: a bounded window of the most recent messages, kept current
    // by a listener. Older pages are fetched on demand and held separately,
    // because history does not change and re-listening to it would put the whole
    // conversation back under a listener — which is what this replaced.
    private val liveWindow: StateFlow<List<ChatMessage>> = firestoreRepository
        .observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _older = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder

    private val _reachedStart = MutableStateFlow(false)
    val reachedStart: StateFlow<Boolean> = _reachedStart

    val messages: StateFlow<List<ChatMessage>> =
        combine(_older, liveWindow) { older, live ->
            // Deduplicate by id: a message can appear in both halves if it was
            // written while an older page was in flight.
            val seen = live.map { it.id }.toSet()
            older.filterNot { it.id in seen } + live
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Fetch the page before the oldest message currently held. */
    fun loadOlderMessages() {
        if (_loadingOlder.value || _reachedStart.value) return
        val oldest = messages.value.minByOrNull { it.timestamp } ?: return
        viewModelScope.launch {
            _loadingOlder.value = true
            val page = firestoreRepository.olderMessages(conversationId, oldest.timestamp)
            if (page.isEmpty()) _reachedStart.value = true
            else _older.value = page + _older.value
            _loadingOlder.value = false
        }
    }

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
