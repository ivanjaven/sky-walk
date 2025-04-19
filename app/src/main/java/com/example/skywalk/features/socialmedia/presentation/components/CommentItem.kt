// com/example/skywalk/features/socialmedia/presentation/components/CommentItem.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.socialmedia.domain.models.Comment

@Composable
fun CommentItem(
    comment: Comment,
    timeAgo: String,
    modifier: Modifier = Modifier,
    onLikeComment: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // User avatar
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(comment.userPhotoUrl ?: R.drawable.placeholder_user)
                .crossfade(true)
                .placeholder(R.drawable.placeholder_user)
                .build(),
            contentDescription = "Profile picture",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Comment content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Comment actions and timestamp
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                if (comment.likeCount > 0) {
                    Text(
                        text = "• ${comment.likeCount} ${if (comment.likeCount == 1) "like" else "likes"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (onLikeComment != null) {
                    Text(
                        text = "• Reply",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        // Like button if needed
        if (onLikeComment != null) {
            var isLiked by remember { mutableStateOf(comment.isLikedByCurrentUser) }

            IconButton(
                onClick = {
                    isLiked = !isLiked
                    onLikeComment(isLiked)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isLiked) R.drawable.ic_favorite
                        else R.drawable.ic_favorite_border
                    ),
                    contentDescription = "Like comment",
                    tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}