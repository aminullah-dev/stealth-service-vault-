package com.safebeauty.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_BOOKINGS = "channel_bookings"

    // Intent extras — read by MainActivity to decide where to navigate after login.
    const val EXTRA_NOTIF_TYPE       = "notif_type"
    const val EXTRA_NOTIF_RELATED_ID = "notif_related_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BOOKINGS,
                "Booking Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                enableLights(true)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Shows a push notification. If [type] and [relatedId] are provided a
     * [PendingIntent] is attached so that tapping the notification opens
     * MainActivity and navigates directly to the Notification Center.
     */
    fun showBookingUpdate(
        context: Context,
        title: String,
        body: String,
        type: String = "",
        relatedId: String = ""
    ) {
        val mainActivityClass = Class.forName("${context.packageName}.ui.MainActivity")
        val tapIntent = Intent(context, mainActivityClass).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (type.isNotBlank())      putExtra(EXTRA_NOTIF_TYPE, type)
            if (relatedId.isNotBlank()) putExtra(EXTRA_NOTIF_RELATED_ID, relatedId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.hashCode(),            // unique request code per notification type
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BOOKINGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS not yet granted */ }
    }
}
