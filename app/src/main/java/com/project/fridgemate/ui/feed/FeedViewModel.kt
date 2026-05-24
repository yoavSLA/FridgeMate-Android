package com.project.fridgemate.ui.feed

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.dto.CommentDto
import com.project.fridgemate.data.remote.dto.CreatePostRequest
import com.project.fridgemate.data.remote.dto.PostDto
import com.project.fridgemate.data.remote.dto.PostLocationRequest
import com.project.fridgemate.data.remote.dto.UpdatePostRequest
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.PostRepository
import kotlinx.coroutines.launch
data class LinkedRecipe(
    val id: String,
    val title: String,
    val cookingTime: String,
    val difficulty: String,
    val imageUrl: String
)

data class Post(
    val id: String,
    val userName: String,
    val userLocation: String,
    val postTitle: String,
    val description: String,
    val likesCount: Int,
    val commentsCount: Int,
    val imageUrl: String = "",
    val authorImageUrl: String = "",
    var isLiked: Boolean = false,
    val comments: List<Comment> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isOwner: Boolean = false,
    val linkedRecipe: LinkedRecipe? = null,
    val isExpanded: Boolean = false
)

data class Comment(
    val id: String,
    val postId: String,
    val userName: String,
    val text: String,
    val authorImageUrl: String = "",
    val isOwner: Boolean = false
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PostRepository(application.applicationContext)

    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _myPosts = MutableLiveData<List<Post>>(emptyList())
    val myPosts: LiveData<List<Post>> = _myPosts

    private val _isMyPostsLoading = MutableLiveData(false)
    val isMyPostsLoading: LiveData<Boolean> = _isMyPostsLoading

    private val _updateSuccess = MutableLiveData<Boolean?>(null)
    val updateSuccess: LiveData<Boolean?> = _updateSuccess

    private var currentPage = 1
    private var isLastPage = false

    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    companion object {
        private const val PAGE_SIZE = 10
    }
    init {
        loadPosts()
    }

    fun resetUpdateState() {
        _updateSuccess.value = null
    }

    fun clearError() {
        _error.value = null
    }
    fun loadPosts(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                currentPage = 1
                isLastPage = false
                _posts.value = emptyList()
            }

            if (_posts.value.isNullOrEmpty()) {
                _isLoading.value = true
            }
            _error.value = null

            when (val result = repository.getPosts(page = currentPage, limit = PAGE_SIZE)) {
                is FridgeResult.Success -> {
                    val currentPostsMap = _posts.value?.associateBy { it.id } ?: emptyMap()
                    val newPosts = result.data.items.map { dto ->
                        val post = dto.toPost()
                        currentPostsMap[post.id]?.let { existing ->
                            post.copy(
                                comments = existing.comments,
                                isExpanded = existing.isExpanded
                            )
                        } ?: post
                    }

                    isLastPage = newPosts.size < PAGE_SIZE

                    if (refresh) {
                        _posts.value = newPosts
                    } else {
                        _posts.value = (_posts.value ?: emptyList()) + newPosts
                    }
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }
    fun loadMorePosts() {
        if (isLastPage || _isLoadingMore.value == true || _isLoading.value == true) {
            return
        }
        viewModelScope.launch {
            _isLoadingMore.value = true
            currentPage++

            when (val result = repository.getPosts(page = currentPage, limit = PAGE_SIZE)) {
                is FridgeResult.Success -> {
                    val currentPostsMap = _posts.value?.associateBy { it.id } ?: emptyMap()
                    val newPosts = result.data.items.map { dto ->
                        val post = dto.toPost()
                        currentPostsMap[post.id]?.let { existing ->
                            post.copy(
                                comments = existing.comments,
                                isExpanded = existing.isExpanded
                            )
                        } ?: post
                    }

                    isLastPage = newPosts.size < PAGE_SIZE
                    _posts.value = (_posts.value ?: emptyList()) + newPosts
                }
                is FridgeResult.Error -> {
                    currentPage--
                    _error.value = result.message
                }
                else -> {}
            }
            _isLoadingMore.value = false
        }
    }
    fun loadMyPosts() {
        viewModelScope.launch {
            _isMyPostsLoading.value = true
            when (val result = repository.getMyPosts()) {
                is FridgeResult.Success -> {
                    val currentPostsMap = _myPosts.value?.associateBy { it.id } ?: emptyMap()
                    val posts = result.data.items.map { dto ->
                        val post = dto.toPost()
                        val existing = currentPostsMap[post.id]
                        if (existing != null) {
                            post.copy(
                                comments = existing.comments,
                                isExpanded = existing.isExpanded
                            )
                        } else {
                            post
                        }
                    }
                    _myPosts.value = posts
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    Log.e("FeedViewModel", "loadMyPosts error: ${result.message}")
                }
                else -> {}
            }
            _isMyPostsLoading.value = false
        }
    }

    fun toggleLike(post: Post) {
        val optimistic: (Post) -> Post = {
            if (it.id == post.id) it.copy(
                isLiked = !it.isLiked,
                likesCount = if (it.isLiked) it.likesCount - 1 else it.likesCount + 1
            ) else it
        }
        _posts.value = _posts.value?.map(optimistic)
        _myPosts.value = _myPosts.value?.map(optimistic)

        viewModelScope.launch {
            when (val result = repository.toggleLike(post.id)) {
                is FridgeResult.Success -> {
                    val update: (Post) -> Post = {
                        if (it.id == post.id) it.copy(
                            isLiked = result.data.liked,
                            likesCount = result.data.likesCount
                        ) else it
                    }
                    _posts.value = _posts.value?.map(update)
                    _myPosts.value = _myPosts.value?.map(update)
                }
                is FridgeResult.Error -> {
                    val revert: (Post) -> Post = {
                        if (it.id == post.id) it.copy(
                            isLiked = post.isLiked,
                            likesCount = post.likesCount
                        ) else it
                    }
                    _posts.value = _posts.value?.map(revert)
                    _myPosts.value = _myPosts.value?.map(revert)
                    Log.e("FeedViewModel", "toggleLike error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun addPost(
        title: String,
        description: String,
        imageUrl: String? = null,
        recipeId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        placeName: String? = null
    ) {
        viewModelScope.launch {
            val location = if (latitude != null && longitude != null)
                PostLocationRequest(lat = latitude, lng = longitude, placeName = placeName)
            else null

            val request = CreatePostRequest(
                title = title,
                text = description,
                mediaUrls = if (imageUrl != null) listOf(imageUrl) else emptyList(),
                recipeId = recipeId,
                location = location
            )
            when (val result = repository.createPost(request)) {
                is FridgeResult.Success -> {
                    loadPosts()
                    loadMyPosts()
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    Log.e("FeedViewModel", "addPost error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun editPost(
        postId: String,
        newTitle: String,
        newDescription: String,
        imageUrl: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        placeName: String? = null
    ) {
        viewModelScope.launch {
            val currentPost = _posts.value?.find { it.id == postId }
            val mediaUrls = when {
                imageUrl != null -> listOf(imageUrl)
                currentPost?.imageUrl?.isNotEmpty() == true -> listOf(currentPost.imageUrl)
                else -> null
            }
            
            val locationRequest = if (latitude != null && longitude != null) {
                PostLocationRequest(latitude, longitude, placeName)
            } else null

            val request = UpdatePostRequest(
                title = newTitle,
                text = newDescription,
                mediaUrls = mediaUrls,
                location = locationRequest
            )
            when (val result = repository.updatePost(postId, request)) {
                is FridgeResult.Success -> {
                    val update: (Post) -> Post = {
                        if (it.id == postId) it.copy(
                            postTitle = newTitle,
                            description = newDescription,
                            imageUrl = mediaUrls?.firstOrNull() ?: it.imageUrl,
                            latitude = latitude ?: it.latitude,
                            longitude = longitude ?: it.longitude,
                            userLocation = placeName ?: it.userLocation
                        ) else it
                    }
                    _posts.value = _posts.value?.map(update)
                    _myPosts.value = _myPosts.value?.map(update)
                    _updateSuccess.value = true
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _updateSuccess.value = false
                    Log.e("FeedViewModel", "editPost error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun deletePost(postId: String) {
        _posts.value = _posts.value?.filter { it.id != postId }
        _myPosts.value = _myPosts.value?.filter { it.id != postId }

        viewModelScope.launch {
            when (val result = repository.deletePost(postId)) {
                is FridgeResult.Error -> {
                    _error.value = result.message
                    loadPosts()
                    loadMyPosts()
                }
                else -> {}
            }
        }
    }

    fun toggleExpanded(postId: String) {
        val update: (Post) -> Post = {
            if (it.id == postId) {
                val newExpanded = !it.isExpanded
                if (newExpanded && it.comments.isEmpty()) {
                    loadComments(postId)
                }
                it.copy(isExpanded = newExpanded)
            } else it
        }
        _posts.value = _posts.value?.map(update)
        _myPosts.value = _myPosts.value?.map(update)
    }

    fun loadComments(postId: String) {
        val currentPost = _posts.value?.find { it.id == postId } ?: _myPosts.value?.find { it.id == postId }
        if (currentPost != null && currentPost.comments.isNotEmpty() && currentPost.comments.size == currentPost.commentsCount) {
            return
        }

        viewModelScope.launch {
            when (val result = repository.getComments(postId)) {
                is FridgeResult.Success -> {
                    val comments = result.data.map { it.toComment() }
                    val update: (Post) -> Post = {
                        if (it.id == postId) it.copy(comments = comments) else it
                    }
                    _posts.value = _posts.value?.map(update)
                    _myPosts.value = _myPosts.value?.map(update)
                }
                else -> {}
            }
        }
    }

    fun addComment(postId: String, text: String) {
        viewModelScope.launch {
            when (val result = repository.createComment(postId, text)) {
                is FridgeResult.Success -> {
                    val newComment = result.data.toComment()
                    val update: (Post) -> Post = {
                        if (it.id == postId) {
                            it.copy(
                                comments = it.comments + newComment,
                                commentsCount = it.commentsCount + 1
                            )
                        } else it
                    }
                    _posts.value = _posts.value?.map(update)
                    _myPosts.value = _myPosts.value?.map(update)
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    Log.e("FeedViewModel", "addComment error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun editComment(postId: String, commentId: String, newText: String) {
        viewModelScope.launch {
            when (val result = repository.updateComment(postId, commentId, newText)) {
                is FridgeResult.Success -> {
                    val updated = result.data.toComment()
                    val update: (Post) -> Post = {
                        if (it.id == postId) it.copy(
                            comments = it.comments.map { c ->
                                if (c.id == commentId) updated else c
                            }
                        ) else it
                    }
                    _posts.value = _posts.value?.map(update)
                    _myPosts.value = _myPosts.value?.map(update)
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    Log.e("FeedViewModel", "editComment error: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        val optimistic: (Post) -> Post = {
            if (it.id == postId) it.copy(
                comments = it.comments.filter { c -> c.id != commentId },
                commentsCount = it.commentsCount - 1
            ) else it
        }
        _posts.value = _posts.value?.map(optimistic)
        _myPosts.value = _myPosts.value?.map(optimistic)

        viewModelScope.launch {
            when (val result = repository.deleteComment(postId, commentId)) {
                is FridgeResult.Error -> {
                    _error.value = result.message
                    loadComments(postId)
                }
                else -> {}
            }
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, mimeType: String): String? {
        return when (val result = repository.uploadImage(imageBytes, mimeType)) {
            is FridgeResult.Success -> result.data
            is FridgeResult.Error -> {
                _error.value = result.message
                Log.e("FeedViewModel", "uploadImage error: ${result.message}")
                null
            }
            else -> null
        }
    }

    private fun PostDto.toPost(): Post {
        val loc = location
        val authorAddr = authorUserId.address
        
        // Priority: Post's specific location, then Author's registered location
        val lat = loc?.coordinates?.getOrNull(1) ?: authorAddr?.lat ?: 0.0
        val lng = loc?.coordinates?.getOrNull(0) ?: authorAddr?.lng ?: 0.0

        val placeName = loc?.placeName
        val city = authorAddr?.city

        val recipe = recipeId?.let {
            LinkedRecipe(
                id = it.id,
                title = it.title ?: "",
                cookingTime = it.cookingTime ?: "",
                difficulty = it.difficulty ?: "",
                imageUrl = it.imageUrl ?: ""
            )
        }

        return Post(
            id = id,
            userName = authorUserId.displayName,
            userLocation = placeName ?: city ?: "",
            postTitle = title ?: "",
            description = text,
            likesCount = likesCount,
            commentsCount = commentsCount,
            imageUrl = mediaUrls.firstOrNull() ?: "",
            authorImageUrl = authorUserId.profileImage ?: "",
            isLiked = isLiked,
            isOwner = isOwner,
            latitude = lat,
            longitude = lng,
            linkedRecipe = recipe
        )
    }

    private fun CommentDto.toComment(): Comment {
        return Comment(
            id = id,
            postId = postId,
            userName = authorUserId.displayName,
            text = text,
            authorImageUrl = authorUserId.profileImage ?: "",
            isOwner = isOwner
        )
    }
}
