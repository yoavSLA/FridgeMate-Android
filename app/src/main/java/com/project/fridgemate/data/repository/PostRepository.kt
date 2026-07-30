package com.project.fridgemate.data.repository

import android.content.Context
import android.util.Log
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.local.entity.PostEntity
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.PostApi
import com.project.fridgemate.data.remote.dto.*
import com.project.fridgemate.data.remote.socket.SocketManager
import com.project.fridgemate.ui.feed.Post
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class PostStatChange(
    val postId: String,
    val likesCount: Int?,
    val commentsCount: Int?,
)

class PostRepository(context: Context) {

    private val postApi: PostApi = ApiClient.createApi(PostApi::class.java)
    private val postDao = AppDatabase.getInstance(context).postDao()

    fun observePostStatChanges(): Flow<PostStatChange> = callbackFlow {
        val socket = SocketManager.connect()

        val onStat = Emitter.Listener { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@Listener
            val postId = json.optString("postId").takeIf { it.isNotBlank() } ?: return@Listener
            val likes = if (json.has("likesCount") && !json.isNull("likesCount")) json.optInt("likesCount") else null
            val comments = if (json.has("commentsCount") && !json.isNull("commentsCount")) json.optInt("commentsCount") else null
            trySend(PostStatChange(postId, likes, comments))
        }

        val onDisconnect = Emitter.Listener {
            if (SocketManager.connect() !== socket) close()
        }

        socket.on("post_stat_changed", onStat)
        socket.on(Socket.EVENT_DISCONNECT, onDisconnect)

        awaitClose {
            socket.off("post_stat_changed", onStat)
            socket.off(Socket.EVENT_DISCONNECT, onDisconnect)
        }
    }

