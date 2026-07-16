package com.safebeauty.app.viewmodel

import androidx.lifecycle.ViewModel
import com.safebeauty.app.data.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: OnboardingRepository
) : ViewModel() {

    val hasSeenOnboarding: Boolean get() = repo.hasSeenOnboarding()

    fun markSeen() = repo.markOnboardingSeen()
}
