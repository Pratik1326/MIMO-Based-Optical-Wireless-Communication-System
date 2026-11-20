package com.example.mimo_optical_receiver.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mimo_optical_receiver.ui.screens.HomeScreen
import com.example.mimo_optical_receiver.ui.screens.ScannerScreen
import com.example.mimo_optical_receiver.ui.screens.SettingsScreen
import com.example.mimo_optical_receiver.ui.screens.TransmitterScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Transmitter : Screen("transmitter", "Send", Icons.Default.Sensors)
    object Scanner : Screen("scanner", "Receive", Icons.Default.CameraAlt)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Transmitter, Screen.Scanner, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToScanner = {
                        navController.navigate(Screen.Scanner.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTransmitter = {
                        navController.navigate(Screen.Transmitter.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Transmitter.route) { TransmitterScreen() }
            composable(Screen.Scanner.route) { ScannerScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
