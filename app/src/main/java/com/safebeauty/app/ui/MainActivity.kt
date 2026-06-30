package com.safebeauty.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
