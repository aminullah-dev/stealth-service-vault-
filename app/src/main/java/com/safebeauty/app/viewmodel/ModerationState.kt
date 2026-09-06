package com.safebeauty.app.viewmodel

import com.safebeauty.app.data.firebase.FirestoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the report sheet is open on. */
data class ReportTarget(
    val type: String,
    val id: String,
    /** What a block filters on — a salonId for a post or story, a uid otherwise. */
    val authorId: String,
    val authorKind: String,
)

/**
 * Reporting and blocking, shared by every screen that shows other people's
 * content.
 *
 * One class rather than the same forty lines in two view models: the feed and
 * the salon sheet both carry customer comments and customer reviews, and a
 * block made on one has to be honoured by the other. Two copies of this state
 * would be two answers to "has she blocked him".
 */
class ModerationState(
    private val repo: FirestoreRepository,
    private val userId: String,
    private val scope: CoroutineScope,
) {
    /** Uids and salonIds this customer has blocked, live — she blocks from a
     *  comment thread and the feed behind it must stop showing them at once. */
    val blocked: StateFlow<Set<String>> =
        repo.observeBlocked(userId)
            .catch { emit(emptySet()) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _reporting = MutableStateFlow<ReportTarget?>(null)
    val reporting: StateFlow<ReportTarget?> = _reporting

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent

    /** True when the last attempt failed. The sheet says so — a report that
     *  fails silently is a control that looks broken. */
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    fun start(target: ReportTarget) {
        _sent.value = false
        _failed.value = false
        _reporting.value = target
    }

    fun cancel() {
        _reporting.value = null
        _sent.value = false
        _failed.value = false
    }

    fun submit(reason: String, note: String, alsoBlock: Boolean) {
        val target = _reporting.value ?: return
        if (_sending.value) return
        _sending.value = true
        _failed.value = false
        scope.launch {
            val ok = runCatching {
                repo.reportContent(target.type, target.id, reason, note)
            }.isSuccess
            // The block is attempted after, and its failure does not fail the
            // report: the report is the half that reaches a human.
            if (ok && alsoBlock && target.authorId.isNotBlank()) {
                runCatching { repo.blockAccount(userId, target.authorId, target.authorKind) }
            }
            _sending.value = false
            _sent.value = ok
            _failed.value = !ok
        }
    }

    fun unblock(otherUid: String) {
        scope.launch { runCatching { repo.unblockAccount(userId, otherUid) } }
    }
}
