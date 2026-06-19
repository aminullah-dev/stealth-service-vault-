package com.security.stealthapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.security.stealthapp.data.model.UserRole
import com.security.stealthapp.ui.screens.AdminDashboardScreen
import com.security.stealthapp.ui.screens.DisguiseScreen
import com.security.stealthapp.ui.screens.HiddenDashboardScreen
import com.security.stealthapp.ui.screens.ProviderDashboardScreen
import com.security.stealthapp.ui.screens.RegisterScreen
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.StringResources
import com.security.stealthapp.ui.theme.layoutDirection
import com.security.stealthapp.viewmodel.LanguageViewModel

// ── Route constants ────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Disguise  : Screen("disguise")
    object Register  : Screen("register")

    object CustomerDashboard : Screen("dashboard/customer/{userId}") {
        fun build(userId: String) = "dashboard/customer/$userId"
    }
    object ProviderDashboard : Screen("dashboard/provider/{userId}") {
        fun build(userId: String) = "dashboard/provider/$userId"
    }
    object AdminDashboard : Screen("dashboard/admin/{userId}") {
        fun build(userId: String) = "dashboard/admin/$userId"
    }
}

// ── Nav graph ─────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(navController: NavHostController) {

    // Language state lives at the activity scope via the singleton repository.
    val langVm: LanguageViewModel = hiltViewModel()
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    val strings = StringResources.forLanguage(currentLanguage)

    CompositionLocalProvider(
        LocalStrings       provides strings,
        LocalLayoutDirection provides currentLanguage.layoutDirection()
    ) {
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

            composable(Screen.Disguise.route) {
                DisguiseScreen(
                    onAuthSuccess = { user ->
                        val route = when (user.role) {
                            UserRole.CUSTOMER -> Screen.CustomerDashboard.build(user.uid)
                            UserRole.PROVIDER -> Screen.ProviderDashboard.build(user.uid)
                            UserRole.ADMIN    -> Screen.AdminDashboard.build(user.uid)
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onRegisterTapped = {
                        navController.navigate(Screen.Register.route) { launchSingleTop = true }
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route     = Screen.CustomerDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                HiddenDashboardScreen(onLockTriggered = lockAndReturn)
            }

            composable(
                route     = Screen.ProviderDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                ProviderDashboardScreen(onLockTriggered = lockAndReturn)
            }

            composable(
                route     = Screen.AdminDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                AdminDashboardScreen(onLockTriggered = lockAndReturn)
            }
        }
    }
}
