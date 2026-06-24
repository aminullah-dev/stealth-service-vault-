package com.security.stealthapp.service

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.security.stealthapp.util.NotificationHelper

class StealthMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Persist locally; AuthViewModel uploads it to Firestore on next login.
        applicationContext
            .getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Prefer the notification payload's text; fall back to data fields so the
        // same function works whether the server sends a "notification" or "data" message.
        val title     = message.notification?.title     ?: message.data["title"]     ?: return
        val body      = message.notification?.body      ?: message.data["body"]      ?: return
        val type      = message.data["type"]      ?: ""
        val relatedId = message.data["relatedId"] ?: ""

        NotificationHelper.showBookingUpdate(
            context   = applicationContext,
            title     = title,
            body      = body,
            type      = type,
            relatedId = relatedId
        )
    }
}
