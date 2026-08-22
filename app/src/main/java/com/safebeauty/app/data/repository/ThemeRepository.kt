package com.safebeauty.app.data.repository

import android.content.Context
import com.safebeauty.app.ui.theme.AppBrand
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which colour family the user picked. Mirrors LanguageRepository exactly —
 * plain SharedPreferences read synchronously, so the choice is already known at
 * nav-graph creation and the app never paints the wrong colours first.
 */
@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _brand = MutableStateFlow(
        try {
            AppBrand.valueOf(prefs.getString("brand", AppBrand.ROSE.name)!!)
        } catch (_: Exception) {
            AppBrand.ROSE
        }
    )

    val brand: StateFlow<AppBrand> = _brand.asStateFlow()

    fun setBrand(brand: AppBrand) {
        prefs.edit().putString("brand", brand.name).apply()
        _brand.value = brand
    }
}
