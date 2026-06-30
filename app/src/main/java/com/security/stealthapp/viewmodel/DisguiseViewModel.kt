package com.security.stealthapp.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** A single note in the disguise notepad (the cover app shown under duress/snooping). */
data class DisguiseNote(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
)

/**
 * Backs the fake notepad. This is the disguise the app presents to a snooper, so
 * it must behave like a genuine note-taking app: notes are created, edited,
 * deleted, sorted, and PERSISTED (plain prefs — fake notes need no encryption).
 * If it were a static stub, anyone who tapped "New note" or edited a note and
 * came back would see it doesn't really work, blowing the cover.
 *
 * It is deliberately unaware of the vault — PIN auth lives entirely in
 * [AuthViewModel] — so it reads like an ordinary notes ViewModel.
 */
@HiltViewModel
class DisguiseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val MAX_NOTE_CHARS = 5000
        private const val PREFS = "disguise_notes"
        private const val KEY   = "notes_json"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var notes by mutableStateOf<List<DisguiseNote>>(emptyList())
        private set

    // Working text for the note currently open in the editor.
    var noteContent by mutableStateOf("")
        private set

    init { notes = load() }

    fun onNoteContentChanged(text: String) {
        if (text.length <= MAX_NOTE_CHARS) noteContent = text
    }

    /**
     * Seeds a few starter notes the very first time the notepad is opened, so a
     * fresh install doesn't look like a suspiciously empty app. Runs once: after
     * any save the prefs key exists and this becomes a no-op.
     */
    fun seedIfEmpty(defaults: List<Pair<String, String>>) {
        if (prefs.contains(KEY) || notes.isNotEmpty()) return
        var t = System.currentTimeMillis()
        notes = defaults.map { (title, body) ->
            DisguiseNote(id = t, title = title, body = body, updatedAt = t).also { t -= 86_400_000L }
        }
        save()
    }

    /** Creates an empty note and returns its id so the caller can open the editor. */
    fun addNote(title: String): Long {
        val now  = System.currentTimeMillis()
        val note = DisguiseNote(id = now, title = title, body = "", updatedAt = now)
        notes = listOf(note) + notes
        save()
        return note.id
    }

    fun updateNoteBody(id: Long, body: String) {
        if (body.length > MAX_NOTE_CHARS) return
        notes = notes.map {
            if (it.id == id) it.copy(body = body, updatedAt = System.currentTimeMillis()) else it
        }
        save()
    }

    fun deleteNote(id: Long) {
        notes = notes.filterNot { it.id == id }
        save()
    }

    fun sortByDate() {
        notes = notes.sortedByDescending { it.updatedAt }
        save()
    }

    private fun load(): List<DisguiseNote> = runCatching {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DisguiseNote(
                id        = o.getLong("id"),
                title     = o.getString("title"),
                body      = o.getString("body"),
                updatedAt = o.getLong("updatedAt"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("body", n.body)
                put("updatedAt", n.updatedAt)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
