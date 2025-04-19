// com/example/skywalk/features/socialmedia/presentation/components/CommentItem.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.socialmedia.domain.models.Comment
import timber.log.Timber

@Composable
fun CommentItem(
    comment: Comment,
    timeAgo: String,
    modifier: Modifier = Modifier
) {
    Timber.d("Rendering comment: ${comment.id} by ${comment.userName} - content: ${comment.content}")

    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // User avatar
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(comment.userPhotoUrl ?: R.drawable.placeholder_user)
                .crossfade(true)
                .placeholder(R.drawable.placeholder_user)
                .error(R.drawable.placeholder_user)
                .build(),
            contentDescription = "Profile picture",
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Comment content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.userName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Comment timestamp
            Text(
                text = timeAgo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}