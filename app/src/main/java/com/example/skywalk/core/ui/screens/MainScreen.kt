package com.example.skywalk.core.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.skywalk.core.navigation.BottomNavItem
import com.example.skywalk.core.ui.components.BottomNavigationBar
import com.example.skywalk.features.home.presentation.screens.HomeScreen
import com.example.skywalk.features.placeholder.presentation.screens.PlaceholderScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem(
            name = "Home",
            route = "home",
            icon = Icons.Filled.Home
        ),
        BottomNavItem(
            name = "Encyclopedia",
            route = "encyclopedia",
            icon = Icons.Filled.Info  // Changed from Book to Info
        ),
        BottomNavItem(
            name = "AR Sky",
            route = "ar_sky",
            icon = Icons.Filled.Star,
            isMainAction = true
        ),
        BottomNavItem(
            name = "Events",
            route = "events",
            icon = Icons.Filled.DateRange  // Changed from Event to DateRange
        ),
        BottomNavItem(
            name = "Profile",
            route = "profile",
            icon = Icons.Filled.Person
        )
    )

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                items = bottomNavItems,
                navController = navController,
                onItemClick = {
                    navController.navigate(it.route) {
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true

                        // Pop up to the start destination to
                        // avoid building up a large stack of destinations
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        NavigationGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen()
        }
        composable("encyclopedia") {
            PlaceholderScreen(title = "Encyclopedia")
        }
        composable("ar_sky") {
            PlaceholderScreen(title = "AR Sky Explorer")
        }
        composable("events") {
            PlaceholderScreen(title = "Celestial Events")
        }
        composable("profile") {
            PlaceholderScreen(title = "Profile")
        }
    }
}