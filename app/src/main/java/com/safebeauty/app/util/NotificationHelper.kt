package com.safebeauty.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safebeauty.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_BOOKINGS = "channel_bookings"

    // Intent extras — read by MainActivity to decide where to navigate after login.
    const val EXTRA_NOTIF_TYPE       = "notif_type"
    const val EXTRA_NOTIF_RELATED_ID = "notif_related_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BOOKINGS,
                // Neutral, because the channel name is listed in Android's own
                // notification settings and read aloud by some launchers. This
                // app is installed as com.security.stealthapp for a reason, and
                // a channel called "Booking Updates" undoes that in Settings.
                context.getString(com.safebeauty.app.R.string.notif_channel_name),
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
        // Reference MainActivity directly rather than deriving the class name from
        // packageName — at runtime packageName is the applicationId
        // (com.security.stealthapp), which differs from the code package
        // (com.safebeauty.app), so Class.forName threw ClassNotFoundException.
        val tapIntent = Intent(context, MainActivity::class.java).apply {
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

        // What the lock screen is allowed to show.
        //
        // The full text names the salon and the service, in the recipient's own
        // language — "قیچی و رنگ در سالن شقایق". That is right inside the app
        // and wrong on a lock screen anyone standing nearby can read, which is
        // the whole reason this app installs under another name. VISIBILITY_
        // PRIVATE tells Android to show the public version instead while the
        // device is locked; the real one is there the moment she unlocks.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_BOOKINGS)
            .setSmallIcon(com.safebeauty.app.R.drawable.ic_notification)
            .setContentTitle(context.getString(com.safebeauty.app.R.string.notif_channel_name))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_BOOKINGS)
            .setSmallIcon(com.safebeauty.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS not yet granted */ }
    }
}
