package com.bonsai.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bonsai.app.ui.chat.ChatScreen
import com.bonsai.app.ui.overview.OverviewScreen
import com.bonsai.app.ui.receipts.ReceiptsScreen
import com.bonsai.app.ui.scanner.ScannerScreen
import com.bonsai.app.ui.settings.SettingsScreen

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Overview : Destination("overview", "Übersicht", Icons.Outlined.Insights)
    data object Receipts : Destination("receipts", "Einkäufe", Icons.AutoMirrored.Filled.ReceiptLong)
    data object Scan : Destination("scan", "Scannen", Icons.Filled.CameraAlt)
    data object Chat : Destination("chat", "Berater", Icons.AutoMirrored.Filled.Chat)
    data object Settings : Destination("settings", "Mehr", Icons.Filled.Settings)
}

private val destinations = listOf(
    Destination.Overview,
    Destination.Receipts,
    Destination.Scan,
    Destination.Chat,
    Destination.Settings
)

@Composable
fun BonsaiNavGraph(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isScanScreen = currentDestination?.route == Destination.Scan.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                destinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    val isScanTab = dest == Destination.Scan

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (isScanTab) {
                                // Der Scan-Button ist die Hauptaktion und daher hervorgehoben
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        dest.icon,
                                        contentDescription = dest.label,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            } else {
                                Icon(dest.icon, contentDescription = dest.label)
                            }
                        },
                        label = {
                            if (!isScanTab) {
                                Text(dest.label, style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Overview.route,
            modifier = Modifier.padding(
                // Der Scanner soll randlos sein, daher oben kein Padding
                top = if (isScanScreen) 0.dp else padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
        ) {
            composable(Destination.Overview.route) {
                OverviewScreen(onOpenAdvisor = { navController.navigate(Destination.Chat.route) })
            }
            composable(Destination.Receipts.route) { ReceiptsScreen() }
            composable(Destination.Scan.route) {
                ScannerScreen(
                    onScanned = {
                        navController.navigate(Destination.Receipts.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Destination.Chat.route) { ChatScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