    suspend fun getPosts(
        page: Int = 1,
        limit: Int = 20,
        scope: String? = null
    ): FridgeResult<PostListResponse> {
        return try {
            Log.d("PostRepository", "Loading page=$page limit=$limit scope=$scope")
            val response = postApi.getPosts(page, limit, scope)
            if (response.isSuccessful) {
                val data = response.body()!!
                // Only cache the unfiltered global feed; "following" scope is a transient view.
                if (scope.isNullOrEmpty() || scope == "all") {
                    cachePosts(data.items)
                }
                FridgeResult.Success(data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getCachedPosts(): List<PostDto> {
        return try {
            postDao.getAll().map { it.toDto() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPostsByUser(
        userId: String,
        page: Int = 1,
        limit: Int = 20
    ): FridgeResult<PostListResponse> {
        return try {
            val response = postApi.getPostsByUser(userId, page, limit)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getMyPosts(): FridgeResult<PostListResponse> {
        return try {
            val response = postApi.getMyPosts()
            if (response.isSuccessful) {
                val data = response.body()!!
                val entities = data.items.map { it.toEntity() }
                postDao.insertAll(entities)
                FridgeResult.Success(data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getCachedMyPosts(): List<PostDto> {
        return try {
            postDao.getMyPosts().map { it.toDto() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createPost(request: CreatePostRequest): FridgeResult<Unit> {
        return try {
            val response = postApi.createPost(request)
            if (response.isSuccessful) {
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun updatePost(postId: String, request: UpdatePostRequest): FridgeResult<Unit> {
        return try {
            val response = postApi.updatePost(postId, request)
            if (response.isSuccessful) {
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun deletePost(postId: String): FridgeResult<Unit> {
        return try {
            val response = postApi.deletePost(postId)
            if (response.isSuccessful) {
                postDao.deleteById(postId)
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun toggleLike(postId: String): FridgeResult<ToggleLikeResponse> {
        return try {
            val response = postApi.toggleLike(postId)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun getComments(postId: String): FridgeResult<List<CommentDto>> {
        return try {
            val response = postApi.getComments(postId)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data.items)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun createComment(postId: String, text: String): FridgeResult<CommentDto> {
        return try {
            val response = postApi.createComment(postId, CreateCommentRequest(text))
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun updateComment(postId: String, commentId: String, text: String): FridgeResult<CommentDto> {
        return try {
            val response = postApi.updateComment(postId, commentId, UpdateCommentRequest(text))
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): FridgeResult<Unit> {
        return try {
            val response = postApi.deleteComment(postId, commentId)
            if (response.isSuccessful) {
                FridgeResult.Success(Unit)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, mimeType: String): FridgeResult<String> {
        return try {
            val requestBody = imageBytes.toRequestBody(mimeType.toMediaType())
            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val part = MultipartBody.Part.createFormData("image", "post.$extension", requestBody)

            val response = postApi.uploadImage(part)
            if (response.isSuccessful) {
                FridgeResult.Success(response.body()!!.data.imageUrl)
            } else {
                FridgeResult.Error(parseError(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            FridgeResult.Error(networkErrorMessage(e))
        }
    }

    private suspend fun cachePosts(posts: List<PostDto>) {
        try {
            val entities = posts.map { it.toEntity() }
            // Delete only non-owner posts to keep "My Shares" cached for offline
            postDao.clearNonOwnerPosts()
            postDao.insertAll(entities)
        } catch (_: Exception) { }
    }

    private suspend fun loadCachedPosts(): List<PostDto> {
        return try {
            postDao.getAll().map { it.toDto() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun PostDto.toEntity(): PostEntity {
        val loc = location
        val authorAddr = authorUserId.address
        
        // Priority: Post's specific coordinates, then Author's registered coordinates
        val hasPostCoords = loc?.coordinates != null && loc.coordinates.size >= 2
        val lng = if (hasPostCoords) loc?.coordinates!![0] else authorAddr?.lng ?: 0.0
        val lat = if (hasPostCoords) loc?.coordinates!![1] else authorAddr?.lat ?: 0.0
        
        return PostEntity(
            id = id,
            authorId = authorUserId.id,
            authorName = authorUserId.displayName,
            authorLocation = loc?.placeName ?: authorAddr?.city ?: "",
            authorProfileImage = authorUserId.profileImage ?: "",
            title = title ?: "",
            text = text,
            imageUrl = mediaUrls.firstOrNull() ?: "",
            likesCount = likesCount,
            commentsCount = commentsCount,
            isLiked = isLiked,
            isOwner = isOwner,
            latitude = lat,
            longitude = lng,
            createdAt = createdAt,
            recipeId = recipeId?.id,
            recipeTitle = recipeId?.title,
            recipeCookingTime = recipeId?.cookingTime,
            recipeDifficulty = recipeId?.difficulty,
            recipeImageUrl = recipeId?.imageUrl
        )
    }

    private fun PostEntity.toDto(): PostDto {
        val recipe = if (recipeId != null) PostRecipeDto(
            id = recipeId,
            title = recipeTitle,
            description = null,
            cookingTime = recipeCookingTime,
            difficulty = recipeDifficulty,
            imageUrl = recipeImageUrl
        ) else null

        return PostDto(
            id = id,
            authorUserId = PostAuthorDto(
                id = authorId,
                displayName = authorName,
                profileImage = authorProfileImage.ifEmpty { null },
                address = if (authorLocation.isNotEmpty()) AddressDto(city = authorLocation) else null
            ),
            title = title,
            text = text,
            mediaUrls = if (imageUrl.isNotEmpty()) listOf(imageUrl) else emptyList(),
            location = if (latitude != 0.0 || longitude != 0.0) PostLocationDto(
                type = "Point",
                coordinates = listOf(longitude, latitude),
                placeName = authorLocation
            ) else null,
            likes = emptyList(),
            recipeId = recipe,
            likesCount = likesCount,
            commentsCount = commentsCount,
            isLiked = isLiked,
            isOwner = isOwner,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun parseError(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "Something went wrong. Please try again."
        return try {
            val json = org.json.JSONObject(errorBody)
            json.optString("message", "Something went wrong. Please try again.")
        } catch (_: Exception) {
            errorBody
        }
    }

    private fun networkErrorMessage(e: Exception): String {
        return if (e is java.net.ConnectException || e is java.net.UnknownHostException) {
            "Unable to connect to server. Please check your connection."
        } else {
            e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
