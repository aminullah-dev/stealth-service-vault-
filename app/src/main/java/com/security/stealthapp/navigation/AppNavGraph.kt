package com.security.stealthapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.security.stealthapp.data.db.entities.UserRole
import com.security.stealthapp.ui.screens.DisguiseScreen
import com.security.stealthapp.ui.screens.HiddenDashboardScreen
import com.security.stealthapp.ui.screens.ProviderDashboardScreen

// ── Route constants ────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Disguise  : Screen("disguise")

    object CustomerDashboard : Screen("dashboard/customer/{userId}") {
        fun build(userId: String) = "dashboard/customer/$userId"
    }

    object ProviderDashboard : Screen("dashboard/provider/{userId}") {
        fun build(userId: String) = "dashboard/provider/$userId"
    }
}

// ── Nav graph ─────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(navController: NavHostController) {

    // Shared lock action used by both dashboards to return to the disguise screen.
    val lockAndReturn: () -> Unit = {
        navController.navigate(Screen.Disguise.route) {
            popUpTo(Screen.Disguise.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Screen.Disguise.route
    ) {

        // ── Disguise / notepad ────────────────────────────────────────────────
        composable(Screen.Disguise.route) {
            DisguiseScreen(
                onAuthSuccess = { user ->
                    val route = when (user.role) {
                        UserRole.CUSTOMER ->
                            Screen.CustomerDashboard.build(user.id)
                        UserRole.PROVIDER ->
                            Screen.ProviderDashboard.build(user.id)
                    }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // ── Customer: beauty marketplace ──────────────────────────────────────
        composable(
            route     = Screen.CustomerDashboard.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            HiddenDashboardScreen(onLockTriggered = lockAndReturn)
        }

        // ── Provider: booking management ──────────────────────────────────────
        composable(
            route     = Screen.ProviderDashboard.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) {
            ProviderDashboardScreen(onLockTriggered = lockAndReturn)
        }
    }
}
