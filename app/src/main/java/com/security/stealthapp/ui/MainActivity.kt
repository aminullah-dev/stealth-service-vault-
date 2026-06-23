package com.security.stealthapp.ui

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.security.stealthapp.navigation.AppNavGraph
import com.security.stealthapp.security.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Lock the app if it returns to the foreground after 5 minutes of inactivity.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) sessionManager.onAppForeground()
            }
        )

        val deepLink = intent?.data?.toString()

        setContent {
            val navController = rememberNavController()
            AppNavGraph(navController = navController, deepLink = deepLink)
        }
    }

    // Reset idle timer on every user touch/key event.
    override fun onUserInteraction() {
        super.onUserInteraction()
        sessionManager.onUserInteraction()
    }
}
