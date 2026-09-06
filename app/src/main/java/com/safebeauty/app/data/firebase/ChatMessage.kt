package com.safebeauty.app.data.firebase

data class ChatMessage(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    /// Denormalised from the conversation id so a salon can QUERY its own
    /// threads — Firestore cannot match a suffix, and "{customerId}_{salonId}"
    /// puts the salon at the end. Empty for a support thread, which belongs to
    /// no salon. firestore.rules requires it to match the conversation.
    val salonId: String = ""
)
