package com.safebeauty.app.viewmodel

import androidx.lifecycle.ViewModel
import com.safebeauty.app.data.repository.ThemeRepository
import com.safebeauty.app.ui.theme.AppBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the chosen colour family and lets a picker change it. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repo: ThemeRepository
) : ViewModel() {
    val brand: StateFlow<AppBrand> = repo.brand
    fun setBrand(brand: AppBrand) = repo.setBrand(brand)
}
