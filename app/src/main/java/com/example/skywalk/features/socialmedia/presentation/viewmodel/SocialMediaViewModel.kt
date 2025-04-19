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
import kotlinx.coroutines.flow.*
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

    // Comments for selected post
    private val _selectedPostId = MutableStateFlow<String?>(null)
    val selectedPostId: StateFlow<String?> = _selectedPostId

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _commentContent = MutableStateFlow("")
    val commentContent: StateFlow<String> = _commentContent

    // Selected image for fullscreen view
    private val _selectedImageUrl = MutableStateFlow<String?>(null)
    val selectedImageUrl: StateFlow<String?> = _selectedImageUrl

    // Bottom sheet states
    private val _showCommentsSheet = MutableStateFlow(false)
    val showCommentsSheet: StateFlow<Boolean> = _showCommentsSheet

    // Track if a post like operation is in progress to prevent multiple clicks
    private val likeOperationInProgress = mutableMapOf<String, Boolean>()

    // Pagination
    private var lastPostId: String? = null
    private var isLoading = false
    private var hasMorePosts = true

    init {
        loadInitialPosts()
    }

    fun loadInitialPosts() {
        viewModelScope.launch {
            isLoading = true
            _uiState.value = SocialMediaUiState.Loading

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

        if (content.isEmpty() && imageUris.isEmpty()) {
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true

            try {
                // Convert Uris to Files
                val imageFiles = imageUris.mapNotNull { uri ->
                    val context = getApplication<Application>()
                    try {
                        val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}_${uri.lastPathSegment}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    } catch (e: Exception) {
                        Timber.e(e, "Error converting URI to file: $uri")
                        null
                    }
                }

                val result = createPostUseCase(content, imageFiles)

                result.fold(
                    onSuccess = { post ->
                        // Add the new post to the list
                        val currentPosts = _posts.value.toMutableList()
                        currentPosts.add(0, post)
                        _posts.value = currentPosts

                        // Clear form
                        _postContent.value = ""
                        _postImages.value = emptyList()
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error creating post")
                        _uiState.value = SocialMediaUiState.Error("Failed to create post: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error creating post")
                _uiState.value = SocialMediaUiState.Error("Failed to create post: ${e.message}")
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun toggleLike(postId: String, isLiked: Boolean) {
        // Check if operation already in progress for this post
        if (likeOperationInProgress[postId] == true) {
            return
        }

        viewModelScope.launch {
            try {
                // Mark operation as in progress
                likeOperationInProgress[postId] = true

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
                // Mark operation as complete
                likeOperationInProgress[postId] = false
            }
        }
    }

    private fun updatePostLikeStatus(postId: String, isLiked: Boolean) {
        val currentPosts = _posts.value.toMutableList()
        val postIndex = currentPosts.indexOfFirst { it.id == postId }

        if (postIndex != -1) {
            val post = currentPosts[postIndex]
            val updatedPost = post.copy(
                isLikedByCurrentUser = isLiked,
                likeCount = if (isLiked) post.likeCount + 1 else (post.likeCount - 1).coerceAtLeast(0)
            )
            currentPosts[postIndex] = updatedPost
            _posts.value = currentPosts
        }
    }

    fun loadComments(postId: String) {
        _selectedPostId.value = postId
        _showCommentsSheet.value = true

        viewModelScope.launch {
            try {
                getCommentsUseCase(postId)
                    .catch { e ->
                        Timber.e(e, "Error loading comments")
                    }
                    .collect { loadedComments ->
                        _comments.value = loadedComments
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error loading comments")
            }
        }
    }

    fun hideCommentsSheet() {
        _showCommentsSheet.value = false
    }

    fun setCommentContent(content: String) {
        _commentContent.value = content
    }

    fun addComment() {
        val postId = _selectedPostId.value ?: return
        val content = _commentContent.value.trim()

        if (content.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                val result = addCommentUseCase(postId, content)

                result.fold(
                    onSuccess = { comment ->
                        // Add comment to the list immediately for better UX
                        val currentComments = _comments.value.toMutableList()
                        currentComments.add(comment)
                        _comments.value = currentComments

                        // Update comment count on the post
                        updatePostCommentCount(postId, 1)

                        // Clear the input
                        _commentContent.value = ""
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error adding comment")
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error adding comment")
            }
        }
    }

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

    fun setSelectedImage(imageUrl: String?) {
        _selectedImageUrl.value = imageUrl
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