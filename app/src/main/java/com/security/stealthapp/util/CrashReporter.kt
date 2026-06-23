package com.security.stealthapp.util

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.security.stealthapp.BuildConfig

/**
 * Thin, privacy-conscious wrapper around Firebase Crashlytics.
 *
 * Stealth constraints:
 *  - Collection is DISABLED in debug builds so developer testing never ships data.
 *  - We NEVER record PII: no PINs, names, phone numbers, user IDs, salon names,
 *    message contents or anything that could identify the user or reveal that the
 *    device runs a hidden beauty-booking app. Only stack traces and coarse,
 *    non-identifying breadcrumbs (e.g. "firestore:observeNotifications") are sent.
 *
 * Usage:
 *  - Call [init] once from Application.onCreate().
 *  - Replace silent `runCatching { ... }` swallows with
 *    `runCatching { ... }.onFailure { CrashReporter.recordNonFatal(it, "context") }`.
 */
object CrashReporter {

    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    /**
     * Enables crash collection only for release builds. Safe to call multiple times.
     */
    fun init() {
        crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    /**
     * Reports a caught (non-fatal) exception with an optional coarse context tag.
     * The [context] must never contain user data — keep it to a static call-site label.
     */
    fun recordNonFatal(throwable: Throwable, context: String? = null) {
        if (BuildConfig.DEBUG) return
        if (context != null) crashlytics.log(context)
        crashlytics.recordException(throwable)
    }

    /**
     * Leaves a breadcrumb in the crash log to aid debugging the next fatal crash.
     * The [message] must be a static, non-identifying label.
     */
    fun log(message: String) {
        if (BuildConfig.DEBUG) return
        crashlytics.log(message)
    }
}
