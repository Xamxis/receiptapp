package com.example.receiptapp.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.receiptapp.ui.dashboard.FinanceDashboardScreen
import com.example.receiptapp.ui.dashboard.HealthDashboardScreen
import com.example.receiptapp.ui.scanner.ScannerScreen
import com.example.receiptapp.ui.settings.SettingsScreen

sealed class Destination(val route: String, val label: String) {
    data object Finance : Destination("finance", "Finanzen")
    data object Health : Destination("health", "Gesundheit")
    data object Scan : Destination("scan", "Scannen")
    data object Settings : Destination("settings", "Einstellungen")
}

private val bottomBarDestinations = listOf(Destination.Finance, Destination.Health, Destination.Scan, Destination.Settings)

@Composable
fun ReceiptNavGraph() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                bottomBarDestinations.forEach { dest ->
                    val icon = when (dest) {
                        Destination.Finance -> Icons.Default.Home
                        Destination.Health -> Icons.Default.Favorite
                        Destination.Scan -> Icons.Default.CameraAlt
                        Destination.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = dest.label) },
                        label = { androidx.compose.material3.Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Finance.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Finance.route) { FinanceDashboardScreen() }
            composable(Destination.Health.route) { HealthDashboardScreen() }
            composable(Destination.Scan.route) { ScannerScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
