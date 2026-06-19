package com.security.stealthapp.data.repository

import android.content.Context
import com.security.stealthapp.ui.theme.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("lang_prefs", Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(
        try {
            AppLanguage.valueOf(prefs.getString("language", AppLanguage.ENGLISH.name)!!)
        } catch (_: Exception) {
            AppLanguage.ENGLISH
        }
    )

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", lang.name).apply()
        _language.value = lang
    }
}
