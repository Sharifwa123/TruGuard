package com.shariftechnologies.scammonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shariftechnologies.scammonitor.app.ScamMonitorApplication
import com.shariftechnologies.scammonitor.ui.common.CenteredLoading
import com.shariftechnologies.scammonitor.ui.dashboard.DashboardScreen
import com.shariftechnologies.scammonitor.ui.flagged.FlaggedDetailScreen
import com.shariftechnologies.scammonitor.ui.flagged.FlaggedListScreen
import com.shariftechnologies.scammonitor.ui.log.SecurityLogScreen
import com.shariftechnologies.scammonitor.ui.onboarding.OnboardingScreen
import com.shariftechnologies.scammonitor.ui.security.SecurityScreen
import com.shariftechnologies.scammonitor.ui.settings.SettingsScreen

private object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val FLAGGED = "flagged"
    const val FLAGGED_DETAIL = "flagged/{eventId}"
    const val LOG = "log"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    fun flaggedDetail(eventId: String) = "flagged/$eventId"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.DASHBOARD, "Dashboard", Icons.Filled.Home),
    TopLevel(Routes.FLAGGED, "Flagged", Icons.Filled.Warning),
    TopLevel(Routes.LOG, "Log", Icons.AutoMirrored.Filled.List),
    TopLevel(Routes.SECURITY, "Security", Icons.Filled.Lock),
    TopLevel(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(pendingEventId: String?, onPendingEventConsumed: () -> Unit, openSecurity: Boolean, onOpenSecurityConsumed: () -> Unit, openMonitoring: Boolean, onOpenMonitoringConsumed: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as ScamMonitorApplication).container.settings
    val onboardingDone by settings.onboardingCompleted.collectAsStateWithLifecycle()

    // The start destination is decided once, after preferences have loaded, and never changes afterwards.
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(onboardingDone) {
        if (start == null && onboardingDone != null) start = if (onboardingDone == true) Routes.DASHBOARD else Routes.ONBOARDING
    }
    val startRoute = start
    if (startRoute == null) { CenteredLoading(); return }

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
        topBar = {
            if (route == Routes.FLAGGED_DETAIL) {
                TopAppBar(
                    title = { Text("Flagged item") },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                )
            } else if (route == Routes.ONBOARDING) {
                TopAppBar(title = { Text("Set up notification access") })
            }
        },
        bottomBar = {
            if (isTopLevel) NavigationBar(Modifier.height(60.dp)) {
                topLevel.forEach { item ->
                    NavigationBarItem(
                        selected = route == item.route,
                        onClick = { goTopLevel(item.route) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            NavHost(navController = navController, startDestination = startRoute) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onDone = {
                        navController.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onOpenFlagged = { goTopLevel(Routes.FLAGGED) },
                        onOpenLog = { goTopLevel(Routes.LOG) },
                        onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                    )
                }
                composable(Routes.FLAGGED) { FlaggedListScreen(onOpenItem = { navController.navigate(Routes.flaggedDetail(it)) }) }
                composable(Routes.FLAGGED_DETAIL, arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { entry ->
                    FlaggedDetailScreen(eventId = entry.arguments?.getString("eventId").orEmpty())
                }
                composable(Routes.LOG) { SecurityLogScreen(onOpenFlagged = { navController.navigate(Routes.flaggedDetail(it)) }) }
                composable(Routes.SECURITY) { SecurityScreen() }
                composable(Routes.SETTINGS) { SettingsScreen() }
            }
        }
    }
}
