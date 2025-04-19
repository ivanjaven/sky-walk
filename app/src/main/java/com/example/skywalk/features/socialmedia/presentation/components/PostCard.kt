// com/example/skywalk/features/socialmedia/presentation/components/PostCard.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.google.firebase.auth.FirebaseAuth
import timber.log.Timber

@Composable
fun PostCard(
    post: Post,
    timeAgo: String,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get current user ID to check if the current user has liked
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Debug log for post and comments
    Timber.d("Rendering post ${post.id} with ${post.commentCount} comments and ${post.commentIds.size} commentIds")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Header with user info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User avatar
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(post.userPhotoUrl ?: R.drawable.placeholder_user)
                    .crossfade(true)
                    .placeholder(R.drawable.placeholder_user)
                    .build(),
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Username and location
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                if (post.location != null) {
                    Text(
                        text = post.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // More options
            IconButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_horiz),
                    contentDescription = "More options"
                )
            }
        }

        // Post content
        if (post.content.isNotEmpty()) {
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Post images
        post.imageUrls?.let { imageUrls ->
            if (imageUrls.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (post.content.isEmpty()) 0.dp else 8.dp)
                ) {
                    ImageCarousel(
                        imageUrls = imageUrls,
                        onImageClick = onImageClick
                    )
                }
            }
        }

        // Action buttons - Always show below content/image
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Like button
            IconButton(onClick = onLikeClick) {
                Icon(
                    painter = painterResource(
                        id = if (post.isLikedByCurrentUser) R.drawable.ic_favorite
                        else R.drawable.ic_favorite_border
                    ),
                    contentDescription = "Like",
                    tint = if (post.isLikedByCurrentUser) Color.Red else LocalContentColor.current,
                )
            }

            // Comment button
            IconButton(onClick = onCommentClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_comment),
                    contentDescription = "Comment"
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Like count with "You" text for current user
        if (post.likeCount > 0) {
            val likeText = buildString {
                if (post.isLikedByCurrentUser) {
                    append("Liked by You")
                    if (post.likeCount > 1) {
                        val otherLikes = post.likeCount - 1
                        append(" and $otherLikes ${if (otherLikes == 1) "other" else "others"}")
                    }
                } else if (post.likes.isNotEmpty()) {
                    // Get first 2 usernames to display
                    val usernames = post.likes.values.take(2)
                    append("Liked by ${usernames.joinToString(" and ")}")
                    if (post.likeCount > usernames.size) {
                        append(" and others")
                    }
                } else {
                    append("${post.likeCount} ${if (post.likeCount == 1) "like" else "likes"}")
                }
            }

            Text(
                text = likeText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Preview of comments or timestamp
        if (post.commentCount > 0) {
            Text(
                text = "View all ${post.commentCount} comments",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onCommentClick)
            )
        }

        Text(
            text = timeAgo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
    }
}