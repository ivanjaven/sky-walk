package com.example.skywalk.features.socialmedia.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.chat.data.repository.ChatRepositoryImpl
import com.example.skywalk.features.chat.domain.usecases.GetOrCreateChatRoomUseCase
import com.example.skywalk.features.socialmedia.data.repository.SocialMediaRepositoryImpl
import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.domain.usecases.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SocialMediaViewModel(application: Application) : AndroidViewModel(application) {

    // Repository
    private val repository = SocialMediaRepositoryImpl()
    private val chatRepository = ChatRepositoryImpl()

    // Use cases
    private val getPostsUseCase = GetPostsUseCase(repository)
    private val createPostUseCase = CreatePostUseCase(repository)
    private val likePostUseCase = LikePostUseCase(repository)
    private val unlikePostUseCase = UnlikePostUseCase(repository)
    private val getCommentsUseCase = GetCommentsUseCase(repository)
    private val addCommentUseCase = AddCommentUseCase(repository)
    private val getCommentsBatchUseCase = GetCommentsBatchUseCase(repository)
    private val getOrCreateChatRoomUseCase = GetOrCreateChatRoomUseCase(chatRepository)
    private val deletePostUseCase = DeletePostUseCase(repository) // Add this

    private val _isLoadingMoreComments = MutableStateFlow(false)
    val isLoadingMoreComments: StateFlow<Boolean> = _isLoadingMoreComments
    private val _hasMoreComments = MutableStateFlow(true)
    val hasMoreComments: StateFlow<Boolean> = _hasMoreComments
    private val commentBatchSize = 10

    // Current user ID
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // UI states
    private val _uiState = MutableStateFlow<SocialMediaUiState>(SocialMediaUiState.Loading)
    val uiState: StateFlow<SocialMediaUiState> = _uiState

    // Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Posts state
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    // User posts state
    private val _userPosts = MutableStateFlow<List<Post>>(emptyList())
    val userPosts: StateFlow<List<Post>> = _userPosts

    // Loading states for user posts
    private val _isLoadingUserPosts = MutableStateFlow(false)
    val isLoadingUserPosts: StateFlow<Boolean> = _isLoadingUserPosts

    private val _isRefreshingUserPosts = MutableStateFlow(false)
    val isRefreshingUserPosts: StateFlow<Boolean> = _isRefreshingUserPosts

    // New post state
    private val _postContent = MutableStateFlow("")
    val postContent: StateFlow<String> = _postContent

    private val _postImages = MutableStateFlow<List<Uri>>(emptyList())
    val postImages: StateFlow<List<Uri>> = _postImages

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    // Delete post state
    private val _isDeletingPost = MutableStateFlow(false)
    val isDeletingPost: StateFlow<Boolean> = _isDeletingPost

    private val _deletePostError = MutableStateFlow<String?>(null)
    val deletePostError: StateFlow<String?> = _deletePostError

    // Comments state - Map of postId to comments
    private val _commentsMap = MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val commentsMap: StateFlow<Map<String, List<Comment>>> = _commentsMap

    // Selected post for comments
    private val _selectedPostId = MutableStateFlow<String?>(null)
    val selectedPostId: StateFlow<String?> = _selectedPostId

    // Comment text input
    private val _commentContent = MutableStateFlow("")
    val commentContent: StateFlow<String> = _commentContent

    // Selected image for fullscreen view
    private val _selectedImageUrls = MutableStateFlow<List<String>?>(null)
    val selectedImageUrls: StateFlow<List<String>?> = _selectedImageUrls

    private val _selectedImageIndex = MutableStateFlow(0)
    val selectedImageIndex: StateFlow<Int> = _selectedImageIndex

    // Bottom sheet states
    private val _showCommentsSheet = MutableStateFlow(false)
    val showCommentsSheet: StateFlow<Boolean> = _showCommentsSheet

    // End of feed message
    private val _showNoMorePostsMessage = MutableStateFlow(false)
    val showNoMorePostsMessage: StateFlow<Boolean> = _showNoMorePostsMessage

    // Track if a post like operation is in progress to prevent multiple clicks
    private val likeOperationsMap = mutableMapOf<String, Boolean>()

    // Pagination and loading state
    private var lastPostId: String? = null
    private var isLoading = false
    private var hasMorePosts = true

    // Post successfully created
    private val _postCreationSuccess = MutableStateFlow(false)
    val postCreationSuccess: StateFlow<Boolean> = _postCreationSuccess

    // Loading state for comments
    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments

    // Comment error state
    private val _commentError = MutableStateFlow<String?>(null)
    val commentError: StateFlow<String?> = _commentError

    // Chat navigation data
    data class ChatNavigationData(val chatRoomId: String, val otherUserId: String)
    private val _chatNavigationData = MutableStateFlow<ChatNavigationData?>(null)
    val chatNavigationData: StateFlow<ChatNavigationData?> = _chatNavigationData

    init {
        loadInitialPosts()
    }

    // New function for deleting posts
    fun deletePost(postId: String) {
        viewModelScope.launch {
            _isDeletingPost.value = true
            _deletePostError.value = null

            try {
                val result = deletePostUseCase(postId)

                result.fold(
                    onSuccess = {
                        // Remove the post from both the main feed and user posts lists
                        _posts.update { currentPosts ->
                            currentPosts.filter { it.id != postId }
                        }

                        _userPosts.update { currentUserPosts ->
                            currentUserPosts.filter { it.id != postId }
                        }

                        Timber.d("Post deleted successfully: $postId")
                    },
                    onFailure = { error ->
                        _deletePostError.value = "Failed to delete post: ${error.message}"
                        Timber.e(error, "Failed to delete post: $postId")
                    }
                )
            } catch (e: Exception) {
                _deletePostError.value = "Failed to delete post: ${e.message}"
                Timber.e(e, "Error deleting post: $postId")
            } finally {
                _isDeletingPost.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadInitialPosts()
            _isRefreshing.value = false
        }
    }

    fun clearPostCreationSuccess() {
        _postCreationSuccess.value = false
    }

    fun loadInitialPosts() {
        if (isLoading && !_isRefreshing.value) return

        viewModelScope.launch {
            isLoading = true
            if (!_isRefreshing.value) {
                _uiState.value = SocialMediaUiState.Loading
            }
            _showNoMorePostsMessage.value = false

            try {
                lastPostId = null
                hasMorePosts = true

                getPostsUseCase(POST_PAGE_SIZE)
                    .catch { e ->
                        Timber.e(e, "Error loading posts")
                        _uiState.value = SocialMediaUiState.Error(e.message ?: "Failed to load posts")
                    }
                    .collect { loadedPosts ->
                        // Filter out the current user's posts
                        val filteredPosts = loadedPosts.filter { it.userId != currentUserId }
                        _posts.value = filteredPosts
                        _uiState.value = SocialMediaUiState.Success

                        if (loadedPosts.isNotEmpty()) {
                            lastPostId = loadedPosts.last().id
                        }

                        hasMorePosts = loadedPosts.size >= POST_PAGE_SIZE
                        if (!hasMorePosts) {
                            _showNoMorePostsMessage.value = true
                        }
                        isLoading = false
                    }

            } catch (e: Exception) {
                isLoading = false
                Timber.e(e, "Error loading initial posts")
                _uiState.value = SocialMediaUiState.Error(e.message ?: "Failed to load posts")
            }
        }
    }

    /**
     * Loads posts for a specific user
     */
    fun loadUserPosts(userId: String) {
        if (_isLoadingUserPosts.value) return

        viewModelScope.launch {
            _isLoadingUserPosts.value = true

            try {
                getPostsUseCase(50) // Get a larger number of posts to filter from
                    .catch { e ->
                        Timber.e(e, "Error loading user posts")
                        _isLoadingUserPosts.value = false
                    }
                    .collect { posts ->
                        // Filter to only show this user's posts
                        val filteredPosts = posts.filter { it.userId == userId }
                        _userPosts.value = filteredPosts
                        _isLoadingUserPosts.value = false
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error in loadUserPosts")
                _isLoadingUserPosts.value = false
            }
        }
    }

    /**
     * Refreshes the user's posts
     */
    fun refreshUserPosts(userId: String) {
        if (_isRefreshingUserPosts.value) return

        viewModelScope.launch {
            _isRefreshingUserPosts.value = true

            try {
                // Clear existing posts first
                _userPosts.value = emptyList()

                // Fetch new posts
                getPostsUseCase(50)
                    .catch { e ->
                        Timber.e(e, "Error refreshing user posts")
                        _isRefreshingUserPosts.value = false
                    }
                    .collect { posts ->
                        // Filter to only show this user's posts
                        val filteredPosts = posts.filter { it.userId == userId }
                        _userPosts.value = filteredPosts
                        _isRefreshingUserPosts.value = false
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error in refreshUserPosts")
                _isRefreshingUserPosts.value = false
            }
        }
    }

    fun loadMorePosts() {
        if (isLoading || !hasMorePosts) return

        viewModelScope.launch {
            isLoading = true

            try {
                getPostsUseCase(POST_PAGE_SIZE, lastPostId)
                    .catch { e ->
                        Timber.e(e, "Error loading more posts")
                        isLoading = false
                    }
                    .collect { loadedPosts ->
                        // Filter out the current user's posts
                        val filteredPosts = loadedPosts.filter { it.userId != currentUserId }

                        val currentPosts = _posts.value.toMutableList()
                        currentPosts.addAll(filteredPosts)
                        _posts.value = currentPosts

                        if (loadedPosts.isNotEmpty()) {
                            lastPostId = loadedPosts.last().id
                        }

                        hasMorePosts = loadedPosts.size >= POST_PAGE_SIZE
                        if (!hasMorePosts) {
                            _showNoMorePostsMessage.value = true
                        }
                        isLoading = false
                    }

            } catch (e: Exception) {
                isLoading = false
                Timber.e(e, "Error loading more posts")
            }
        }
    }

    fun setPostContent(content: String) {
        _postContent.value = content
    }

    fun addPostImage(uri: Uri) {
        val currentImages = _postImages.value.toMutableList()
        currentImages.add(uri)
        _postImages.value = currentImages
    }

    fun removePostImage(uri: Uri) {
        val currentImages = _postImages.value.toMutableList()
        currentImages.remove(uri)
        _postImages.value = currentImages
    }

    fun clearPostImages() {
        _postImages.value = emptyList()
    }

    // Function to start a chat with a user
    fun startChatWithUser(userId: String) {
        if (userId == currentUserId) {
            // Cannot chat with yourself
            return
        }

        viewModelScope.launch {
            try {
                val result = getOrCreateChatRoomUseCase(userId)

                result.fold(
                    onSuccess = { chatRoom ->
                        _chatNavigationData.value = ChatNavigationData(
                            chatRoomId = chatRoom.id,
                            otherUserId = userId
                        )
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error creating chat room: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception in startChatWithUser: ${e.message}")
            }
        }
    }

    fun clearChatNavigationData() {
        _chatNavigationData.value = null
    }

    fun createPost() {
        val content = _postContent.value.trim()
        val imageUris = _postImages.value

        if ((content.isEmpty() && imageUris.isEmpty()) || isSubmitting.value) {
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true

            try {
                // Convert Uris to Files
                val imageFiles = mutableListOf<File>()

                for (uri in imageUris) {
                    try {
                        val context = getApplication<Application>()
                        val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "unknown"}.jpg")

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        imageFiles.add(tempFile)
                    } catch (e: Exception) {
                        Timber.e(e, "Error converting URI to file: $uri")
                        // Continue with other images
                    }
                }

                val result = createPostUseCase(content, imageFiles)

                result.fold(
                    onSuccess = { post ->
                        // Clear form
                        _postContent.value = ""
                        _postImages.value = emptyList()

                        // Signal post creation success for UI feedback
                        _postCreationSuccess.value = true

                        // Add the new post to userPosts too
                        val currentUserPosts = _userPosts.value.toMutableList()
                        currentUserPosts.add(0, post) // Add at the top
                        _userPosts.value = currentUserPosts
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error creating post")
                        _uiState.value = SocialMediaUiState.Error("Failed to create post. Please try again.")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error creating post")
                _uiState.value = SocialMediaUiState.Error("Failed to create post. Please try again.")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun toggleLike(postId: String, isLiked: Boolean) {
        // Prevent rapid clicking by checking if an operation is already in progress
        if (likeOperationsMap[postId] == true) {
            return
        }

        viewModelScope.launch {
            try {
                // Mark operation as in progress for this specific post
                likeOperationsMap[postId] = true

                // Optimistically update the UI
                updatePostLikeStatus(postId, !isLiked)

                val result = if (isLiked) {
                    unlikePostUseCase(postId)
                } else {
                    likePostUseCase(postId)
                }

                result.onFailure { error ->
                    // Revert on failure
                    updatePostLikeStatus(postId, isLiked)
                    Timber.e(error, "Error toggling star")
                }
            } catch (e: Exception) {
                // Revert on exception
                updatePostLikeStatus(postId, isLiked)
                Timber.e(e, "Error toggling star")
            } finally {
                // Clear the operation status
                likeOperationsMap[postId] = false
            }
        }
    }

    private fun updatePostLikeStatus(postId: String, isLiked: Boolean) {
        // Update in main posts list
        val currentPosts = _posts.value.toMutableList()
        val postIndex = currentPosts.indexOfFirst { it.id == postId }

        if (postIndex != -1) {
            val post = currentPosts[postIndex]

            // Just update the isLikedByCurrentUser flag and the like count
            // The actual likeUserIds and likeUsernames will be updated from the server
            val updatedPost = post.copy(
                isLikedByCurrentUser = isLiked,
                likeCount = if (isLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
            )
            currentPosts[postIndex] = updatedPost
            _posts.value = currentPosts
        }

        // Also update in user posts list
        val currentUserPosts = _userPosts.value.toMutableList()
        val userPostIndex = currentUserPosts.indexOfFirst { it.id == postId }

        if (userPostIndex != -1) {
            val post = currentUserPosts[userPostIndex]

            val updatedPost = post.copy(
                isLikedByCurrentUser = isLiked,
                likeCount = if (isLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
            )
            currentUserPosts[userPostIndex] = updatedPost
            _userPosts.value = currentUserPosts
        }
    }

    // Add this method to SocialMediaViewModel
    fun loadCommentsBatch(postId: String, refresh: Boolean = false) {
        if (_isLoadingComments.value || _isLoadingMoreComments.value) return

        val currentComments = if (refresh) emptyList() else _commentsMap.value[postId] ?: emptyList()
        val lastCommentId = if (refresh || currentComments.isEmpty()) null else currentComments.lastOrNull()?.id

        Timber.d("Loading comments batch for post $postId, refresh: $refresh, lastCommentId: $lastCommentId")

        if (refresh) {
            _isLoadingComments.value = true
            _hasMoreComments.value = true
        } else {
            _isLoadingMoreComments.value = true
        }

        viewModelScope.launch {
            try {
                val result = getCommentsBatchUseCase(postId, commentBatchSize, lastCommentId)

                result.fold(
                    onSuccess = { newComments ->
                        Timber.d("Loaded ${newComments.size} comments in batch")

                        _commentsMap.update { currentMap ->
                            val newMap = currentMap.toMutableMap()
                            if (refresh) {
                                newMap[postId] = newComments
                            } else {
                                val combinedComments = (currentComments + newComments).distinctBy { it.id }
                                newMap[postId] = combinedComments
                            }
                            newMap
                        }

                        // Check if we have more comments to load
                        _hasMoreComments.value = newComments.size == commentBatchSize

                        if (refresh) _isLoadingComments.value = false
                        else _isLoadingMoreComments.value = false
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error loading comments batch: ${error.message}")
                        _commentError.value = "Failed to load comments. Please try again."
                        if (refresh) _isLoadingComments.value = false
                        else _isLoadingMoreComments.value = false
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception loading comments batch: ${e.message}")
                _commentError.value = "Failed to load comments. Please try again."
                if (refresh) _isLoadingComments.value = false
                else _isLoadingMoreComments.value = false
            }
        }
    }

    fun loadComments(postId: String) {
        Timber.d("Loading comments for post $postId")
        _selectedPostId.value = postId
        _showCommentsSheet.value = true
        _commentContent.value = "" // Clear previous comment
        _commentError.value = null

        // Load first batch with refresh = true
        loadCommentsBatch(postId, true)
    }

    fun hideCommentsSheet() {
        _showCommentsSheet.value = false
        _commentContent.value = "" // Clear comment field when closing
        _commentError.value = null
    }

    fun addComment() {
        val postId = _selectedPostId.value ?: return
        val content = _commentContent.value.trim()

        if (content.isEmpty()) {
            return
        }

        Timber.d("Adding comment to post $postId: $content")

        viewModelScope.launch {
            try {
                // Clear the input first for immediate feedback
                _commentContent.value = ""

                val result = addCommentUseCase(postId, content)

                result.fold(
                    onSuccess = { newComment ->
                        Timber.d("Comment added successfully: ${newComment.id}")
                        // Optimistically update the comment list without waiting for the listener
                        _commentsMap.update { currentMap ->
                            val currentComments = currentMap[postId]?.toMutableList() ?: mutableListOf()
                            currentComments.add(newComment)
                            val newMap = currentMap.toMutableMap()
                            newMap[postId] = currentComments
                            newMap
                        }

                        // Update post comment count
                        updatePostCommentCount(postId, 1)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error adding comment: ${error.message}")
                        _commentError.value = "Failed to add comment. Please try again."
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception in addComment: ${e.message}")
                _commentError.value = "Failed to add comment. Please try again."
            }
        }
    }

    fun setCommentContent(content: String) {
        _commentContent.value = content
    }

    // Method to update the post's comment count locally
    private fun updatePostCommentCount(postId: String, delta: Int) {
        // Update in main posts list
        val currentPosts = _posts.value.toMutableList()
        val postIndex = currentPosts.indexOfFirst { it.id == postId }

        if (postIndex != -1) {
            val post = currentPosts[postIndex]
            val updatedPost = post.copy(
                commentCount = (post.commentCount + delta).coerceAtLeast(0)
            )
            currentPosts[postIndex] = updatedPost
            _posts.value = currentPosts
        }

        // Also update in user posts list
        val currentUserPosts = _userPosts.value.toMutableList()
        val userPostIndex = currentUserPosts.indexOfFirst { it.id == postId }

        if (userPostIndex != -1) {
            val post = currentUserPosts[userPostIndex]
            val updatedPost = post.copy(
                commentCount = (post.commentCount + delta).coerceAtLeast(0)
            )
            currentUserPosts[userPostIndex] = updatedPost
            _userPosts.value = currentUserPosts
        }
    }

    fun setSelectedImages(imageUrls: List<String>, initialIndex: Int) {
        _selectedImageUrls.value = imageUrls
        _selectedImageIndex.value = initialIndex
    }

    fun clearSelectedImages() {
        _selectedImageUrls.value = null
    }

    fun formatTimestamp(date: Date): String {
        val now = Calendar.getInstance().time
        val diffInMillis = now.time - date.time
        val diffInMinutes = diffInMillis / (60 * 1000)
        val diffInHours = diffInMinutes / 60
        val diffInDays = diffInHours / 24

        return when {
            diffInMinutes < 1 -> "Just now"
            diffInMinutes < 60 -> "${diffInMinutes}m ago"
            diffInHours < 24 -> "${diffInHours}h ago"
            diffInDays < 7 -> "${diffInDays}d ago"
            diffInDays < 365 -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }

    companion object {
        private const val POST_PAGE_SIZE = 10
    }
}

sealed class SocialMediaUiState {
    object Loading : SocialMediaUiState()
    object Success : SocialMediaUiState()
    data class Error(val message: String) : SocialMediaUiState()
}