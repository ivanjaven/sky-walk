// com/example/skywalk/features/socialmedia/presentation/components/FullscreenCommentDialog.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.socialmedia.domain.models.Comment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenCommentDialog(
    postId: String,
    comments: List<Comment>,
    commentContent: String,
    onCommentContentChange: (String) -> Unit,
    onSendComment: () -> Unit,
    formatTimestamp: (Date) -> String,
    currentUserPhotoUrl: String? = null,
    isLoadingComments: Boolean = false,
    isLoadingMoreComments: Boolean = false,
    hasMoreComments: Boolean = true,
    commentError: String? = null,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    // Used to expand input field when user starts typing
    var isCommentFieldExpanded by remember { mutableStateOf(false) }

    // Log the comments for debugging
    LaunchedEffect(comments) {
        Timber.d("Comments in dialog: ${comments.size}")
    }

    // When comments change, scroll to bottom if it's a new comment
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty() && !isLoadingMoreComments) {
            delay(100) // Small delay to allow list to update
            lazyListState.animateScrollToItem(comments.size - 1)
        }
    }

    // Detect scrolling to load more comments
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset

                // Check if we're close to the end and should load more
                (lastVisibleItem.index >= layoutInfo.totalItemsCount - 3) &&
                        hasMoreComments &&
                        !isLoadingMoreComments &&
                        !isLoadingComments
            }
        }
            .distinctUntilChanged()
            .collectLatest { shouldLoadMore ->
                if (shouldLoadMore) {
                    Timber.d("Reached near end of comments list, loading more...")
                    onLoadMore()
                }
            }
    }

    // Reset expanded state if comment content becomes empty (e.g., after sending)
    LaunchedEffect(commentContent) {
        if (commentContent.isEmpty()) {
            isCommentFieldExpanded = false
        }
    }

    Dialog(
        onDismissRequest = {
            keyboardController?.hide()
            focusManager.clearFocus()
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding() // Handle system insets
                    .imePadding() // Adjust for keyboard
            ) {
                // Header
                TopAppBar(
                    title = { Text("Comments") },
                    navigationIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDismiss()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Close"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Divider()

                // Error message if any
                if (commentError != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = commentError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Comments list
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (isLoadingComments && comments.isEmpty()) {
                        // Initial loading indicator (full screen)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                            Timber.d("Showing loading indicator")
                        }
                    } else if (comments.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No comments yet. Be the first to comment!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Timber.d("Showing empty state")
                        }
                    } else {
                        // Comments list
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(
                                items = comments,
                                key = { it.id }
                            ) { comment ->
                                CommentItem(
                                    comment = comment,
                                    timeAgo = formatTimestamp(comment.createdAt),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }

                            // Loading indicator at the bottom when loading more
                            if (isLoadingMoreComments) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add comment section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User avatar
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentUserPhotoUrl ?: R.drawable.placeholder_user)
                                .crossfade(true)
                                .placeholder(R.drawable.placeholder_user)
                                .error(R.drawable.placeholder_user)
                                .build(),
                            contentDescription = "Your profile picture",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Comment input - No auto-focus
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            TextField(
                                value = commentContent,
                                onValueChange = {
                                    onCommentContentChange(it)
                                    // Expand if text becomes non-empty
                                    if (!isCommentFieldExpanded && it.isNotEmpty()) {
                                        isCommentFieldExpanded = true
                                    }
                                },
                                placeholder = { Text("Add a comment...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester), // Keep the focus requester but don't auto-focus
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                maxLines = if (isCommentFieldExpanded) 5 else 1,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Send button
                        IconButton(
                            onClick = {
                                if (commentContent.isNotBlank()) {
                                    onSendComment()
                                    coroutineScope.launch {
                                        // Scroll to bottom after sending
                                        delay(100)
                                        if (comments.isNotEmpty()) {
                                            lazyListState.animateScrollToItem(comments.size)
                                        }
                                    }
                                }
                            },
                            enabled = commentContent.isNotBlank()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_send),
                                contentDescription = "Send comment",
                                tint = if (commentContent.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}