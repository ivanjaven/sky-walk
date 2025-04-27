package com.example.skywalk.features.home.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.example.skywalk.features.chat.presentation.screens.ChatRoomScreen
import com.example.skywalk.features.socialmedia.presentation.screens.SocialMediaScreen
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaViewModel

@Composable
fun HomeScreen() {
    val authViewModel: AuthViewModel = viewModel()
    val socialMediaViewModel: SocialMediaViewModel = viewModel()
    val currentUser by authViewModel.currentUser.collectAsState(initial = null)
    val navController = rememberNavController()

    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            // App Bar (without notification icon)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "SkyWalk",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Set up navigation with NavHost
            NavHost(
                navController = navController,
                startDestination = "social_feed"
            ) {
                composable("social_feed") {
                    // Social Media Content
                    SocialMediaScreen(
                        viewModel = socialMediaViewModel,
                        currentUser = currentUser,
                        onNavigateToChat = { chatRoomId, otherUserId ->
                            // Navigate to chat room with parameters
                            navController.navigate("chat_room/$chatRoomId/$otherUserId")
                        }
                    )
                }

                // Add a route for the chat room
                composable(
                    route = "chat_room/{chatRoomId}/{otherUserId}"
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
    }
}