// com/example/skywalk/features/splash/presentation/screens/SplashScreen.kt
package com.example.skywalk.features.splash.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skywalk.R

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Create animation values
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Star animation
    val starScale = remember { Animatable(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "starTwinkle")
    val starAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ), label = "starTwinkle"
    )

    // Launch animation sequence
    LaunchedEffect(key1 = true) {
        // First animate the logo scaling and fading in
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = EaseOutQuad
            )
        )

        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = EaseOutQuad
            )
        )

        // Then animate the stars
        starScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 500,
                easing = EaseOutQuad
            )
        )

        // Finally animate the text
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = EaseOutQuad
            )
        )

        // Delay before finishing splash screen
        kotlinx.coroutines.delay(1500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Drawing some stars in background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Top-left star
            Image(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(starAlpha * starScale.value)
                    .scale(starScale.value)
                    .align(Alignment.TopStart)
            )

            // Top-right star
            Image(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(starAlpha * 0.7f * starScale.value)
                    .scale(starScale.value)
                    .align(Alignment.TopEnd)
                    .offset((-20).dp, 40.dp)
            )

            // Bottom-left star
            Image(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(starAlpha * 0.9f * starScale.value)
                    .scale(starScale.value)
                    .align(Alignment.BottomStart)
                    .offset(20.dp, (-30).dp)
            )

            // Bottom-right star
            Image(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(starAlpha * 0.8f * starScale.value)
                    .scale(starScale.value)
                    .align(Alignment.BottomEnd)
                    .offset((-30).dp, (-50).dp)
            )
        }

        // Main content column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo
            Image(
                painter = painterResource(id = R.drawable.skywalk_logo),
                contentDescription = "SkyWalk Logo",
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "SkyWalk",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App tagline
            Text(
                text = "Explore the Universe",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 18.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}