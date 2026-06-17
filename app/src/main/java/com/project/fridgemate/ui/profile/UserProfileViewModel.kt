package com.project.fridgemate.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.UserDto
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.PostRepository
import com.project.fridgemate.data.repository.UserRepository
import com.project.fridgemate.ui.feed.Post
import com.project.fridgemate.ui.feed.toComment
import com.project.fridgemate.ui.feed.toPost
import kotlinx.coroutines.launch

/**
 * Drives a profile screen for either the current user (when [userId] is null/blank
 * or equals the logged-in user's id) or for another user.
 *
 * Owns its own state; does NOT touch the shared [ProfileViewModel] used by the
 * edit form / dashboard greeting, so visiting other profiles doesn't pollute "me".
 */
class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application.applicationContext)
    private val postRepository = PostRepository(application.applicationContext)

    private val _user = MutableLiveData<UserDto?>()
    val user: LiveData<UserDto?> = _user

    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _followBusy = MutableLiveData(false)
    val followBusy: LiveData<Boolean> = _followBusy

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private var loadedUserId: String? = null

    val meId: String? get() = ApiClient.getTokenManager().userId

    /** Returns the resolved target user id (own id when [arg] is null/blank). */
    fun resolveTargetId(arg: String?): String? {
        val mine = meId
        return if (arg.isNullOrBlank()) mine else arg
    }

    fun load(targetUserId: String) {
        if (loadedUserId == targetUserId && _user.value != null) {
            // Refresh in background only
            refresh(targetUserId)
            return
        }
        loadedUserId = targetUserId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fresh = userRepository.getUserById(targetUserId)
                _user.value = fresh
                loadPostsFor(targetUserId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh(targetUserId: String, showIndicator: Boolean = false) {
        viewModelScope.launch {
            if (showIndicator) _isRefreshing.value = true
            try {
                val fresh = userRepository.getUserById(targetUserId)
                if (fresh != null) _user.value = fresh
                loadPostsFor(targetUserId)
            } catch (_: Exception) { /* keep current */ }
            finally {
                if (showIndicator) _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadPostsFor(targetUserId: String) {
        when (val result = postRepository.getPostsByUser(targetUserId, page = 1, limit = 50)) {
            is FridgeResult.Success -> {
                _posts.value = result.data.items.map { it.toPost() }
            }
            is FridgeResult.Error -> {
                _error.value = result.message
            }
            else -> {}
        }
    }

    /** Toggle follow on the currently loaded user. No-op when viewing self. */
    fun toggleFollow() {
        val current = _user.value ?: return
        val mine = meId ?: return
        if (current.id == mine) return
        if (_followBusy.value == true) return

        val wasFollowing = current.isFollowing
        // Optimistic update
        _user.value = current.copy(
            isFollowing = !wasFollowing,
            followersCount = current.followersCount + if (wasFollowing) -1 else 1
        )
        _followBusy.value = true
        viewModelScope.launch {
            when (val result = userRepository.toggleFollow(current.id)) {
                is FridgeResult.Success -> {
                    _user.value = _user.value?.copy(
                        isFollowing = result.data.following,
                        followersCount = result.data.followersCount
                    )
                }
                is FridgeResult.Error -> {
                    // Revert
                    _user.value = current
                    _error.value = result.message
                }
                else -> {}
            }
            _followBusy.value = false
        }
    }

    fun clearError() { _error.value = null }

    // ── Post interactions ────────────────────────────────────────────────

    fun toggleLike(post: Post) {
        // Optimistic flip
        _posts.value = _posts.value?.map {
            if (it.id == post.id) it.copy(
                isLiked = !it.isLiked,
                likesCount = if (it.isLiked) it.likesCount - 1 else it.likesCount + 1
            ) else it
        }

        viewModelScope.launch {
            when (val result = postRepository.toggleLike(post.id)) {
                is FridgeResult.Success -> {
                    _posts.value = _posts.value?.map {
                        if (it.id == post.id) it.copy(
                            isLiked = result.data.liked,
                            likesCount = result.data.likesCount
                        ) else it
                    }
                }
                is FridgeResult.Error -> {
                    // Revert to original
                    _posts.value = _posts.value?.map {
                        if (it.id == post.id) it.copy(
                            isLiked = post.isLiked,
                            likesCount = post.likesCount
                        ) else it
                    }
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }

    fun toggleExpanded(postId: String) {
        _posts.value = _posts.value?.map {
            if (it.id == postId) {
                val newExpanded = !it.isExpanded
                if (newExpanded && it.comments.isEmpty()) {
                    loadComments(postId)
                }
                it.copy(isExpanded = newExpanded)
            } else it
        }
    }

    fun loadComments(postId: String) {
        viewModelScope.launch {
            when (val result = postRepository.getComments(postId)) {
                is FridgeResult.Success -> {
                    val comments = result.data.map { it.toComment() }
                    _posts.value = _posts.value?.map {
                        if (it.id == postId) it.copy(comments = comments) else it
                    }
                }
                else -> {}
            }
        }
    }

    fun addComment(postId: String, text: String) {
        viewModelScope.launch {
            when (val result = postRepository.createComment(postId, text)) {
                is FridgeResult.Success -> {
                    val newComment = result.data.toComment()
                    _posts.value = _posts.value?.map {
                        if (it.id == postId) it.copy(
                            comments = it.comments + newComment,
                            commentsCount = it.commentsCount + 1
                        ) else it
                    }
                }
                is FridgeResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun editComment(postId: String, commentId: String, newText: String) {
        viewModelScope.launch {
            when (val result = postRepository.updateComment(postId, commentId, newText)) {
                is FridgeResult.Success -> {
                    val updated = result.data.toComment()
                    _posts.value = _posts.value?.map {
                        if (it.id == postId) it.copy(
                            comments = it.comments.map { c -> if (c.id == commentId) updated else c }
                        ) else it
                    }
                }
                is FridgeResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        val snapshot = _posts.value
        _posts.value = _posts.value?.map {
            if (it.id == postId) it.copy(
                comments = it.comments.filter { c -> c.id != commentId },
                commentsCount = it.commentsCount - 1
            ) else it
        }
        viewModelScope.launch {
            when (val result = postRepository.deleteComment(postId, commentId)) {
                is FridgeResult.Error -> {
                    _posts.value = snapshot
                    _error.value = result.message
                }
                else -> {}
            }
        }
    }

    fun deletePost(postId: String) {
        val snapshot = _posts.value
        _posts.value = _posts.value?.filter { it.id != postId }
        viewModelScope.launch {
            when (val result = postRepository.deletePost(postId)) {
                is FridgeResult.Error -> {
                    _posts.value = snapshot
                    _error.value = result.message
                }
                else -> {
                    // Refresh counts/header
                    loadedUserId?.let { refresh(it) }
                }
            }
        }
    }
}
