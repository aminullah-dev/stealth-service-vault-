package com.safebeauty.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Detects rooted devices and emulators.
 *
 * The app is not blocked on detection — that would make it a denial-of-service
 * vector against legitimate power-users. Instead, the LoginScreen shows a
 * one-time dismissible warning so the user is aware of the reduced security
 * posture without preventing access.
 *
 * Detection heuristics cover common root managers (Magisk, SuperSU) and
 * emulator build-prop signatures. No heuristic is 100%; the goal is surfacing
 * meaningful risk, not perfect certainty.
 */
object RootDetector {

    data class Result(
        val isRooted: Boolean,
        val isEmulator: Boolean
    ) {
        val isRisky: Boolean get() = isRooted || isEmulator
    }

    fun check(context: Context): Result = Result(
        isRooted   = checkRoot(context),
        isEmulator = checkEmulator()
    )

    // ── Root detection ────────────────────────────────────────────────────────

    private val ROOT_BINARIES = arrayOf(
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local.prop"
    )

    private val MAGISK_PATHS = arrayOf(
        "/sbin/.magisk",
        "/sbin/.core/mirror",
        "/sbin/.core/img",
        "/data/adb/magisk",
        "/data/adb/modules"
    )

    private val ROOT_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "com.smedialink.oneclickroot",
        "com.zhiqupk.root.global",
        "com.alephzain.framaroot"
    )

    private fun checkRoot(context: Context): Boolean {
        if (hasBuildTestKeys()) return true
        if (ROOT_BINARIES.any { File(it).exists() }) return true
        if (MAGISK_PATHS.any { File(it).exists() }) return true
        if (hasRootPackage(context)) return true
        return false
    }

    private fun hasBuildTestKeys(): Boolean =
        Build.TAGS?.contains("test-keys") == true

    private fun hasRootPackage(context: Context): Boolean {
        val pm = context.packageManager
        return ROOT_PACKAGES.any { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            }.getOrDefault(false)
        }
    }

    // ── Emulator detection ────────────────────────────────────────────────────

    private val EMULATOR_HARDWARE = setOf(
        "goldfish", "ranchu", "vbox86", "nox", "ttVM_Hdragon"
    )
    private val EMULATOR_FINGERPRINTS = setOf(
        "generic", "unknown", "google/sdk_gphone", "Android/sdk_gphone"
    )
    private val EMULATOR_BRANDS = setOf(
        "generic", "generic_x86", "generic_x86_64", "TTVM"
    )
    private val EMULATOR_MODELS = setOf(
        "Emulator",
        "Android SDK built for x86",
        "Android SDK built for x86_64",
        "google_sdk"
    )
    private val EMULATOR_MANUFACTURERS = setOf(
        "Genymotion", "unknown"
    )

    private fun checkEmulator(): Boolean {
        val fp = Build.FINGERPRINT?.lowercase() ?: ""
        if (EMULATOR_FINGERPRINTS.any { fp.contains(it.lowercase()) }) return true
        if (Build.MODEL in EMULATOR_MODELS) return true
        if (EMULATOR_HARDWARE.any { Build.HARDWARE.equals(it, ignoreCase = true) }) return true
        if (EMULATOR_BRANDS.any { Build.BRAND.equals(it, ignoreCase = true) }) return true
        if (EMULATOR_MANUFACTURERS.any { Build.MANUFACTURER.equals(it, ignoreCase = true) }) return true
        return false
    }
}
