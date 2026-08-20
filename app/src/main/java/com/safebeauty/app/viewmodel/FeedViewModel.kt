package com.safebeauty.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.data.firebase.SalonPostDocument
import com.safebeauty.app.data.firebase.StoryDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the social discovery feed (salon posts, newest first). */
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FirestoreRepository
) : ViewModel() {

    val posts: StateFlow<List<SalonPostDocument>> =
        repo.observeFeed()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live 24-hour salon announcements, newest first. */
    val stories: StateFlow<List<StoryDocument>> =
        repo.observeStories()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
