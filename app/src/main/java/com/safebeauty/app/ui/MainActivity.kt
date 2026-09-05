package com.safebeauty.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.safebeauty.app.BuildConfig
import com.safebeauty.app.navigation.AppNavGraph
import com.safebeauty.app.navigation.NotificationDeeplink
import com.safebeauty.app.security.SessionManager
import androidx.compose.foundation.layout.Column
import com.safebeauty.app.ui.components.UpdateAvailableBanner
import com.safebeauty.app.ui.components.ForceUpdateDialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safebeauty.app.viewmodel.ThemeViewModel
import com.safebeauty.app.ui.theme.DashboardTheme
import com.safebeauty.app.util.NotificationHelper
import com.safebeauty.app.viewmodel.ForceUpdateViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    private val forceUpdateViewModel: ForceUpdateViewModel by viewModels()

    // Persists across recompositions so AppNavGraph can consume it after login.
    private var pendingDeeplink by mutableStateOf<NotificationDeeplink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_SECURE used to be set here, and is deliberately not any more.
        //
        // It existed for one threat: a phone being inspected, where a live
        // beauty-booking preview in the task switcher or a screenshot in the
        // gallery was the thing that could expose a woman. The owner has since
        // said that threat no longer applies, and the flag is not free — it
        // blocks every screenshot and screen recording app-wide.
        //
        // What that cost, specifically: a customer could not screenshot her own
        // booking to send to a friend. The safety rules already forbade asking
        // her to share anything publicly, so between the two there was no path
        // by which one satisfied customer could show the product to another.
        // Removing this is the only referral mechanism this app has ever had.
        //
        // Note what it does NOT change. The disguise people assume was here was
        // always thinner than it looked: android:label is @string/app_name,
        // which is "SafeBeauty", so the launcher and the app list have always
        // shown the real name and the real icon. Only the applicationId is
        // disguised, and only Settings and the Play URL ever showed it.
        //
        // To put it back: set FLAG_SECURE on the window in release builds only —
        // with it on in debug, nobody developing the app can screenshot the
        // screen they are working on, adb screencap included.

        enableEdgeToEdge()

        // Lock the app if it returns to the foreground after 5 minutes of inactivity.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) sessionManager.onAppForeground()
            }
        )

        forceUpdateViewModel.check(BuildConfig.VERSION_CODE)

        pendingDeeplink = intent.toNotificationDeeplink()

        setContent {
            // Themed at the root so every screen (and any future one) gets the
            // brand colors and the Vazirmatn typography without wrapping itself.
            // Dark/light follows the device; the colour family is the user's own
            // choice and is read synchronously, so the first frame is already
            // painted in the family they picked rather than flashing the default.
            val themeVm: ThemeViewModel = hiltViewModel()
            val brand by themeVm.brand.collectAsStateWithLifecycle()
            DashboardTheme(darkTheme = isSystemInDarkTheme(), brand = brand) {
                val navController = rememberNavController()
                Column {
                // A newer version exists and this one still works. Above the nav
                // graph so it reaches every screen, and inside the theme so it
                // follows the customer's colour family and language. It occupies
                // real height rather than floating: a strip over the top of a
                // booking screen covers the thing she came to use.
                forceUpdateViewModel.updateAvailable?.let { available ->
                    UpdateAvailableBanner(
                        info      = available,
                        onDismiss = { forceUpdateViewModel.dismissUpdateBanner() },
                    )
                }
                AppNavGraph(
                    navController      = navController,
                    deepLink           = intent?.data?.toString(),
                    notifDeeplink      = pendingDeeplink,
                    onDeeplinkConsumed = { pendingDeeplink = null }
                )

                }

                // Overlay a non-dismissible dialog if a forced update is required.
                forceUpdateViewModel.updateInfo?.let { info ->
                    ForceUpdateDialog(
                        minVersionName = info.minVersionName,
                        updateUrl      = info.updateUrl,
                    )
                }
            }
        }
    }

    // Handle notification tap while app is already running (single-top).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeeplink = intent.toNotificationDeeplink()
    }

    // Reset idle timer on every user touch/key event.
    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionManager.onUserInteraction()
    }

    private fun Intent?.toNotificationDeeplink(): NotificationDeeplink? {
        val type = this?.getStringExtra(NotificationHelper.EXTRA_NOTIF_TYPE)
            ?.takeIf { it.isNotBlank() } ?: return null
        val relatedId = this.getStringExtra(NotificationHelper.EXTRA_NOTIF_RELATED_ID) ?: ""
        return NotificationDeeplink(type = type, relatedId = relatedId)
    }
}
