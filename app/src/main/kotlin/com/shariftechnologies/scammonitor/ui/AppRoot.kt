package com.shariftechnologies.scammonitor.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication
import com.shariftechnologies.scammonitor.ui.about.AboutScreen
import com.shariftechnologies.scammonitor.ui.common.AdGate
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.common.LocalAdGate
import com.shariftechnologies.scammonitor.ui.common.ClayIconButton
import com.shariftechnologies.scammonitor.ui.common.GlassNavBar
import com.shariftechnologies.scammonitor.ui.common.NavSpec
import com.shariftechnologies.scammonitor.ui.dashboard.DashboardScreen
import com.shariftechnologies.scammonitor.ui.flagged.FlaggedDetailScreen
import com.shariftechnologies.scammonitor.ui.flagged.FlaggedListScreen
import com.shariftechnologies.scammonitor.ui.log.SecurityLogScreen
import com.shariftechnologies.scammonitor.ui.onboarding.OnboardingScreen
import com.shariftechnologies.scammonitor.ui.security.SecurityScreen
import com.shariftechnologies.scammonitor.ui.settings.SettingsScreen
import com.shariftechnologies.scammonitor.ui.theme.AuroraBackground
import com.shariftechnologies.scammonitor.ui.theme.LocalGlass

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FLAGGED = "flagged"
    const val FLAGGED_DETAIL = "flagged/{eventId}"
    const val LOG = "log"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun flaggedDetail(eventId: String) = "flagged/$eventId"
}

private val topLevel = listOf(
    NavSpec(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    NavSpec(Routes.FLAGGED, "Flagged", Icons.Filled.Warning),
    NavSpec(Routes.LOG, "Log", Icons.AutoMirrored.Filled.List),
    NavSpec(Routes.SECURITY, "Security", Icons.Filled.Lock),
    NavSpec(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
private fun GlassTopBar(title: String, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ClayIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = LocalGlass.current.ink)
    }
}

@Composable
fun AppRoot(pendingEventId: String?, onPendingEventConsumed: () -> Unit, openSecurity: Boolean, onOpenSecurityConsumed: () -> Unit, openMonitoring: Boolean, onOpenMonitoringConsumed: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as ScamMonitorApplication).container.settings
    val onboardingDone by settings.onboardingCompleted.collectAsStateWithLifecycle()
    val container = (LocalContext.current.applicationContext as ScamMonitorApplication).container
    val supporter by container.supporter.active.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAdGate provides AdGate(container.ads, supporter)) {
    AuroraBackground {
        // The start destination is decided once, after preferences have loaded, and never changes afterwards.
        var start by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(onboardingDone) {
            if (start == null && onboardingDone != null) start = if (onboardingDone == true) Routes.DASHBOARD else Routes.ONBOARDING
        }
        val startRoute = start
        if (startRoute == null) { CenteredLoading(); return@AuroraBackground }

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route

        LaunchedEffect(pendingEventId) {
            if (pendingEventId != null) {
                navController.navigate(Routes.flaggedDetail(pendingEventId))
                onPendingEventConsumed()
            }
        }

        LaunchedEffect(openSecurity) {
            if (openSecurity) {
                navController.navigate(Routes.SECURITY) { launchSingleTop = true }
                onOpenSecurityConsumed()
            }
        }

        fun goTopLevel(target: String) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // The screen-monitoring controls live in Settings; the status notification and the floating shortcut open it.
        LaunchedEffect(openMonitoring) {
            if (openMonitoring) {
                goTopLevel(Routes.SETTINGS)
                onOpenMonitoringConsumed()
            }
        }

        val isTopLevel = topLevel.any { it.route == route }
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = LocalGlass.current.ink,
            topBar = {
                if (route == Routes.FLAGGED_DETAIL) GlassTopBar("Flagged item") { navController.popBackStack() }
                else if (route == Routes.ONBOARDING) GlassTopBar("Set up", null)
                else if (route == Routes.ABOUT) GlassTopBar("About") { navController.popBackStack() }
            },
            bottomBar = {
                if (isTopLevel) GlassNavBar(topLevel, route, ::goTopLevel)
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 } },
                    exitTransition = { fadeOut(tween(140)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(140)) + slideOutVertically(tween(200)) { it / 24 } },
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(onDone = {
                            navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        })
                    }
                    composable(Routes.DASHBOARD) {
                        DashboardScreen(
                            onOpenFlagged = { goTopLevel(Routes.FLAGGED) },
                            onOpenLog = { goTopLevel(Routes.LOG) },
                            onOpenMonitoring = { goTopLevel(Routes.SETTINGS) },
                            onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
                        )
                    }
                    composable(Routes.FLAGGED) { FlaggedListScreen(onOpenItem = { navController.navigate(Routes.flaggedDetail(it)) }) }
                    composable(Routes.FLAGGED_DETAIL, arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                        FlaggedDetailScreen(eventId = entry.arguments?.getString("eventId").orEmpty())
                    }
                    composable(Routes.LOG) { SecurityLogScreen(onOpenFlagged = { navController.navigate(Routes.flaggedDetail(it)) }) }
                    composable(Routes.SECURITY) { SecurityScreen() }
                    composable(Routes.SETTINGS) { SettingsScreen(onOpenAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } }) }
                    composable(Routes.ABOUT) { AboutScreen() }
                }
            }
        }
    }
    }
}
