// com/example/skywalk/features/socialmedia/data/remote/FirebaseSocialService.kt
package com.example.skywalk.features.socialmedia.data.remote

import com.example.skywalk.core.firebase.FirebaseConfig
import com.example.skywalk.features.socialmedia.domain.models.Comment
import com.example.skywalk.features.socialmedia.domain.models.Like
import com.example.skywalk.features.socialmedia.domain.models.Post
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.UUID

class FirebaseSocialService {
    private val db = FirebaseConfig.firestore
    private val storage = FirebaseConfig.storage
    private val auth = FirebaseAuth.getInstance()

    private val postsCollection = db.collection("posts")
    private val likesCollection = db.collection("likes")
    private val commentsCollection = db.collection("comments")
    private val usersCollection = db.collection("users")

    // Get current user ID
    private val currentUserId: String?
        get() = auth.currentUser?.uid

    suspend fun getPosts(limit: Int, lastPostId: String? = null): Flow<List<Post>> = callbackFlow {
        var query = postsCollection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())

        // Apply pagination if lastPostId is provided
        if (lastPostId != null) {
            val lastPostDoc = postsCollection.document(lastPostId).get().await()
            if (lastPostDoc.exists()) {
                query = query.startAfter(lastPostDoc)
            }
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "Error fetching posts")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val posts = snapshot.documents.mapNotNull { doc ->
                    try {
                        parsePostDocument(doc)
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing post document")
                        null
                    }
                }
                trySend(posts)
            }
        }

        awaitClose { listener.remove() }
    }

    suspend fun getPostById(postId: String): Flow<Post?> = callbackFlow {
        val listener = postsCollection.document(postId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error fetching post")
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    try {
                        val post = parsePostDocument(snapshot)
                        trySend(post)
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing post")
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun createPost(content: String, imageFiles: List<File>?): Result<Post> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Get user info
            val userDoc = usersCollection.document(userId).get().await()
            val userName = userDoc.getString("displayName") ?: "Anonymous"
            val userPhotoUrl = userDoc.getString("photoUrl")

            // Upload images if present
            val imageUrls = mutableListOf<String>()
            if (!imageFiles.isNullOrEmpty()) {
                for (file in imageFiles) {
                    val imageRef = storage.reference.child("post_images/${UUID.randomUUID()}")
                    imageRef.putFile(file.toUri()).await()
                    val downloadUrl = imageRef.downloadUrl.await().toString()
                    imageUrls.add(downloadUrl)
                }
            }

            // Create post document
            val postId = postsCollection.document().id
            val timestamp = Timestamp.now()

            val postData = hashMapOf(
                "id" to postId,
                "userId" to userId,
                "userName" to userName,
                "userPhotoUrl" to userPhotoUrl,
                "content" to content,
                "imageUrls" to imageUrls,
                "location" to null, // Optional: Add location support later
                "createdAt" to timestamp,
                "likeCount" to 0,
                "commentCount" to 0,
                "likeUsernames" to emptyList<String>()
            )

            // Save post to Firestore
            postsCollection.document(postId).set(postData).await()

            // Create the post object to return
            val post = Post(
                id = postId,
                userId = userId,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                content = content,
                imageUrls = imageUrls,
                location = null,
                createdAt = timestamp.toDate(),
                likeCount = 0,
                commentCount = 0,
                isLikedByCurrentUser = false,
                likeUsernames = emptyList()
            )

            Result.success(post)
        } catch (e: Exception) {
            Timber.e(e, "Error creating post")
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Verify the post belongs to the current user
            val post = postsCollection.document(postId).get().await()
            if (!post.exists() || post.getString("userId") != userId) {
                return Result.failure(IllegalStateException("Unauthorized to delete this post"))
            }

            // Delete the post images if they exist
            val imageUrls = post.get("imageUrls") as? List<String>
            if (!imageUrls.isNullOrEmpty()) {
                for (imageUrl in imageUrls) {
                    val imageRef = storage.getReferenceFromUrl(imageUrl)
                    imageRef.delete().await()
                }
            }

            // Delete associated likes and comments
            val likesDocs = likesCollection.whereEqualTo("postId", postId).get().await()
            for (doc in likesDocs.documents) {
                likesCollection.document(doc.id).delete().await()
            }

            val commentsDocs = commentsCollection.whereEqualTo("postId", postId).get().await()
            for (doc in commentsDocs.documents) {
                commentsCollection.document(doc.id).delete().await()
            }

            // Delete the post
            postsCollection.document(postId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting post")
            Result.failure(e)
        }
    }

    suspend fun likePost(postId: String): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Check if already liked
            val existingLike = likesCollection
                .whereEqualTo("postId", postId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            if (existingLike.documents.isNotEmpty()) {
                return Result.success(Unit) // Already liked
            }

            // Get user info for like username display
            val userDoc = usersCollection.document(userId).get().await()
            val userName = userDoc.getString("displayName") ?: "Anonymous"

            // Create a new like
            val likeId = likesCollection.document().id
            val likeData = hashMapOf(
                "id" to likeId,
                "postId" to postId,
                "userId" to userId,
                "userName" to userName,
                "createdAt" to FieldValue.serverTimestamp()
            )

            // Use a transaction to update both the like and post like count
            db.runTransaction { transaction ->
                val postRef = postsCollection.document(postId)
                val post = transaction.get(postRef)

                if (!post.exists()) {
                    throw IllegalStateException("Post not found")
                }

                val currentLikeCount = post.getLong("likeCount") ?: 0

                // Get current like usernames
                val likeUsernames = post.get("likeUsernames") as? List<String> ?: emptyList()
                val updatedLikeUsernames = likeUsernames.toMutableList()

                // Add current user to like usernames (max 2 for UI display)
                if (updatedLikeUsernames.size < 2) {
                    updatedLikeUsernames.add(userName)
                }

                transaction.set(likesCollection.document(likeId), likeData)
                transaction.update(postRef, "likeCount", currentLikeCount + 1)
                transaction.update(postRef, "likeUsernames", updatedLikeUsernames)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error liking post")
            Result.failure(e)
        }
    }

    suspend fun unlikePost(postId: String): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Find the like document
            val likeQuery = likesCollection
                .whereEqualTo("postId", postId)
                .whereEqualTo("userId", userId)
                .limit(1)
                .get()
                .await()

            if (likeQuery.documents.isEmpty()) {
                return Result.success(Unit) // Not liked
            }

            val likeDoc = likeQuery.documents[0]
            val userName = likeDoc.getString("userName") ?: "Anonymous"

            // Use a transaction to update both the like and post like count
            db.runTransaction { transaction ->
                val postRef = postsCollection.document(postId)
                val post = transaction.get(postRef)

                if (!post.exists()) {
                    throw IllegalStateException("Post not found")
                }

                val currentLikeCount = post.getLong("likeCount") ?: 0

                // Get current like usernames
                val likeUsernames = post.get("likeUsernames") as? List<String> ?: emptyList()
                val updatedLikeUsernames = likeUsernames.toMutableList()

                // Remove current user from like usernames
                updatedLikeUsernames.remove(userName)

                transaction.delete(likesCollection.document(likeDoc.id))
                transaction.update(postRef, "likeCount", (currentLikeCount - 1).coerceAtLeast(0))
                transaction.update(postRef, "likeUsernames", updatedLikeUsernames)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error unliking post")
            Result.failure(e)
        }
    }

    suspend fun getLikes(postId: String): Flow<List<Like>> = callbackFlow {
        val listener = likesCollection
            .whereEqualTo("postId", postId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error fetching likes")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val likes = snapshot.documents.mapNotNull { doc ->
                        try {
                            Like(
                                id = doc.getString("id") ?: doc.id,
                                postId = doc.getString("postId") ?: "",
                                userId = doc.getString("userId") ?: "",
                                userName = doc.getString("userName") ?: "Anonymous",
                                createdAt = (doc.getTimestamp("createdAt")?.toDate() ?: Date())
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing like document")
                            null
                        }
                    }
                    trySend(likes)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun getComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val listener = commentsCollection
            .whereEqualTo("postId", postId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error fetching comments")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val comments = snapshot.documents.mapNotNull { doc ->
                        try {
                            Comment(
                                id = doc.getString("id") ?: doc.id,
                                postId = doc.getString("postId") ?: "",
                                userId = doc.getString("userId") ?: "",
                                userName = doc.getString("userName") ?: "Anonymous",
                                userPhotoUrl = doc.getString("userPhotoUrl"),
                                content = doc.getString("content") ?: "",
                                createdAt = (doc.getTimestamp("createdAt")?.toDate() ?: Date()),
                                likeCount = doc.getLong("likeCount")?.toInt() ?: 0
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing comment document")
                            null
                        }
                    }
                    trySend(comments)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun addComment(postId: String, content: String): Result<Comment> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            // Get user info
            val userDoc = usersCollection.document(userId).get().await()
            val userName = userDoc.getString("displayName") ?: "Anonymous"
            val userPhotoUrl = userDoc.getString("photoUrl")

            // Create comment
            val commentId = commentsCollection.document().id
            val timestamp = FieldValue.serverTimestamp()

            val commentData = hashMapOf(
                "id" to commentId,
                "postId" to postId,
                "userId" to userId,
                "userName" to userName,
                "userPhotoUrl" to userPhotoUrl,
                "content" to content,
                "createdAt" to timestamp,
                "likeCount" to 0
            )

            // Use a transaction to update both the comment and post comment count
            db.runTransaction { transaction ->
                val postRef = postsCollection.document(postId)
                val post = transaction.get(postRef)

                if (!post.exists()) {
                    throw IllegalStateException("Post not found")
                }

                val currentCommentCount = post.getLong("commentCount") ?: 0

                transaction.set(commentsCollection.document(commentId), commentData)
                transaction.update(postRef, "commentCount", currentCommentCount + 1)
            }.await()

            // Create the comment object to return
            val comment = Comment(
                id = commentId,
                postId = postId,
                userId = userId,
                userName = userName,
                userPhotoUrl = userPhotoUrl,
                content = content,
                createdAt = Date(), // The server timestamp isn't available immediately, so use client time
                likeCount = 0
            )

            Result.success(comment)
        } catch (e: Exception) {
            Timber.e(e, "Error adding comment")
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> {
        return try {
            val userId = currentUserId ?: return Result.failure(IllegalStateException("User not authenticated"))

            val commentDoc = commentsCollection.document(commentId).get().await()
            if (!commentDoc.exists()) {
                return Result.failure(IllegalStateException("Comment not found"))
            }

            val commentUserId = commentDoc.getString("userId")
            if (commentUserId != userId) {
                return Result.failure(IllegalStateException("Unauthorized to delete this comment"))
            }

            val postId = commentDoc.getString("postId") ?: return Result.failure(IllegalStateException("Invalid comment data"))

            // Use a transaction to update both the comment and post comment count
            db.runTransaction { transaction ->
                val postRef = postsCollection.document(postId)
                val post = transaction.get(postRef)

                if (post.exists()) {
                    val currentCommentCount = post.getLong("commentCount") ?: 0
                    transaction.update(postRef, "commentCount", (currentCommentCount - 1).coerceAtLeast(0))
                }

                transaction.delete(commentsCollection.document(commentId))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting comment")
            Result.failure(e)
        }
    }

    private fun parsePostDocument(document: DocumentSnapshot): Post {
        val id = document.getString("id") ?: document.id
        val userId = document.getString("userId") ?: ""
        val userName = document.getString("userName") ?: "Anonymous"
        val userPhotoUrl = document.getString("userPhotoUrl")
        val content = document.getString("content") ?: ""
        val imageUrls = document.get("imageUrls") as? List<String> ?: emptyList()
        val location = document.getString("location")
        val timestamp = document.getTimestamp("createdAt") ?: Timestamp.now()
        val likeCount = document.getLong("likeCount")?.toInt() ?: 0
        val commentCount = document.getLong("commentCount")?.toInt() ?: 0
        val likeUsernames = document.get("likeUsernames") as? List<String> ?: emptyList()

        // Check if current user has liked this post
        val isLiked = false // We'll check this asynchronously if needed

        return Post(
            id = id,
            userId = userId,
            userName = userName,
            userPhotoUrl = userPhotoUrl,
            content = content,
            imageUrls = imageUrls,
            location = location,
            createdAt = timestamp.toDate(),
            likeCount = likeCount,
            commentCount = commentCount,
            isLikedByCurrentUser = isLiked,
            likeUsernames = likeUsernames
        )
    }

    private fun File.toUri() = android.net.Uri.fromFile(this)
}