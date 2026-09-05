package com.safebeauty.app.data.repository

import android.content.Context
import com.safebeauty.app.ui.theme.AppLanguage
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
            val stored = prefs.getString("language", null)
            if (stored != null) AppLanguage.valueOf(stored) else deviceLanguage(context)
        } catch (_: Exception) {
            AppLanguage.DARI
        }
    )

    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", lang.name).apply()
        _language.value = lang
    }

    private companion object {
        /**
         * What to open in before anyone has chosen.
         *
         * Every install opened in English. There is a language picker, on the
         * login screen and in the menu, but a woman in Kabul meets the app for
         * the first time in a language she may not read, on the screen that asks
         * for her phone number and her password — and the control that would fix
         * that is labelled in that same language. It is the first impression and
         * it was wrong for almost everyone the app is for.
         *
         * The phone already knows. Persian and Dari share a language code, and
         * Afghanistan's own Dari is written `fa-AF` or `prs`; Pashto is `ps`.
         * Anything else falls to Dari rather than English, because that is the
         * market, and because a wrong guess costs one tap on a picker that is on
         * the very first screen. A stored choice always wins — this is only ever
         * consulted before there is one.
         */
        fun deviceLanguage(context: Context): AppLanguage {
            val tag = try {
                val locales = context.resources.configuration.locales
                if (locales.isEmpty) "" else locales.get(0).language.lowercase()
            } catch (_: Exception) {
                ""
            }
            return when (tag) {
                "ps", "pus"          -> AppLanguage.PASHTO
                "en"                 -> AppLanguage.ENGLISH
                else                 -> AppLanguage.DARI
            }
        }
    }
}
