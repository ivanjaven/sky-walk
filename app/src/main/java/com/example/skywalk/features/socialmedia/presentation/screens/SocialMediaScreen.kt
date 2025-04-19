// com/example/skywalk/features/socialmedia/presentation/screens/SocialMediaScreen.kt
package com.example.skywalk.features.socialmedia.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.skywalk.R
import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.socialmedia.presentation.components.*
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaUiState
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaScreen(
    viewModel: SocialMediaViewModel,
    currentUser: User?
) {
    val uiState by viewModel.uiState.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val postContent by viewModel.postContent.collectAsState()
    val postImages by viewModel.postImages.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val selectedPostId by viewModel.selectedPostId.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentContent by viewModel.commentContent.collectAsState()
    val showCommentsSheet by viewModel.showCommentsSheet.collectAsState()
    val selectedImageUrl by viewModel.selectedImageUrl.collectAsState()
    val showNoMorePostsMessage by viewModel.showNoMorePostsMessage.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState()

    // Load more posts when scrolled to the bottom
    val shouldLoadMore = remember {
        derivedStateOf {
            val visibleItemCount = listState.layoutInfo.visibleItemsInfo.size
            val totalItemCount = listState.layoutInfo.totalItemsCount
            val firstVisibleItemIndex = listState.firstVisibleItemIndex

            // Load more when approaching the end
            firstVisibleItemIndex + visibleItemCount >= totalItemCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && posts.isNotEmpty()) {
            viewModel.loadMorePosts()
        }
    }

    // Comments bottom sheet
    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideCommentsSheet() },
            sheetState = sheetState
        ) {
            CommentBottomSheet(
                comments = comments,
                commentContent = commentContent,
                onCommentContentChange = viewModel::setCommentContent,
                onSendComment = {
                    viewModel.addComment()
                },
                formatTimestamp = viewModel::formatTimestamp,
                currentUserPhotoUrl = currentUser?.photoUrl,
                onDismiss = { viewModel.hideCommentsSheet() }
            )
        }
    }

    // Fullscreen image viewer
    if (selectedImageUrl != null) {
        FullscreenImageViewer(
            imageUrl = selectedImageUrl!!,
            onDismiss = { viewModel.setSelectedImage(null) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            is SocialMediaUiState.Loading -> {
                if (posts.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            is SocialMediaUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (uiState as SocialMediaUiState.Error).message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = { viewModel.loadInitialPosts() }) {
                        Text("Retry")
                    }
                }
            }

            is SocialMediaUiState.Success -> {
                SwipeToRefreshLayout(
                    onRefresh = { viewModel.loadInitialPosts() },
                    refreshing = posts.isEmpty() && uiState is SocialMediaUiState.Loading
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp) // Space for bottom navigation
                    ) {
                        // Create post card
                        item {
                            CreatePostCard(
                                content = postContent,
                                onContentChange = viewModel::setPostContent,
                                selectedImages = postImages,
                                onImageSelected = { viewModel.addPostImage(it) },
                                onImageRemoved = { viewModel.removePostImage(it) },
                                onPostClick = viewModel::createPost,
                                isSubmitting = isSubmitting,
                                currentUserPhotoUrl = currentUser?.photoUrl
                            )
                        }

                        // Posts
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                timeAgo = viewModel.formatTimestamp(post.createdAt),
                                onLikeClick = { viewModel.toggleLike(post.id, post.isLikedByCurrentUser) },
                                onCommentClick = {
                                    scope.launch {
                                        viewModel.loadComments(post.id)
                                    }
                                },
                                onImageClick = { imageUrl ->
                                    viewModel.setSelectedImage(imageUrl)
                                },
                                onMoreClick = { /* Handle more options */ }
                            )
                        }

                        // End of feed message
                        if (showNoMorePostsMessage) {
                            item {
                                SpaceEndMessage()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeToRefreshLayout(
    onRefresh: () -> Unit,
    refreshing: Boolean,
    content: @Composable () -> Unit
) {
    // Simple implementation - you can replace with a proper library like accompanist-swiperefresh
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (refreshing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun SpaceEndMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🚀 You've reached the edge of our digital universe!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Like stars in the night sky, more stellar content will appear soon. Check back later for new cosmic discoveries!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            AsyncImageWithZoom(
                imageUrl = imageUrl,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
fun AsyncImageWithZoom(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Image with zooming would go here
        // For simplicity, using a basic implementation
        AsyncImage(
            model = imageUrl,
            contentDescription = "Fullscreen image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}