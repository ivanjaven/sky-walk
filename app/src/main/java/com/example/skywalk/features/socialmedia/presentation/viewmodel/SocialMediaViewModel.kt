// com/example/skywalk/features/socialmedia/presentation/viewmodel/SocialMediaViewModel.kt
package com.example.skywalk.features.socialmedia.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skywalk.features.socialmedia.data.repository.SocialMediaRepositoryImpl
import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.example.skywalk.features.socialmedia.domain.usecases.*
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

    // Use cases
    private val getPostsUseCase = GetPostsUseCase(repository)
    private val createPostUseCase = CreatePostUseCase(repository)
    private val likePostUseCase = LikePostUseCase(repository)
    private val unlikePostUseCase = UnlikePostUseCase(repository)
    private val getCommentsUseCase = GetCommentsUseCase(repository)
    private val addCommentUseCase = AddCommentUseCase(repository)

    // UI states
    private val _uiState = MutableStateFlow<SocialMediaUiState>(SocialMediaUiState.Loading)
    val uiState: StateFlow<SocialMediaUiState> = _uiState

    // Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Posts state
    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts

    // New post state
    private val _postContent = MutableStateFlow("")
    val postContent: StateFlow<String> = _postContent

    private val _postImages = MutableStateFlow<List<Uri>>(emptyList())
    val postImages: StateFlow<List<Uri>> = _postImages

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

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

    init {
        loadInitialPosts()
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
                        _posts.value = loadedPosts
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
                        val currentPosts = _posts.value.toMutableList()
                        currentPosts.addAll(loadedPosts)
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
                    Timber.e(error, "Error toggling like")
                }
            } catch (e: Exception) {
                // Revert on exception
                updatePostLikeStatus(postId, isLiked)
                Timber.e(e, "Error toggling like")
            } finally {
                // Clear the operation status
                likeOperationsMap[postId] = false
            }
        }
    }

    private fun updatePostLikeStatus(postId: String, isLiked: Boolean) {
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
    }

    fun loadComments(postId: String) {
        Timber.d("Loading comments for post $postId")
        _selectedPostId.value = postId
        _showCommentsSheet.value = true
        _commentContent.value = "" // Clear previous comment
        _isLoadingComments.value = true
        _commentError.value = null

        viewModelScope.launch {
            try {
                // Show loading state for comments
                _commentsMap.update { currentMap ->
                    val newMap = currentMap.toMutableMap()
                    // If no comments are cached yet, put an empty list to show loading state
                    if (!newMap.containsKey(postId)) {
                        newMap[postId] = emptyList()
                    }
                    newMap
                }

                getCommentsUseCase(postId)
                    .catch { e ->
                        Timber.e(e, "Error in comment flow: ${e.message}")
                        _isLoadingComments.value = false
                        _commentError.value = "Failed to load comments. Please try again."
                    }
                    .collect { loadedComments ->
                        Timber.d("Received ${loadedComments.size} comments for post $postId: $loadedComments")

                        // Store comments in map indexed by postId
                        _commentsMap.update { currentMap ->
                            val newMap = currentMap.toMutableMap()
                            newMap[postId] = loadedComments
                            newMap
                        }

                        _isLoadingComments.value = false
                    }
            } catch (e: Exception) {
                Timber.e(e, "Exception in loadComments: ${e.message}")
                _isLoadingComments.value = false
                _commentError.value = "Failed to load comments. Please try again."
            }
        }
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