// com/example/skywalk/features/socialmedia/presentation/components/PostCard.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.background
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

@Composable
fun PostCard(
    post: Post,
    timeAgo: String,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        // Like count
        if (post.likeCount > 0) {
            Text(
                text = "Liked by ${if (post.likeUsernames.isNotEmpty()) post.likeUsernames.joinToString(" and ") else ""} ${if (post.likeUsernames.isNotEmpty() && post.likeCount > post.likeUsernames.size) "and others" else if (post.likeUsernames.isEmpty()) "${post.likeCount} others" else ""}",
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

@Composable
fun ImageCarousel(
    imageUrls: List<String>,
    onImageClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        // If only one image, just show it directly
        if (imageUrls.size == 1) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrls[0])
                    .crossfade(true)
                    .build(),
                contentDescription = "Post image",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onImageClick(imageUrls[0]) },
                contentScale = ContentScale.Crop
            )
        } else {
            // For multiple images, implement a simple manual carousel
            var currentPage by remember { mutableStateOf(0) }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrls[currentPage])
                    .crossfade(true)
                    .build(),
                contentDescription = "Post image",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onImageClick(imageUrls[currentPage]) },
                contentScale = ContentScale.Crop
            )

            // Left/Right navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentPage > 0) {
                    IconButton(
                        onClick = { currentPage-- },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_left),
                            contentDescription = "Previous image",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                if (currentPage < imageUrls.size - 1) {
                    IconButton(
                        onClick = { currentPage++ },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_right),
                            contentDescription = "Next image",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Image indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(imageUrls.size) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (currentPage == index) Color.White
                                else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}