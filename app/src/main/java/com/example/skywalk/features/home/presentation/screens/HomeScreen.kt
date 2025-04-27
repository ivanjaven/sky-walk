// HomeScreen.kt
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
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.example.skywalk.features.socialmedia.presentation.screens.SocialMediaScreen
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaViewModel

@Composable
fun HomeScreen() {
    val authViewModel: AuthViewModel = viewModel()
    val socialMediaViewModel: SocialMediaViewModel = viewModel()

    val currentUser by authViewModel.currentUser.collectAsState(initial = null)

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
                    text = "SkyWalk Social",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Social Media Content
            SocialMediaScreen(
                viewModel = socialMediaViewModel,
                currentUser = currentUser
            )
        }
    }
}