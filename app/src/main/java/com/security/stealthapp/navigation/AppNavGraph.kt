package com.security.stealthapp.navigation

import android.net.Uri
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
import com.security.stealthapp.ui.screens.ChatScreen
import com.security.stealthapp.ui.screens.DisguiseScreen
import com.security.stealthapp.ui.screens.HiddenDashboardScreen
import com.security.stealthapp.ui.screens.LoginScreen
import com.security.stealthapp.ui.screens.ProviderDashboardScreen
import com.security.stealthapp.ui.screens.RegisterScreen
import com.security.stealthapp.ui.theme.LocalStrings
import com.security.stealthapp.ui.theme.StringResources
import com.security.stealthapp.ui.theme.layoutDirection
import com.security.stealthapp.viewmodel.LanguageViewModel

// ── Route constants ────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Login     : Screen("login")
    object Register  : Screen("register")
    object Disguise  : Screen("disguise")

    object CustomerDashboard : Screen("dashboard/customer/{userId}") {
        fun build(userId: String) = "dashboard/customer/$userId"
    }
    object ProviderDashboard : Screen("dashboard/provider/{userId}") {
        fun build(userId: String) = "dashboard/provider/$userId"
    }
    object AdminDashboard : Screen("dashboard/admin/{userId}") {
        fun build(userId: String) = "dashboard/admin/$userId"
    }
    object Chat : Screen("chat/{conversationId}/{myUserId}/{myName}/{otherName}") {
        fun build(
            conversationId: String,
            myUserId: String,
            myName: String,
            otherName: String
        ) = "chat/${Uri.encode(conversationId)}/${Uri.encode(myUserId)}/${Uri.encode(myName)}/${Uri.encode(otherName)}"
    }
}

// ── Nav graph ─────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(navController: NavHostController) {

    val langVm: LanguageViewModel = hiltViewModel()
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    val strings = StringResources.forLanguage(currentLanguage)

    CompositionLocalProvider(
        LocalStrings        provides strings,
        LocalLayoutDirection provides currentLanguage.layoutDirection()
    ) {
        val lockAndReturn: () -> Unit = {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Login.route) { inclusive = false }
                launchSingleTop = true
            }
        }

        NavHost(
            navController    = navController,
            startDestination = Screen.Login.route
        ) {

            composable(Screen.Login.route) {
                LoginScreen(
                    onAuthSuccess = { user ->
                        val route = when (user.role) {
                            UserRole.CUSTOMER -> Screen.CustomerDashboard.build(user.uid)
                            UserRole.PROVIDER -> Screen.ProviderDashboard.build(user.uid)
                            UserRole.ADMIN    -> Screen.AdminDashboard.build(user.uid)
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onDecoyMode = {
                        // Clear back stack so back-press from fake notepad can't return to SafeBeauty
                        navController.navigate(Screen.Disguise.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onRegisterTapped = {
                        navController.navigate(Screen.Register.route) { launchSingleTop = true }
                    }
                )
            }

            composable(Screen.Disguise.route) {
                DisguiseScreen()
            }

            composable(Screen.Register.route) {
                RegisterScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route     = Screen.CustomerDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                HiddenDashboardScreen(
                    onLockTriggered = lockAndReturn,
                    onNavigate      = { route -> navController.navigate(route) }
                )
            }

            composable(
                route     = Screen.ProviderDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                ProviderDashboardScreen(
                    onLockTriggered = lockAndReturn,
                    onNavigate      = { route -> navController.navigate(route) }
                )
            }

            composable(
                route     = Screen.AdminDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                AdminDashboardScreen(onLockTriggered = lockAndReturn)
            }

            composable(
                route     = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                    navArgument("myUserId")       { type = NavType.StringType },
                    navArgument("myName")         { type = NavType.StringType },
                    navArgument("otherName")      { type = NavType.StringType }
                )
            ) {
                ChatScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
