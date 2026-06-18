package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Manages only the notepad UI state — search query, note body text, etc.
 * PIN authentication has moved entirely to [AuthViewModel] so this VM remains
 * unaware of the vault and looks like a genuine note-taking ViewModel.
 */
@HiltViewModel
class DisguiseViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val MAX_NOTE_CHARS = 500
    }

    var noteContent by mutableStateOf("")
        private set

    fun onNoteContentChanged(text: String) {
        if (text.length <= MAX_NOTE_CHARS) noteContent = text
    }
}
