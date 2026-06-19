package com.security.stealthapp.viewmodel

import androidx.lifecycle.ViewModel
import com.security.stealthapp.data.repository.LanguageRepository
import com.security.stealthapp.ui.theme.AppLanguage
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
