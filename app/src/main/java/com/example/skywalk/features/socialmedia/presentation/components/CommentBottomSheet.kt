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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.socialmedia.domain.models.Comment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date // Import Date

// Dummy CommentItem for compilation - Replace with your actual implementation
@Composable
fun CommentItem(comment: Comment, timeAgo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(comment.userPhotoUrl ?: R.drawable.placeholder_user) // Use comment's user photo
                .crossfade(true)
                .placeholder(R.drawable.placeholder_user)
                .error(R.drawable.placeholder_user)
                .build(),
            contentDescription = "Commenter profile picture",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { // Align text and time
                Text(
                    text = comment.userName, // Use comment's user name
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1 // Prevent wrapping
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "• $timeAgo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1 // Prevent wrapping
                )
            }
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


// Dummy Comment data class for compilation - Replace with your actual model
// data class Comment(
//     val id: String,
//     val content: String,
//     val createdAt: Date,
//     val userName: String, // Added for CommentItem
//     val userPhotoUrl: String? = null // Added for CommentItem
// )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    postId: String, // Unused in this composable directly, but likely needed for viewmodel interaction
    comments: List<Comment>,
    commentContent: String,
    onCommentContentChange: (String) -> Unit,
    onSendComment: () -> Unit,
    formatTimestamp: (java.util.Date) -> String,
    currentUserPhotoUrl: String? = null,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    // Used to expand input field when user starts typing
    var isCommentFieldExpanded by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    // When comments change, scroll to bottom
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            // Use a slightly longer delay or ensure it runs *after* composition/measure
            delay(150) // Small delay to allow list to potentially update
            // Scroll to the last item index
            lazyListState.animateScrollToItem(comments.size - 1)
        }
    }

    // Reset expanded state if comment content becomes empty (e.g., after sending)
    LaunchedEffect(commentContent) {
        if (commentContent.isEmpty()) {
            isCommentFieldExpanded = false
        }
    }


    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Use safeDrawingPadding to handle system bars AND ime
                .safeDrawingPadding() // More robust than just imePadding
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp) // Reduced vertical padding
            ) {
                // Center title
                Text(
                    text = "Comments",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Close button
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        // Make sure R.drawable.ic_close exists
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close"
                    )
                }
            }

            // Horizontal line under header (moved below Box padding)
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // Add horizontal padding to match content
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) // Adjust alpha if needed
            )


            // Comments list
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Takes available space
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp, // Add some top padding
                    bottom = 8.dp
                )
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight(0.5f) // Take up some vertical space
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No comments yet. Be the first to comment!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(
                        items = comments,
                        key = { it.id } // Ensure comment.id is unique and stable
                    ) { comment ->
                        CommentItem(
                            comment = comment,
                            timeAgo = formatTimestamp(comment.createdAt)
                        )
                        Spacer(modifier = Modifier.height(16.dp)) // Increased spacing between comments
                    }
                }
            }

            // Add comment section Divider
            Divider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            // Add comment input area
            Surface( // Wrap input area in a Surface for elevation/different background if needed
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface // Or surfaceVariant, etc.
                // shadowElevation = 2.dp // Optional: Add shadow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically // Align items vertically
                ) {
                    // User avatar
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(currentUserPhotoUrl ?: R.drawable.placeholder_user)
                            .crossfade(true)
                            .placeholder(R.drawable.placeholder_user)
                            .error(R.drawable.placeholder_user) // Add error placeholder
                            .build(),
                        contentDescription = "Your profile picture",
                        modifier = Modifier
                            .size(36.dp) // Slightly larger avatar
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(8.dp)) // Space between avatar and text field

                    // Comment input wrapped in Surface for background/shape
                    Surface(
                        modifier = Modifier.weight(1f), // Take remaining space
                        shape = RoundedCornerShape(24.dp), // Rounded corners for the input background
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Subtle background
                        // border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline) // Optional border
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // Padding inside the input Surface
                            modifier = Modifier.padding(horizontal = 1.dp) // Minimal padding here
                        ) {
                            TextField(
                                value = commentContent,
                                onValueChange = {
                                    onCommentContentChange(it)
                                    // Expand if text becomes non-empty and wasn't already expanded
                                    if (!isCommentFieldExpanded && it.isNotEmpty()) {
                                        isCommentFieldExpanded = true
                                    }
                                },
                                placeholder = { Text("Add a comment...") },
                                modifier = Modifier
                                    .weight(1f) // TextField takes space within the inner Row
                                    .focusRequester(focusRequester)
                                    // Add padding within the TextField itself
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                colors = TextFieldDefaults.colors( // Use .colors() in M3
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, // Hide underline
                                    unfocusedIndicatorColor = Color.Transparent, // Hide underline
                                    disabledIndicatorColor = Color.Transparent, // Hide underline
                                    errorIndicatorColor = Color.Transparent // Hide underline
                                ),
                                maxLines = if (isCommentFieldExpanded) 4 else 1, // Allow more lines when expanded
                                // Now uses the imported classes
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium // Ensure consistent text style
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp)) // Space between text field and send button

                    // Send button
                    IconButton(
                        onClick = {
                            if (commentContent.isNotBlank()) { // Check isNotBlank
                                val currentSize = comments.size // Get size before sending
                                onSendComment()
                                coroutineScope.launch {
                                    // Delay slightly allows state propagation & list update
                                    delay(150)
                                    // Scroll to the position where the new item *will be*
                                    lazyListState.animateScrollToItem(currentSize)
                                }
                            }
                        },
                        enabled = commentContent.isNotBlank() // Use isNotBlank to avoid sending only whitespace
                    ) {
                        Icon(
                            // Make sure R.drawable.ic_send exists
                            painter = painterResource(id = R.drawable.ic_send),
                            contentDescription = "Send comment",
                            tint = if (commentContent.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) // Adjust disabled alpha
                        )
                    }
                }
            }
        }
    }
}