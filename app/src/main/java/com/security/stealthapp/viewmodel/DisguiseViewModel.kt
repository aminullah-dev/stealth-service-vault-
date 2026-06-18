package com.security.stealthapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.security.stealthapp.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DisguiseViewModel @Inject constructor(
    private val repository: VaultRepository
) : ViewModel() {

    companion object {
        private const val SECRET_PIN    = "9988"
        private const val MAX_NOTE_CHARS = 500
    }

    // ── Observable state consumed by DisguiseScreen ───────────────────────────

    var noteContent by mutableStateOf("")
        private set

    /** True for exactly one frame: consumed by LaunchedEffect to trigger navigation. */
    var unlockTriggered by mutableStateOf(false)
        private set

    // ── Actions ───────────────────────────────────────────────────────────────

    fun onNoteContentChanged(text: String) {
        if (text.length <= MAX_NOTE_CHARS) noteContent = text
    }

    /**
     * Called every time the disguise search field changes value.
     * If the field exactly matches the PIN the vault unlocks.
     */
    fun onSearchQueryChanged(query: String) {
        if (query == SECRET_PIN) {
            fireUnlock()
        }
    }

    /** Secondary trigger: long-press anywhere on the top-bar title. */
    fun onTitleLongPressed() {
        fireUnlock()
    }

    /** Reset after the navigation side-effect has been consumed. */
    fun resetUnlockTrigger() {
        unlockTriggered = false
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun fireUnlock() {
        viewModelScope.launch {
            repository.log("VAULT_UNLOCK", "Hidden vault accessed")
        }
        unlockTriggered = true
    }
}
