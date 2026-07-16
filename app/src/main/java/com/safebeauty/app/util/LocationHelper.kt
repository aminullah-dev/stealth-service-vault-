package com.safebeauty.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Thin, dependency-free location helper built on the Android framework
 * LocationManager (no Google Play Services, no billing, no API key). It only
 * ever reads the last known fix — good enough for "how far is this salon" and
 * "sort by nearest" — and degrades gracefully to null when permission is
 * missing or no fix is available. Turn-by-turn directions are delegated to
 * whatever maps app the user has via a standard geo: intent.
 */
object LocationHelper {

    /** True if the user has granted either coarse or fine location. */
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Best available last-known location across the enabled providers, or null
     * if permission is missing / nothing is cached. Never throws.
     */
    fun lastKnownLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers
                .mapNotNull { p -> if (lm.isProviderEnabled(p)) lm.getLastKnownLocation(p) else null }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    /** Straight-line distance in kilometres between two coordinates. */
    fun distanceKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(aLat, aLng, bLat, bLng, out)
        return out[0] / 1000.0
    }

    /**
     * Opens the user's maps app with directions to [lat]/[lng]. Uses a plain
     * geo: URI so it works with Google Maps or any installed maps app — no SDK,
     * no key, no cost. Falls back to a Google Maps https link if no app handles
     * geo:. Returns false only if nothing at all could handle it.
     */
    fun openDirections(context: Context, lat: Double, lng: Double, label: String): Boolean {
        val encoded = Uri.encode(label)
        // Try a native maps app first. On Android 11+ resolveActivity is
        // unreliable under package-visibility rules, so just attempt the launch
        // and fall back to a universal https maps link if nothing handles geo:.
        val geo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lng?q=$lat,$lng($encoded)")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(geo); return true }
        return runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }
}
