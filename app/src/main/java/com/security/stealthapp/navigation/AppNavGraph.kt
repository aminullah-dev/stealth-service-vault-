package com.security.stealthapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.security.stealthapp.ui.screens.DisguiseScreen
import com.security.stealthapp.ui.screens.HiddenDashboardScreen

sealed class Screen(val route: String) {
    object Disguise  : Screen("disguise")
    object Dashboard : Screen("dashboard")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Disguise.route
    ) {
        composable(Screen.Disguise.route) {
            DisguiseScreen(
                onUnlockTriggered = {
                    navController.navigate(Screen.Dashboard.route) {
                        // Keep Disguise in back-stack so pressing Back returns to it.
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            HiddenDashboardScreen(
                onLockTriggered = {
                    navController.navigate(Screen.Disguise.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
