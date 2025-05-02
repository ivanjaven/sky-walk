package com.example.skywalk.features.auth.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.auth.presentation.viewmodel.AuthViewModel
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.presentation.components.ProfilePostCard
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    socialMediaViewModel: SocialMediaViewModel,
    onSignOut: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState(initial = null)
    val userPosts by socialMediaViewModel.userPosts.collectAsState()
    val isLoading by socialMediaViewModel.isLoadingUserPosts.collectAsState()
    val isRefreshing by socialMediaViewModel.isRefreshingUserPosts.collectAsState()
    val isUpdatingProfile by authViewModel.isUpdatingProfile.collectAsState()
    val profileUpdateError by authViewModel.profileUpdateError.collectAsState()
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showImagePickerDialog by remember { mutableStateOf(false) }
    var showDeletePostDialog by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            authViewModel.updateProfilePicture(it)
        }
    }

    // Load user posts when the screen is shown
    LaunchedEffect(currentUser?.id) {
        currentUser?.id?.let { userId ->
            socialMediaViewModel.loadUserPosts(userId)
        }
    }

    // Handle image picker dialog
    if (showImagePickerDialog) {
        AlertDialog(
            onDismissRequest = { showImagePickerDialog = false },
            title = { Text("Profile Picture") },
            text = { Text("Update your profile picture?") },
            confirmButton = {
                Button(
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                        showImagePickerDialog = false
                    }
                ) {
                    Text("Choose Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Logout confirmation dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = {
                Text(
                    text = "Log Out",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out?",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.signOut()
                        onSignOut()
                        showConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete post confirmation dialog
    showDeletePostDialog?.let { postId ->
        AlertDialog(
            onDismissRequest = { showDeletePostDialog = null },
            title = {
                Text(
                    text = "Delete Post",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this post? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        socialMediaViewModel.deletePost(postId)
                        showDeletePostDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeletePostDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Loading overlay when updating profile picture
    if (isUpdatingProfile) {
        Dialog(onDismissRequest = { /* Cannot dismiss */ }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Updating profile picture...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = { showConfirmationDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Log out"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = {
                currentUser?.id?.let { userId ->
                    socialMediaViewModel.refreshUserPosts(userId)
                }
            },
            modifier = Modifier.padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Profile Header
                item {
                    ProfileHeader(
                        user = currentUser,
                        onProfilePictureClick = { showImagePickerDialog = true }
                    )
                }

                // User Stats Section
                item {
                    UserStatsSection(postCount = userPosts.size)
                }

                // Error message if profile update failed
                if (profileUpdateError != null) {
                    item {
                        Text(
                            text = profileUpdateError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Section Title
                item {
                    Text(
                        text = "My Posts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }

                // Loading indicator
                if (isLoading && userPosts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                // Empty state
                else if (userPosts.isEmpty()) {
                    item {
                        EmptyPostsState()
                    }
                }

                // User Posts
                else {
                    items(userPosts) { post ->
                        ProfilePostCard(
                            post = post,
                            timeAgo = socialMediaViewModel.formatTimestamp(post.createdAt),
                            onLikeClick = { socialMediaViewModel.toggleLike(post.id, post.isLikedByCurrentUser) },
                            onCommentClick = { socialMediaViewModel.loadComments(post.id) },
                            onImageClick = { imageUrls, initialIndex ->
                                socialMediaViewModel.setSelectedImages(imageUrls, initialIndex)
                            },
                            onDeleteClick = { showDeletePostDialog = post.id },
                            isCurrentUserPost = post.userId == currentUser?.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(
    user: User?,
    onProfilePictureClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        // Profile Picture with edit indicator
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
        ) {
            // Profile image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user?.photoUrl ?: R.drawable.placeholder_user)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_user)
                    .error(R.drawable.placeholder_user)
                    .build(),
                contentDescription = "Profile Picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onProfilePictureClick)
            )

            // Edit icon overlay
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onProfilePictureClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit profile picture",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // User name
        Text(
            text = user?.displayName ?: "User",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // User email
        Text(
            text = user?.email ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UserStatsSection(postCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            StatItem(
                value = postCount.toString(),
                label = "Posts",
                icon = Icons.Default.List
            )
        }
    }
}

@Composable
fun StatItem(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyPostsState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No posts yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Your posts will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}