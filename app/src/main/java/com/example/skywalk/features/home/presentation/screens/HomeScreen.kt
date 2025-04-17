package com.example.skywalk.features.home.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skywalk.R
import com.example.skywalk.core.ui.theme.SkyWalkTheme
import com.example.skywalk.features.home.presentation.components.*

@Composable
fun HomeScreen() {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            HomeAppBar()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Leave space for bottom nav
            ) {
                item {
                    StoriesSection()
                    Divider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }

                items(samplePosts) { post ->
                    PostItem(
                        post = post,
                        onLikeClick = { /* Handle like click */ },
                        onCommentClick = { /* Handle comment click */ },
                        onShareClick = { /* Handle share click */ },
                        onMoreClick = { /* Handle more click */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeAppBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Social",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        IconButton(
            onClick = { /* Handle notification click */ },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun StoriesSection() {
    val stories = remember {
        listOf(
            UserStory(1, "You", R.drawable.placeholder_user, isMe = true),
            UserStory(2, "Alex", R.drawable.placeholder_user),
            UserStory(3, "Maya", R.drawable.placeholder_user),
            UserStory(4, "Carlos", R.drawable.placeholder_user),
            UserStory(5, "Zoe", R.drawable.placeholder_user),
            UserStory(6, "Jamal", R.drawable.placeholder_user)
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(stories) { story ->
            StoryItem(
                userStory = story,
                onClick = { /* Handle story click */ }
            )
        }
    }
}

// Sample data
private val user1 = User(1, "Kareem Aljabari", R.drawable.placeholder_user)
private val user2 = User(2, "Sara Almasi", R.drawable.placeholder_user)

private val samplePosts = listOf(
    Post(
        id = 1,
        user = user1,
        timeAgo = "2h ago",
        content = "Jupiter's Great Red Spot is actually shrinking! I captured this image last night with my telescope.",
        imageRes = R.drawable.placeholder_jupiter,
        likeCount = 156,
        commentCount = 24
    ),
    Post(
        id = 2,
        user = user2,
        timeAgo = "5h ago",
        content = "Has anyone else observed the Perseid meteor shower this week? I counted 27 meteors in one hour!",
        likeCount = 89,
        commentCount = 34
    ),
    Post(
        id = 3,
        user = user1,
        timeAgo = "1d ago",
        content = "The Andromeda Galaxy is approximately 2.5 million light-years away and contains over a trillion stars. Here's my photo from my backyard.",
        imageRes = R.drawable.placeholder_galaxy,
        likeCount = 215,
        commentCount = 42
    )
)

@Preview
@Composable
fun HomeScreenPreview() {
    SkyWalkTheme {
        HomeScreen()
    }
}