package com.safebeauty.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.PostCommentDocument
import com.safebeauty.app.data.firebase.SalonPostDocument
import com.safebeauty.app.data.firebase.StoryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the social discovery feed (salon posts, newest first). */
@HiltViewModel
class FeedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: FirestoreRepository
) : ViewModel() {

    val userId: String = savedStateHandle["userId"] ?: ""

    val posts: StateFlow<List<SalonPostDocument>> =
        repo.observeFeed()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live 24-hour salon announcements, newest first. */
    val stories: StateFlow<List<StoryDocument>> =
        repo.observeStories()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which posts this user has liked — one query for the whole grid. */
    val likedPostIds: StateFlow<Set<String>> =
        repo.observeMyLikes(userId)
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * The post whose comment thread is open, or blank for none.
     *
     * Held here rather than in the composable so the thread keeps streaming
     * across a configuration change, and so only ONE thread is ever listened to
     * — attaching a listener per tile would put a hundred of them on Discover.
     */
    private val openThread = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<PostCommentDocument>> =
        openThread
            .flatMapLatest { postId -> repo.observeComments(postId) }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The signed-in user's own name, used to sign a comment. */
    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName

    init {
        if (userId.isNotBlank()) {
            viewModelScope.launch {
                _myName.value = runCatching { repo.getUserById(userId)?.name ?: "" }.getOrDefault("")
            }
        }
    }

    fun openComments(postId: String) { openThread.value = postId }
    fun closeComments() { openThread.value = "" }

    /**
     * Flip the like on [post].
     *
     * The heart redraws from [likedPostIds], which the snapshot listener updates
     * as soon as the write lands locally — Firestore applies it optimistically
     * offline too, so the tap feels immediate without a separate local flag to
     * keep in sync.
     */
    fun toggleLike(post: SalonPostDocument) {
        if (userId.isBlank()) return
        val liked = post.id in likedPostIds.value
        viewModelScope.launch {
            runCatching { repo.setPostLiked(post.id, post.salonId, userId, !liked) }
        }
    }

    /** True after a comment failed to send; cleared when the next one is tried. */
    private val _commentFailed = MutableStateFlow(false)
    val commentFailed: StateFlow<Boolean> = _commentFailed

    fun postComment(post: SalonPostDocument, text: String) {
        val body = text.trim().take(300)
        if (body.isEmpty() || userId.isBlank()) return
        viewModelScope.launch {
            _commentFailed.value = false
            // The rules check authorName against this user's own document, so an
            // empty name (the fetch in init not having landed yet) is refused
            // rather than merely unsigned. Someone who opens Discover and types
            // straight away would hit exactly that, so make sure of the name
            // before writing instead of racing the initial load.
            if (_myName.value.isBlank()) {
                _myName.value = runCatching { repo.getUserById(userId)?.name ?: "" }.getOrDefault("")
            }
            val ok = runCatching {
                repo.addComment(
                    PostCommentDocument(
                        postId     = post.id,
                        salonId    = post.salonId,
                        userId     = userId,
                        authorName = _myName.value,
                        text       = body,
                        createdAt  = System.currentTimeMillis(),
                    )
                )
            }.isSuccess
            _commentFailed.value = !ok
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch { runCatching { repo.deleteComment(commentId) } }
    }
}
