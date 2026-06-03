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

    fun refresh(targetUserId: String) {
        viewModelScope.launch {
            try {
                val fresh = userRepository.getUserById(targetUserId)
                if (fresh != null) _user.value = fresh
                loadPostsFor(targetUserId)
            } catch (_: Exception) { /* keep current */ }
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
}
