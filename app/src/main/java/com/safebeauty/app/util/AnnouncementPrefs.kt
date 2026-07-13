package com.safebeauty.app.util

import android.content.Context

/**
 * Remembers which admin announcement (broadcast) this device has already shown
 * as a popup, so a given announcement only interrupts the user once. Backed by
 * a tiny SharedPreferences entry — no network, no account coupling.
 */
object AnnouncementPrefs {
    private const val PREFS = "safebeauty_prefs"
    private const val KEY_LAST_SEEN = "last_broadcast_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastSeenId(context: Context): String =
        prefs(context).getString(KEY_LAST_SEEN, "") ?: ""

    fun markSeen(context: Context, id: String) {
        prefs(context).edit().putString(KEY_LAST_SEEN, id).apply()
    }
}
