// com/example/skywalk/features/socialmedia/presentation/screens/SocialMediaScreen.kt
package com.example.skywalk.features.socialmedia.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

                    // Loading more indicator
                    if (posts.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FullscreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    // Implementation for fullscreen image viewer
    // You'll need to create this component to handle fullscreen image viewing
}