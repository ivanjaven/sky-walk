package com.example.skywalk.core.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
//import androidx.navigation.findStartDestination
import com.example.skywalk.core.navigation.BottomNavItem
import com.example.skywalk.core.ui.components.BottomNavigationBar
import com.example.skywalk.features.auth.presentation.screens.ProfileScreen
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.example.skywalk.features.chat.presentation.screens.ChatListScreen
import com.example.skywalk.features.chat.presentation.screens.ChatRoomScreen
import com.example.skywalk.features.chat.presentation.viewmodel.ChatListViewModel
import com.example.skywalk.features.encyclopedia.domain.models.CelestialObject
import com.example.skywalk.features.encyclopedia.presentation.screens.EncyclopediaDetailScreen
import com.example.skywalk.features.encyclopedia.presentation.screens.EncyclopediaScreen
import com.example.skywalk.features.encyclopedia.presentation.viewmodel.EncyclopediaViewModel
import com.example.skywalk.features.home.presentation.screens.HomeScreen
import com.example.skywalk.features.placeholder.presentation.screens.PlaceholderScreen

@Composable
fun MainScreen(
    onSignOut: () -> Unit
) {
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
            icon = Icons.Filled.Info
        ),
        BottomNavItem(
            name = "AR Sky",
            route = "ar_sky",
            icon = Icons.Filled.Star,
            isMainAction = true
        ),
        BottomNavItem(
            name = "Chat",
            route = "chat",
            icon = Icons.Filled.Email,
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
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavigationGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
            onSignOut = onSignOut
        )
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit
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
            val viewModel = viewModel<EncyclopediaViewModel>()

            // Ensure data is loaded when entering this screen
            LaunchedEffect(Unit) {
                viewModel.loadInitialDataIfNeeded()
            }

            EncyclopediaScreen(
                viewModel = viewModel,
                onNavigateToDetail = { celestialObject ->
                    // Store the object in saved state
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        "celestialObject",
                        celestialObject
                    )
                    navController.navigate("encyclopedia/detail")
                }
            )
        }

        composable("encyclopedia/detail") {
            val viewModel = viewModel<EncyclopediaViewModel>()
            val celestialObject = navController.previousBackStackEntry?.savedStateHandle?.get<CelestialObject>("celestialObject")

            // Set navigating back flag when leaving this screen
            DisposableEffect(Unit) {
                onDispose {
                    viewModel.setNavigatingBack(true)
                }
            }

            celestialObject?.let {
                EncyclopediaDetailScreen(
                    celestialObject = it,
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            } ?: Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        composable("ar_sky") {
            PlaceholderScreen(title = "AR Sky Explorer")
        }

        composable("profile") {
            val authViewModel = viewModel<AuthViewModel>()
            ProfileScreen(
                viewModel = authViewModel,
                onSignOut = onSignOut
            )
        }

        composable("chat") {
            val chatListViewModel = viewModel<ChatListViewModel>()

            // Use silentRefresh instead of manualRefresh
            LaunchedEffect(navController.currentBackStackEntry) {
                chatListViewModel.silentRefresh() // Changed to silentRefresh
            }

            ChatListScreen(
                onNavigateToChatRoom = { chatRoomId, otherUserId ->
                    navController.navigate("chat/room/$chatRoomId/$otherUserId")
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                viewModel = chatListViewModel
            )
        }

        composable(
            route = "chat/room/{chatRoomId}/{otherUserId}",
            arguments = listOf(
                navArgument("chatRoomId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val chatRoomId = backStackEntry.arguments?.getString("chatRoomId") ?: ""
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: ""

            ChatRoomScreen(
                chatRoomId = chatRoomId,
                otherUserId = otherUserId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}