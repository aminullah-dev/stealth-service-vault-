package com.safebeauty.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.ChatMessage
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.SupportHistoryDocument
import com.safebeauty.app.util.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
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

    // ── Support conversations ────────────────────────────────────────────────
    //
    // A support thread is one ever-growing chat ("support_{userId}"); the server
    // cuts it into conversations by writing a history doc each time the admin
    // closes the ticket. Nothing is moved: the owner's "current" conversation is
    // simply the messages after the newest closedAt. The admin opens the same
    // thread with her own id as myUserId, so she is not the owner and keeps
    // seeing everything.
    val isSupportOwner: Boolean = conversationId == "support_$myUserId"

    /** Closed conversations, newest first. null = not loaded yet. */
    val supportHistory: StateFlow<List<SupportHistoryDocument>?> =
        (if (isSupportOwner) firestoreRepository.observeSupportHistory(myUserId)
         else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<ChatMessage>> =
        combine(_older, liveWindow, supportHistory) { older, live, history ->
            // Deduplicate by id: a message can appear in both halves if it was
            // written while an older page was in flight.
            val seen = live.map { it.id }.toSet()
            val all = older.filterNot { it.id in seen } + live
            when {
                !isSupportOwner -> all
                // Hold the thread back until the cut-off is known, rather than
                // flashing closed conversations and then pulling them away.
                history == null -> emptyList()
                else -> {
                    val cutoff = history.firstOrNull()?.closedAt ?: 0L
                    all.filter { it.timestamp > cutoff }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-history-doc rating submission state, shared by the card and transcripts. */
    data class RatingState(
        val submitting: Boolean = false,
        val failed: Boolean = false,
        val thanked: Boolean = false
    )

    private val _ratingStates = MutableStateFlow<Map<String, RatingState>>(emptyMap())
    val ratingStates: StateFlow<Map<String, RatingState>> = _ratingStates

    /** History ids whose rating card was dismissed with "Not now" (this screen only). */
    private val _dismissedRatings = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The conversation the rating card offers, if any: only when the current
     * conversation is empty, and the newest closed one is unrated and not
     * backfilled (or was just rated here — then the card stays to say thanks).
     */
    val pendingRating: StateFlow<SupportHistoryDocument?> =
        combine(messages, supportHistory, _dismissedRatings, _ratingStates) { msgs, history, dismissed, states ->
            val newest = history?.firstOrNull()
            when {
                !isSupportOwner || newest == null || msgs.isNotEmpty() -> null
                states[newest.id]?.thanked == true -> newest
                newest.rating == 0 && !newest.backfilled && newest.id !in dismissed -> newest
                else -> null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissRating(historyId: String) {
        _dismissedRatings.value = _dismissedRatings.value + historyId
    }

    fun submitRating(historyId: String, stars: Int, comment: String) {
        if (!isSupportOwner || stars !in 1..5) return
        if (_ratingStates.value[historyId]?.submitting == true) return
        _ratingStates.value = _ratingStates.value + (historyId to RatingState(submitting = true))
        viewModelScope.launch {
            val result = runCatching {
                firestoreRepository.rateSupportConversation(myUserId, historyId, stars, comment)
            }
            result.exceptionOrNull()?.let { CrashReporter.recordNonFatal(it, "rateSupportConversation") }
            _ratingStates.value = _ratingStates.value +
                (historyId to if (result.isSuccess) RatingState(thanked = true) else RatingState(failed = true))
        }
    }

    // ── Read-only transcript of a closed conversation ────────────────────────

    data class TranscriptState(
        val historyId: String = "",
        val loading: Boolean = false,
        val failed: Boolean = false,
        val messages: List<ChatMessage> = emptyList()
    )

    private val _transcript = MutableStateFlow<TranscriptState?>(null)
    /** null = no transcript open. */
    val transcript: StateFlow<TranscriptState?> = _transcript

    fun openTranscript(entry: SupportHistoryDocument) {
        if (!isSupportOwner) return
        _transcript.value = TranscriptState(historyId = entry.id, loading = true)
        viewModelScope.launch {
            val result = runCatching {
                firestoreRepository.supportTranscript(myUserId, entry.openedAt, entry.closedAt)
            }
            result.exceptionOrNull()?.let { CrashReporter.recordNonFatal(it, "supportTranscript") }
            // Ignore a late result for a transcript that was closed or replaced.
            if (_transcript.value?.historyId != entry.id) return@launch
            _transcript.value = TranscriptState(
                historyId = entry.id,
                failed    = result.isFailure,
                messages  = result.getOrDefault(emptyList())
            )
        }
    }

    fun closeTranscript() { _transcript.value = null }

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
