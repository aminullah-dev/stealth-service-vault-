package com.safebeauty.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Open the store page for an update, robustly.
 *
 * The two call sites used to do a bare `Intent(ACTION_VIEW, httpsUrl)`. The
 * banner wrapped it in `runCatching {}` and the forced-update dialog did not
 * wrap it at all, so on a device where nothing resolves the https link the
 * button either did nothing (exception swallowed) or crashed the app. Reported
 * dead on a real phone, not only a browser-less emulator.
 *
 * This tries, in order:
 *   1. `market://details?id=<pkg>` — the Play Store app's own scheme, the most
 *      reliable way to land on the listing when Play is installed.
 *   2. the original https link — a browser, or Play Store's https deep link.
 *   3. a visible Toast, so the last resort is a message, never silence.
 *
 * Every launch carries FLAG_ACTIVITY_NEW_TASK because we start it from a
 * non-Activity-typed Context in Compose, and each attempt is guarded so a
 * missing handler falls through to the next instead of throwing.
 *
 * @param onUnavailable localized message shown if no attempt could start —
 *   pass `LocalStrings.current.updateOpenFailed` from the composable.
 */
fun launchUpdate(context: Context, updateUrl: String, onUnavailable: String) {
    if (updateUrl.isBlank()) return

    val marketUri = runCatching {
        Uri.parse(updateUrl).getQueryParameter("id")
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { id ->
        Uri.parse("market://details?id=$id")
    }

    val attempts = buildList {
        marketUri?.let { add(it) }
        add(Uri.parse(updateUrl))
    }

    for (uri in attempts) {
        val started = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (started) return
    }

    Toast.makeText(context, onUnavailable, Toast.LENGTH_LONG).show()
}
