// com/example/skywalk/features/chat/presentation/screens/ChatRoomScreen.kt
package com.example.skywalk.features.chat.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.skywalk.R
import com.example.skywalk.features.chat.presentation.components.DateHeader
import com.example.skywalk.features.chat.presentation.components.FullScreenImageViewer
import com.example.skywalk.features.chat.presentation.components.MessageItem
import com.example.skywalk.features.chat.presentation.viewmodel.ChatRoomUiState
import com.example.skywalk.features.chat.presentation.viewmodel.ChatRoomViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatRoomId: String,
    otherUserId: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val otherUser by viewModel.otherUser.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val selectedImage by viewModel.selectedImage.collectAsState()
    val isSending by viewModel.isSending.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Track if we need to scroll to bottom
    var shouldScrollToBottom by remember { mutableStateOf(false) }

    // For full-screen image viewer
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    // Initialize the chat room
    LaunchedEffect(chatRoomId, otherUserId) {
        viewModel.setChatRoom(chatRoomId, otherUserId)
        shouldScrollToBottom = true
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && shouldScrollToBottom) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
                shouldScrollToBottom = false
            }
        }
    }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setSelectedImage(uri)
            shouldScrollToBottom = true
        }
    }

    // Full-screen image viewer
    fullScreenImageUrl?.let { imageUrl ->
        FullScreenImageViewer(
            imageUrl = imageUrl,
            onDismiss = { fullScreenImageUrl = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(otherUser?.photoUrl ?: R.drawable.placeholder_user)
                                .crossfade(true)
                                .placeholder(R.drawable.placeholder_user)
                                .build(),
                            contentDescription = "Profile picture",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(otherUser?.displayName ?: "Chat")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages
            when (uiState) {
                is ChatRoomUiState.Loading -> {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Show existing messages while loading more
                        MessagesList(
                            messages = messages,
                            viewModel = viewModel,
                            listState = listState,
                            onImageClick = { url -> fullScreenImageUrl = url },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ChatRoomUiState.Success -> {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth() // Ensure box takes full width
                                .padding(16.dp),
                            contentAlignment = Alignment.Center // Center content both horizontally and vertically
                        ) {
                            Text(
                                text = "No messages yet.\nSay hello to start a conversation!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center // Center the text within the Text composable
                            )
                        }
                    } else {
                        MessagesList(
                            messages = messages,
                            viewModel = viewModel,
                            listState = listState,
                            onImageClick = { url -> fullScreenImageUrl = url },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is ChatRoomUiState.Error -> {
                    // Show messages if available and error overlay
                    if (messages.isNotEmpty()) {
                        MessagesList(
                            messages = messages,
                            viewModel = viewModel,
                            listState = listState,
                            onImageClick = { url -> fullScreenImageUrl = url },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (uiState as ChatRoomUiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Message input
            ChatInputBar(
                text = messageText,
                onTextChanged = viewModel::setMessageText,
                selectedImage = selectedImage,
                onImageSelected = {
                    imagePicker.launch("image/*")
                },
                onImageRemoved = { viewModel.setSelectedImage(null) },
                onSendClick = {
                    viewModel.sendMessage()
                    shouldScrollToBottom = true
                },
                isSending = isSending
            )
        }
    }
}

@Composable
fun MessagesList(
    messages: List<com.example.skywalk.features.chat.domain.models.ChatMessage>,
    viewModel: ChatRoomViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = messages,
            key = { _, message -> message.id } // Use stable IDs for better animation
        ) { index, message ->
            // Date header
            if (viewModel.shouldShowDateHeader(index)) {
                DateHeader(
                    date = viewModel.formatDateHeader(message.timestamp)
                )
            }

            // Message item
            MessageItem(
                message = message,
                isFromCurrentUser = viewModel.isFromCurrentUser(message),
                formattedTime = viewModel.formatTime(message.timestamp),
                onImageClick = onImageClick
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    selectedImage: Uri?,
    onImageSelected: () -> Unit,
    onImageRemoved: () -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Selected image preview
            if (selectedImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    AsyncImage(
                        model = selectedImage,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .height(100.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onImageRemoved,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment button
                IconButton(
                    onClick = onImageSelected
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Text field
                TextField(
                    value = text,
                    onValueChange = onTextChanged,
                    placeholder = { Text("Type a message") },
                    modifier = Modifier
                        .weight(1f)
                        .height(IntrinsicSize.Min),
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                // Send button
                IconButton(
                    onClick = onSendClick,
                    enabled = (text.isNotBlank() || selectedImage != null) && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank() || selectedImage != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}