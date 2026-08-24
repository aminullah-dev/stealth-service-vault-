package com.safebeauty.app.util

import android.content.Context

/**
 * How many times a one-line gesture hint has been shown, and whether the user
 * has dismissed it.
 *
 * A swipe is invisible. Nothing on a screen says it can be dragged, so a
 * gesture nobody discovers is a gesture that does not exist — which is what
 * happened here: the tab pager shipped and was reported missing.
 *
 * The hint therefore appears on its own and then stops appearing, rather than
 * waiting to be found in a settings screen. Three sightings is enough to teach
 * a gesture; after that it is clutter, so it retires itself. Dismissing it once
 * retires it immediately, because a user who closes a hint has understood it.
 *
 * Backed by the same SharedPreferences file as AnnouncementPrefs — device-local,
 * no account coupling, so it survives a sign-out and does not follow the user
 * to a second phone, which is the right behaviour for a thing about fingers.
 */
object HintPrefs {
    private const val PREFS = "safebeauty_prefs"
    private const val MAX_SHOWS = 3

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun countKey(hint: String) = "hint_shows_$hint"
    private fun doneKey(hint: String)  = "hint_done_$hint"

    fun shouldShow(context: Context, hint: String): Boolean {
        val p = prefs(context)
        if (p.getBoolean(doneKey(hint), false)) return false
        return p.getInt(countKey(hint), 0) < MAX_SHOWS
    }

    fun recordShown(context: Context, hint: String) {
        val p = prefs(context)
        if (p.getBoolean(doneKey(hint), false)) return
        p.edit().putInt(countKey(hint), p.getInt(countKey(hint), 0) + 1).apply()
    }

    /** The user closed it, so they have read it. */
    fun dismiss(context: Context, hint: String) {
        prefs(context).edit().putBoolean(doneKey(hint), true).apply()
    }
}
