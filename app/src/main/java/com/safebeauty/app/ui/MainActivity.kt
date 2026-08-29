package com.safebeauty.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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

        // Keeps the app out of the recents thumbnail, and out of screenshots.
        //
        // The disguise stopped at the applicationId: com.security.stealthapp is
        // only visible in Settings, while the task switcher rendered a live
        // preview of a beauty-booking app to anyone who pressed the square
        // button. FLAG_SECURE blanks that preview.
        //
        // It also blocks screenshots and screen recording app-wide, which is a
        // real cost — a customer cannot screenshot her booking to send to a
        // friend. That is the trade this app is for: the same picture in the
        // wrong gallery is the thing being protected against.
        // Release only. FLAG_SECURE blanks the recents thumbnail AND blocks
        // every screenshot, adb screencap included — so with it on in debug
        // builds nobody developing the app can see what they changed, and the
        // one screen you most want to look at is the one you cannot capture.
        // The protection matters for the build a customer installs; a debug
        // build never reaches a phone anyone else picks up.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE)
        }

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
                AppNavGraph(
                    navController      = navController,
                    deepLink           = intent?.data?.toString(),
                    notifDeeplink      = pendingDeeplink,
                    onDeeplinkConsumed = { pendingDeeplink = null }
                )

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
