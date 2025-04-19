// com/example/skywalk/features/socialmedia/presentation/components/ImageCarousel.kt
package com.example.skywalk.features.socialmedia.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageCarousel(
    imageUrls: List<String>,
    onImageClick: (List<String>, Int) -> Unit
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
                    .clickable { onImageClick(imageUrls, 0) },
                contentScale = ContentScale.Crop
            )
        } else {
            // For multiple images, use HorizontalPager for swiping
            val pagerState = rememberPagerState { imageUrls.size }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrls[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = "Post image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onImageClick(imageUrls, page) },
                    contentScale = ContentScale.Crop
                )
            }

            // Image indicators at bottom
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
                                if (pagerState.currentPage == index) Color.White
                                else Color.White.copy(alpha = 0.5f)
                            )
                    )
                }
            }
        }
    }
}