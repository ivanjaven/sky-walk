// Create a new file: UserProfileImage.kt
package com.example.skywalk.features.auth.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun UserProfileImage(
    userId: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    var profileImageUrl by remember { mutableStateOf<String?>(null) }

    // Fetch the latest user profile image URL from Firestore
    LaunchedEffect(userId) {
        withContext(Dispatchers.IO) {
            try {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .get()
                    .await()

                if (userDoc.exists()) {
                    profileImageUrl = userDoc.getString("photoUrl")
                }
            } catch (e: Exception) {
                // Just log error and use placeholder
            }
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(profileImageUrl ?: R.drawable.placeholder_user)
            .crossfade(true)
            .placeholder(R.drawable.placeholder_user)
            .error(R.drawable.placeholder_user)
            .build(),
        contentDescription = "Profile picture",
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}