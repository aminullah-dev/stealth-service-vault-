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
        val title = message.notification?.title ?: return
        val body  = message.notification?.body  ?: return
        NotificationHelper.showBookingUpdate(applicationContext, title, body)
    }
}
