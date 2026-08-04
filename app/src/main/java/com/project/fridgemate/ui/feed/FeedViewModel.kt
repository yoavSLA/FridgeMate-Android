package com.project.fridgemate.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.dto.CreatePostRequest
import com.project.fridgemate.data.remote.dto.PostLocationRequest
import com.project.fridgemate.data.remote.dto.UpdatePostRequest
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.PostRepository
import com.project.fridgemate.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val authorId: String = "",
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
    val isExpanded: Boolean = false,
    val createdAt: String = "",
    val isFollowingAuthor: Boolean = false
)

data class Comment(
    val id: String,
    val postId: String,
    val authorId: String = "",
    val userName: String,
    val text: String,
    val authorImageUrl: String = "",
    val isOwner: Boolean = false,
    val createdAt: String = ""
)

enum class FeedViewMode {
    ALL, FOLLOWING, MAP
}

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PostRepository(application.applicationContext)
    private val userRepository = UserRepository(application.applicationContext)
    private val followInFlight = mutableSetOf<String>()

    private val _allPosts = mutableListOf<Post>()
    private val _followingPosts = mutableListOf<Post>()
    private var allPostsLastLoad = 0L
    private var followingPostsLastLoad = 0L
    private var allPostsLoadedOnce = false
    private var followingPostsLoadedOnce = false

    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts

    private val _followingCount = MutableLiveData<Int?>(null)
    val followingCount: LiveData<Int?> = _followingCount

    private val _isLoading = MutableLiveData(true)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _updateSuccess = MutableLiveData<Boolean?>(null)
    val updateSuccess: LiveData<Boolean?> = _updateSuccess

    private var currentPage = 1
    private var isLastPage = false

    private var loadPostsJob: Job? = null
    private val _isLoadingMore = MutableLiveData(false)
    val isLoadingMore: LiveData<Boolean> = _isLoadingMore

    private val _viewMode = MutableLiveData(FeedViewMode.ALL)
    val viewMode: LiveData<FeedViewMode> = _viewMode

    var scope: String? = null
        private set

    companion object {
        private const val PAGE_SIZE = 10
        private const val REFRESH_INTERVAL_MS = 2 * 60 * 1000L
    }

    private var postStatJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        loadPosts(refresh = true)
        viewModelScope.launch {
            delay(500)
            loadPosts(refresh = true, silent = true, forceScope = "following")
        }
        startPostStatListener()
        startAutoRefresh()
        fetchFollowingCount()
    }

    private fun fetchFollowingCount() {
        val userId = ApiClient.getTokenManager().userId ?: return
        viewModelScope.launch {
            if (_followingCount.value == null) {
                userRepository.getCachedUser(userId)?.let { cached ->
                    _followingCount.value = cached.followingCount
                }
            }

            when (val result = userRepository.getUserById(userId)) {
                is FridgeResult.Success -> {
                    _followingCount.value = result.data.followingCount
                }
                else -> {}
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                if (isActive) {
                    loadPosts(refresh = true, silent = true)
                }
            }
        }
    }

    private fun startPostStatListener() {
        postStatJob?.cancel()
        postStatJob = viewModelScope.launch {
            while (isActive) {
                repository.observePostStatChanges().collect { change ->
                    val apply: (Post) -> Post = { post ->
                        if (post.id != change.postId) post
                        else post.copy(
                            likesCount = change.likesCount ?: post.likesCount,
                            commentsCount = change.commentsCount ?: post.commentsCount,
                        )
                    }
                    _posts.value = _posts.value?.map(apply)
                    
                    val idxAll = _allPosts.indexOfFirst { it.id == change.postId }
                    if (idxAll >= 0) _allPosts[idxAll] = apply(_allPosts[idxAll])
                    
                    val idxFollow = _followingPosts.indexOfFirst { it.id == change.postId }
                    if (idxFollow >= 0) _followingPosts[idxFollow] = apply(_followingPosts[idxFollow])
                }
                if (isActive) delay(2000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        postStatJob?.cancel()
        autoRefreshJob?.cancel()
    }

    fun setViewMode(newMode: FeedViewMode) {
        if (_viewMode.value == newMode) return
        
        val newScope = when (newMode) {
            FeedViewMode.FOLLOWING -> "following"
            else -> null
        }
        
        if (newMode == FeedViewMode.ALL || newMode == FeedViewMode.FOLLOWING) {
            val isFollowing = newMode == FeedViewMode.FOLLOWING
            if (isFollowing) fetchFollowingCount()
            
            val cache = if (isFollowing) _followingPosts else _allPosts
            val loadedOnce = if (isFollowing) followingPostsLoadedOnce else allPostsLoadedOnce
            
            if (loadedOnce) {
                _isLoading.value = false
                _posts.value = cache.toList()
                
                scope = newScope
                loadPosts(refresh = true, silent = true)
            } else {
                scope = newScope
                loadPosts(refresh = true)
            }
        } else {
            scope = newScope
        }

        _viewMode.value = newMode
    }

    fun resetUpdateState() {
        _updateSuccess.value = null
    }

    fun clearError() {
        _error.value = null
    }
    fun loadPosts(refresh: Boolean = false, silent: Boolean = false, forceScope: String? = "USE_CURRENT") {
        if (loadPostsJob?.isActive == true && forceScope == "USE_CURRENT") return
        
        val targetScope = if (forceScope == "USE_CURRENT") scope else forceScope
        
        if (refresh) {
            if (!silent && targetScope == "following") {
                _followingCount.value = null
            }
            fetchFollowingCount()
        }

        val job = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            
            if (refresh && (_posts.value.isNullOrEmpty())) {
                val cached = if (targetScope == "following") emptyList() else repository.getCachedPosts()
                if (cached.isNotEmpty()) {
                    _posts.value = cached.map { it.toPost() }
                }
            }

            if (refresh && !silent) {
                currentPage = 1
                isLastPage = false
                if (_posts.value.isNullOrEmpty()) {
                    _isLoading.value = true
                }
            }

            if (_posts.value.isNullOrEmpty() && !silent) {
                _isLoading.value = true
            }
            _error.value = null

            val result = repository.getPosts(page = if (refresh) 1 else currentPage, limit = PAGE_SIZE, scope = targetScope)
            
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 800 && !silent) delay(800 - elapsed)

            when (result) {
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

                    if (refresh) {
                        if (targetScope == scope) {
                            currentPage = 1
                            isLastPage = newPosts.size < PAGE_SIZE
                            
                            if (_posts.value != newPosts) {
                                _posts.value = newPosts
                            }
                        }
                        
                        if (targetScope == "following") {
                            _followingPosts.clear()
                            _followingPosts.addAll(newPosts)
                            followingPostsLastLoad = System.currentTimeMillis()
                            followingPostsLoadedOnce = true
                        } else {
                            _allPosts.clear()
                            _allPosts.addAll(newPosts)
                            allPostsLastLoad = System.currentTimeMillis()
                            allPostsLoadedOnce = true
                        }
                    } else {
                        if (targetScope == scope) {
                            isLastPage = newPosts.size < PAGE_SIZE
                            val current = _posts.value ?: emptyList()
                            val combined = (current + newPosts).distinctBy { it.id }
                            if (current != combined) {
                                _posts.value = combined
                            }
                        }
                        
                        if (targetScope == "following") {
                            val combined = _followingPosts + newPosts
                            _followingPosts.clear()
                            _followingPosts.addAll(combined.distinctBy { it.id })
                            followingPostsLoadedOnce = true
                        } else {
                            val combined = _allPosts + newPosts
                            _allPosts.clear()
                            _allPosts.addAll(combined.distinctBy { it.id })
                            allPostsLoadedOnce = true
                        }
                    }
                }
                is FridgeResult.Error -> {
                    if (targetScope == "following") followingPostsLoadedOnce = true
                    else allPostsLoadedOnce = true

                    if (_posts.value.isNullOrEmpty() && !silent && targetScope == scope) {
                        val cached = repository.getCachedPosts()
                        if (cached.isNotEmpty()) {
                            _posts.value = cached.map { it.toPost() }
                        }
                    }
                    if (!silent && targetScope == scope) _error.value = result.message
                }
                else -> {}
            }
            if (targetScope == scope) _isLoading.value = false
        }
        
        if (forceScope == "USE_CURRENT") {
            loadPostsJob = job
        }
    }
    fun loadMorePosts() {
        if (isLastPage || _isLoadingMore.value == true || _isLoading.value == true) {
            return
        }
        viewModelScope.launch {
            _isLoadingMore.value = true
            currentPage++

            when (val result = repository.getPosts(page = currentPage, limit = PAGE_SIZE, scope = scope)) {
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

    fun toggleLike(post: Post) {
        val optimistic: (Post) -> Post = {
            if (it.id == post.id) it.copy(
                isLiked = !it.isLiked,
                likesCount = if (it.isLiked) it.likesCount - 1 else it.likesCount + 1
            ) else it
        }
        _posts.value = _posts.value?.map(optimistic)

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
                }
                is FridgeResult.Error -> {
                    val revert: (Post) -> Post = {
                        if (it.id == post.id) it.copy(
                            isLiked = post.isLiked,
                            likesCount = post.likesCount
                        ) else it
                    }
                    _posts.value = _posts.value?.map(revert)
                }
                else -> {}
            }
        }
    }

    fun toggleAuthorFollow(post: Post) {
        val authorId = post.authorId
        if (authorId.isEmpty() || post.isOwner) return
        if (!followInFlight.add(authorId)) return

        val previous = post.isFollowingAuthor
        val optimistic: (Post) -> Post = {
            if (it.authorId == authorId) it.copy(isFollowingAuthor = !previous) else it
        }
        _posts.value = _posts.value?.map(optimistic)

        viewModelScope.launch {
            try {
                when (val result = userRepository.toggleFollow(authorId)) {
                    is FridgeResult.Success -> {
                        val confirm: (Post) -> Post = {
                            if (it.authorId == authorId) it.copy(isFollowingAuthor = result.data.following) else it
                        }
                        _posts.value = _posts.value?.map(confirm)
                        fetchFollowingCount()
                    }
                    is FridgeResult.Error -> {
                        val revert: (Post) -> Post = {
                            if (it.authorId == authorId) it.copy(isFollowingAuthor = previous) else it
                        }
                        _posts.value = _posts.value?.map(revert)
                        _error.value = result.message
                    }
                    else -> {}
                }
            } finally {
                followInFlight.remove(authorId)
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
                    loadPosts(refresh = true)
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
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
                    _updateSuccess.value = true
                    loadPosts(refresh = true)
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
                    _updateSuccess.value = false
                }
                else -> {}
            }
        }
    }

    fun deletePost(postId: String) {
        _posts.value = _posts.value?.filter { it.id != postId }

        viewModelScope.launch {
            when (val result = repository.deletePost(postId)) {
                is FridgeResult.Error -> {
                    _error.value = result.message
                    loadPosts()
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
    }

    fun loadComments(postId: String) {
        val currentPost = _posts.value?.find { it.id == postId }
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
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
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
                }
                is FridgeResult.Error -> {
                    _error.value = result.message
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
                null
            }
            else -> null
        }
    }

}
