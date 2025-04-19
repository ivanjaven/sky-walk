// com/example/skywalk/features/chat/presentation/components/MessageItem.kt
package com.example.skywalk.features.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.chat.domain.models.ChatMessage
import com.example.skywalk.features.chat.domain.models.MessageStatus

@Composable
fun DateHeader(
    date: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun MessageItem(
    message: ChatMessage,
    isFromCurrentUser: Boolean,
    formattedTime: String,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isFromCurrentUser) 60.dp else 20.dp,
                end = if (isFromCurrentUser) 20.dp else 60.dp,
                top = 2.dp,
                bottom = 2.dp
            ),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        // For image messages - display without bubble
        if (message.imageUrl != null && message.content.isEmpty()) {
            Column(
                horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
            ) {
                // Image (no bubble) with loading skeleton
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(message.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Message image",
                    loading = {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImageClick(message.imageUrl) },
                    contentScale = ContentScale.Fit
                )

                // Time under the image
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            // Text messages (with bubble)
            Column(
                horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
            ) {
                Surface(
                    color = if (isFromCurrentUser)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                        bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp
                        )
                    ) {
                        // Text content
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isFromCurrentUser)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )

                        // Image content if there's also text
                        message.imageUrl?.let { imageUrl ->
                            Spacer(modifier = Modifier.height(8.dp))

                            // Image with loading skeleton
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Message image",
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .size(180.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Gray.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = if (isFromCurrentUser)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onImageClick(imageUrl) },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                // Time and status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )

                    if (isFromCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (message.status) {
                                MessageStatus.SENDING -> "Sending..."
                                MessageStatus.SENT -> "Sent"
                                MessageStatus.DELIVERED -> "Delivered"
                                MessageStatus.READ -> "Read"
                                MessageStatus.FAILED -> "Failed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}