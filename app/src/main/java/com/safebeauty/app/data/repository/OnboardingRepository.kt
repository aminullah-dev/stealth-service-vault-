package com.safebeauty.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean("seen", false)

    fun markOnboardingSeen() {
        prefs.edit().putBoolean("seen", true).apply()
    }
}
