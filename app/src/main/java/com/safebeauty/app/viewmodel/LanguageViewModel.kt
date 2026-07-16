package com.safebeauty.app.viewmodel

import androidx.lifecycle.ViewModel
import com.safebeauty.app.data.repository.LanguageRepository
import com.safebeauty.app.ui.theme.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val repo: LanguageRepository
) : ViewModel() {

    val language: StateFlow<AppLanguage> = repo.language

    fun setLanguage(lang: AppLanguage) = repo.setLanguage(lang)
}
