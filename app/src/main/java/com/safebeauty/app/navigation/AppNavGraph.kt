package com.safebeauty.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.safebeauty.app.data.model.UserRole
import com.safebeauty.app.ui.screens.AccountStatusScreen
import com.safebeauty.app.ui.screens.AdminDashboardScreen
import com.safebeauty.app.ui.screens.ChatScreen
import com.safebeauty.app.ui.screens.CustomerDashboardScreen
import com.safebeauty.app.ui.screens.ForgotPinScreen
import com.safebeauty.app.ui.screens.KycScreen
import com.safebeauty.app.ui.screens.LoginScreen
import com.safebeauty.app.ui.screens.NotificationCenterScreen
import com.safebeauty.app.ui.screens.OnboardingScreen
import com.safebeauty.app.ui.screens.ProviderDashboardScreen
import com.safebeauty.app.ui.screens.RegisterScreen
import com.safebeauty.app.ui.screens.SetNewPinScreen
import com.safebeauty.app.ui.screens.FeedScreen
import com.safebeauty.app.ui.screens.SalonMapScreen
import com.safebeauty.app.ui.screens.SupportScreen
import com.safebeauty.app.ui.theme.LocalStrings
import com.safebeauty.app.ui.theme.StringResources
import com.safebeauty.app.ui.theme.layoutDirection
import com.safebeauty.app.viewmodel.LanguageViewModel
import com.safebeauty.app.viewmodel.OnboardingViewModel
import com.safebeauty.app.viewmodel.SessionViewModel

// ── Deeplink payload carried from a tapped push notification ──────────────────

data class NotificationDeeplink(
    val type: String,       // e.g. "BOOKING_CONFIRMED", "NEW_BOOKING"
    val relatedId: String   // appointmentId or other related doc id
)

// ── Route constants ────────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login     : Screen("login")
    object Register  : Screen("register")
    object AccountStatus : Screen("accountStatus/{status}?reason={reason}") {
        fun build(status: String, reason: String = "") =
            "accountStatus/${Uri.encode(status.ifBlank { "PENDING" })}?reason=${Uri.encode(reason)}"
    }

    object CustomerDashboard : Screen("dashboard/customer/{userId}") {
        fun build(userId: String) = "dashboard/customer/$userId"
    }
    object ProviderDashboard : Screen("dashboard/provider/{userId}") {
        fun build(userId: String) = "dashboard/provider/$userId"
    }
    object AdminDashboard : Screen("dashboard/admin/{userId}") {
        fun build(userId: String) = "dashboard/admin/$userId"
    }
    object ForgotPin : Screen("forgotPin")
    object SetNewPin : Screen("setNewPin/{oobCode}") {
        fun build(oobCode: String) = "setNewPin/${Uri.encode(oobCode)}"
    }
    object Chat : Screen("chat/{conversationId}/{myUserId}/{myName}/{otherName}?active={active}") {
        fun build(
            conversationId: String,
            myUserId: String,
            myName: String,
            otherName: String,
            active: Boolean = true
        ): String {
            // Names ride in path segments; Uri.encode("") yields an empty segment
            // that matches no destination → navigate() throws. Display names can be
            // blank while the user/salon doc is still loading, so coerce to a
            // non-empty placeholder to keep navigation crash-safe.
            val safeMyName    = myName.ifBlank { "—" }
            val safeOtherName = otherName.ifBlank { "—" }
            return "chat/${Uri.encode(conversationId)}/${Uri.encode(myUserId)}/${Uri.encode(safeMyName)}/${Uri.encode(safeOtherName)}?active=$active"
        }
    }
    object Notifications : Screen("notifications/{userId}") {
        fun build(userId: String) = "notifications/$userId"
    }
    object Kyc : Screen("kyc/{userId}") {
        fun build(userId: String) = "kyc/$userId"
    }
    object Support : Screen("support")
    object Feed : Screen("feed/{userId}?story={story}") {
        // The optional story lets a ring tapped on the dashboard land on that
        // exact announcement instead of dropping the customer at the top of
        // Discover to hunt for it again.
        fun build(userId: String, storyId: String = "") = "feed/$userId?story=$storyId"
    }
    object SalonMap : Screen("salonMap/{userId}") {
        fun build(userId: String) = "salonMap/$userId"
    }
}

