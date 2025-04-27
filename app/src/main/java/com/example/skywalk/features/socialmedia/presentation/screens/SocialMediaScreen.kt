// SocialMediaScreen.kt
package com.example.skywalk.features.socialmedia.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skywalk.features.auth.domain.models.User
import com.example.skywalk.features.socialmedia.presentation.components.*
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaUiState
import com.example.skywalk.features.socialmedia.presentation.viewmodel.SocialMediaViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
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
    val commentsMap by viewModel.commentsMap.collectAsState()
    val commentContent by viewModel.commentContent.collectAsState()
    val showCommentsSheet by viewModel.showCommentsSheet.collectAsState()
    val selectedImageUrls by viewModel.selectedImageUrls.collectAsState()
    val selectedImageIndex by viewModel.selectedImageIndex.collectAsState()
    val showNoMorePostsMessage by viewModel.showNoMorePostsMessage.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val postCreationSuccess by viewModel.postCreationSuccess.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val commentError by viewModel.commentError.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Handle post creation success
    LaunchedEffect(postCreationSuccess) {
        if (postCreationSuccess) {
            // Show success message and trigger refresh
            viewModel.refresh()
            viewModel.clearPostCreationSuccess()
        }
    }

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

    // Fullscreen Comments Dialog
    if (showCommentsSheet && selectedPostId != null) {
        val currentPostComments = commentsMap[selectedPostId] ?: emptyList()

        FullscreenCommentDialog(
            postId = selectedPostId!!,
            comments = currentPostComments,
            commentContent = commentContent,
            onCommentContentChange = viewModel::setCommentContent,
            onSendComment = { viewModel.addComment() },
            formatTimestamp = viewModel::formatTimestamp,
            currentUserPhotoUrl = currentUser?.photoUrl,
            isLoadingComments = isLoadingComments,
            isLoadingMoreComments = viewModel.isLoadingMoreComments.collectAsState().value,
            hasMoreComments = viewModel.hasMoreComments.collectAsState().value,
            commentError = commentError,
            onDismiss = { viewModel.hideCommentsSheet() },
            onLoadMore = {
                if (selectedPostId != null) {
                    viewModel.loadCommentsBatch(selectedPostId!!, false)
                }
            }
        )
    }

    // Fullscreen image viewer
    if (selectedImageUrls != null) {
        FullscreenImageViewer(
            imageUrls = selectedImageUrls!!,
            initialPage = selectedImageIndex,
            onDismiss = { viewModel.clearSelectedImages() }
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
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = { viewModel.refresh() }
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

                        // Posts - filtered to exclude current user's posts
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
                                onImageClick = { imageUrls, initialIndex ->
                                    viewModel.setSelectedImages(imageUrls, initialIndex)
                                },
                                onChatClick = {
                                    // Start a chat with the post author
                                    viewModel.startChatWithUser(post.userId)
                                }
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
fun SpaceEndMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
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