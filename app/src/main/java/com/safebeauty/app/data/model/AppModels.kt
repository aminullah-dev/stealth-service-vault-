package com.safebeauty.app.data.model

enum class UserRole { CUSTOMER, PROVIDER, ADMIN }

/** Lightweight auth result passed through the nav graph after PIN verification. */
data class LoggedInUser(
    val uid: String,
    val name: String,
    val role: UserRole,
    // Account status from the server ("APPROVED" | "PENDING" | "SUSPENDED" | "REJECTED").
    // Drives the post-login gate: a non-APPROVED provider is sent to a status screen.
    val status: String = "APPROVED",
    // Set by the admin when status is REJECTED; shown on AccountStatusScreen.
    val rejectionReason: String = ""
)