// ── Nav graph ─────────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(
    navController: NavHostController,
    deepLink: String? = null,
    notifDeeplink: NotificationDeeplink? = null,
    onDeeplinkConsumed: () -> Unit = {}
) {

    val langVm: LanguageViewModel = hiltViewModel()
    val sessionVm: SessionViewModel = hiltViewModel()
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val currentLanguage by langVm.language.collectAsStateWithLifecycle()
    val shouldLock      by sessionVm.shouldLock.collectAsStateWithLifecycle()
    val strings = StringResources.forLanguage(currentLanguage)
    // Read once at nav-graph creation (plain SharedPreferences, synchronous) —
    // decides whether the one-time intro is the first screen the user sees.
    val startDestination = remember {
        if (onboardingVm.hasSeenOnboarding) Screen.Login.route else Screen.Onboarding.route
    }
    // Who is signed in right now, so a notification tapped at any moment knows
    // whose notification centre to open. Blank means nobody is past sign-in yet.
    var signedInUid by remember { mutableStateOf("") }

    // Auto-lock: when session expires after 5 min of inactivity, return to Login.
    LaunchedEffect(shouldLock) {
        if (shouldLock) {
            sessionVm.onLockHandled()
            signedInUid = ""
            // Drop any notification tap that was never acted on, rather than
            // letting it fire at the next sign-in as if it had just happened.
            onDeeplinkConsumed()
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(
        LocalStrings        provides strings,
        LocalLayoutDirection provides currentLanguage.layoutDirection()
    ) {
        // A notification tap, wherever the user happens to be.
        //
        // This used to live inside LoginScreen's onAuthSuccess, which meant it
        // only ever ran on a fresh sign-in. Tapping a push while the app was
        // already open did nothing at all — onNewIntent set the pending
        // deeplink and nothing consumed it. It then stayed pending, so it fired
        // at the NEXT sign-in instead: after five minutes of inactivity the
        // session auto-locks, and signing back in opened the notification
        // centre out of nowhere, for a tap from an hour earlier.
        //
        // Keyed on the signed-in uid as well as the deeplink, so a tap that
        // arrives before sign-in is honoured the moment sign-in completes.
        LaunchedEffect(notifDeeplink, signedInUid) {
            if (notifDeeplink != null && signedInUid.isNotBlank()) {
                navController.navigate(Screen.Notifications.build(signedInUid)) {
                    launchSingleTop = true
                }
                onDeeplinkConsumed()
            }
        }

        // Navigate to SetNewPin screen when the app is opened via Firebase reset link
        LaunchedEffect(deepLink) {
            if (deepLink != null && deepLink.contains("mode=resetPassword")) {
                val oobCode = Uri.parse(deepLink).getQueryParameter("oobCode").orEmpty()
                if (oobCode.isNotBlank()) {
                    navController.navigate(Screen.SetNewPin.build(oobCode)) {
                        launchSingleTop = true
                    }
                }
            }
        }

        val returnToLogin: () -> Unit = {
            signedInUid = ""
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Login.route) { inclusive = false }
                launchSingleTop = true
            }
        }

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            // slideIntoContainer with Start/End rather than a signed pixel offset:
            // the app runs right-to-left in Dari and Pashto, and an absolute
            // offset would send every forward navigation the wrong way for most
            // of the people using it. Start and End follow the layout direction.
            //
            // Short and shallow on purpose. This is a booking app used one-handed,
            // often on a slow phone; a transition long enough to admire is a
            // transition in the way.
            enterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(240)) +
                    fadeIn(tween(180))
            },
            exitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(240)) +
                    fadeOut(tween(140))
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(240)) +
                    fadeIn(tween(180))
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(240)) +
                    fadeOut(tween(140))
            },
        ) {

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = {
                        onboardingVm.markSeen()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onAuthSuccess = { user ->
                        sessionVm.onLoggedIn()
                        // Gate non-APPROVED accounts (e.g. a provider awaiting admin
                        // approval, or a suspended user) to a status screen instead of
                        // a live dashboard. Customers/admins are always APPROVED.
                        val dashboardRoute = if (user.status != "APPROVED") {
                            Screen.AccountStatus.build(user.status, user.rejectionReason)
                        } else if (user.role == UserRole.PROVIDER && user.kycStatus != "APPROVED") {
                            // A provider can't operate until identity-verified — send
                            // them straight to the KYC screen (submit / under review /
                            // rejected). Customers verify later, gated at booking.
                            Screen.Kyc.build(user.uid)
                        } else when (user.role) {
                            UserRole.CUSTOMER -> Screen.CustomerDashboard.build(user.uid)
                            UserRole.PROVIDER -> Screen.ProviderDashboard.build(user.uid)
                            UserRole.ADMIN    -> Screen.AdminDashboard.build(user.uid)
                        }
                        navController.navigate(dashboardRoute) { launchSingleTop = true }
                        // Hands the notification-tap effect above the uid it needs;
                        // it opens the notification centre if a tap is pending.
                        signedInUid = user.uid
                    },
                    onRegisterTapped  = {
                        navController.navigate(Screen.Register.route) { launchSingleTop = true }
                    },
                    onForgotPinTapped = {
                        navController.navigate(Screen.ForgotPin.route) { launchSingleTop = true }
                    }
                )
            }

            composable(
                route     = Screen.AccountStatus.route,
                arguments = listOf(
                    navArgument("status") { type = NavType.StringType },
                    navArgument("reason") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                AccountStatusScreen(
                    status = backStackEntry.arguments?.getString("status") ?: "PENDING",
                    reason = backStackEntry.arguments?.getString("reason") ?: "",
                    onBack = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
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
                CustomerDashboardScreen(
                    onSignOut = returnToLogin,
                    onNavigate      = { route -> navController.navigate(route) }
                )
            }

            composable(
                route     = Screen.ProviderDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                ProviderDashboardScreen(
                    onSignOut = returnToLogin,
                    onNavigate      = { route -> navController.navigate(route) }
                )
            }

            composable(
                route     = Screen.AdminDashboard.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                AdminDashboardScreen(
                    onSignOut = returnToLogin,
                    onNavigate      = { route -> navController.navigate(route) }
                )
            }

            composable(
                route     = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.StringType },
                    navArgument("myUserId")       { type = NavType.StringType },
                    navArgument("myName")         { type = NavType.StringType },
                    navArgument("otherName")      { type = NavType.StringType },
                    navArgument("active")         { type = NavType.BoolType; defaultValue = true }
                )
            ) {
                ChatScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.ForgotPin.route) {
                ForgotPinScreen(
                    onBack         = { navController.popBackStack() },
                    onLoginTapped  = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Login.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route     = Screen.SetNewPin.route,
                arguments = listOf(navArgument("oobCode") { type = NavType.StringType })
            ) {
                SetNewPinScreen(
                    onSuccess = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route     = Screen.Notifications.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                NotificationCenterScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route     = Screen.Kyc.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                KycScreen(onDone = { navController.popBackStack() })
            }

            composable(Screen.Support.route) {
                SupportScreen(
                    onBack = { navController.popBackStack() },
                    // The account no longer exists — clear the whole back stack so
                    // no signed-in screen is reachable behind the login page.
                    onAccountDeleted = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route     = Screen.Feed.route,
                arguments = listOf(
                    navArgument("userId") { type = NavType.StringType },
                    navArgument("story")  { type = NavType.StringType; defaultValue = "" },
                )
            ) {
                // Discover leads somewhere: tapping through to a salon returns to
                // the dashboard with that salon active, so the detail sheet opens
                // on exactly the salon whose work caught the customer's eye.
                val dashVm: com.safebeauty.app.viewmodel.DashboardViewModel = hiltViewModel()
                FeedScreen(
                    initialStoryId = it.arguments?.getString("story").orEmpty(),
                    onBack      = { navController.popBackStack() },
                    onOpenSalon = { salonId ->
                        dashVm.setActiveSalon(salonId)
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route     = Screen.SalonMap.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) {
                // DashboardViewModel resolves customerId from the route's userId,
                // so the map route must carry it too — without the argument the
                // ViewModel's checkNotNull fails and the screen crashes on open.
                // Using the same ViewModel means the map shows exactly the salons
                // the browse list already loaded: same filters, no second fetch.
                val dashVm: com.safebeauty.app.viewmodel.DashboardViewModel = hiltViewModel()
                val salons by dashVm.displayedSalons.collectAsStateWithLifecycle()
                SalonMapScreen(
                    salons = salons,
                    onBack = { navController.popBackStack() },
                    onBook = { salon ->
                        dashVm.setActiveSalon(salon.id)
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
