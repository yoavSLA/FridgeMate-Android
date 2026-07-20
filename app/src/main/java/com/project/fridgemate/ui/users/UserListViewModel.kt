package com.project.fridgemate.ui.users

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.UserListItemDto
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Lists used by [UserListFragment]: followers, following, or search results. */
enum class UserListMode { FOLLOWERS, FOLLOWING, SEARCH }

class UserListViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application.applicationContext)

    private val _users = MutableLiveData<List<UserListItemDto>>(emptyList())
    val users: LiveData<List<UserListItemDto>> = _users

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val meId: String? get() = ApiClient.getTokenManager().userId

    private var searchJob: Job? = null

    fun loadFollowers(userId: String) = load {
        userRepository.getFollowers(userId)
    }

    fun loadFollowing(userId: String) = load {
        userRepository.getFollowing(userId)
    }

    fun searchDebounced(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _users.value = emptyList()
            _isLoading.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce typing
            _isLoading.value = true
            when (val r = userRepository.searchUsers(query)) {
                is FridgeResult.Success -> _users.value = r.data
                is FridgeResult.Error -> _error.value = r.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private fun load(block: suspend () -> FridgeResult<List<UserListItemDto>>) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = block()) {
                is FridgeResult.Success -> _users.value = r.data
                is FridgeResult.Error -> _error.value = r.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    /** Toggles follow + updates the local list optimistically. */
    fun toggleFollow(user: UserListItemDto) {
        val originalList = _users.value ?: return
        val updatedList = originalList.map {
            if (it.id == user.id) it.copy(isFollowing = !it.isFollowing) else it
        }
        _users.value = updatedList

        viewModelScope.launch {
            when (val r = userRepository.toggleFollow(user.id)) {
                is FridgeResult.Success -> {
                    _users.value = _users.value?.map {
                        if (it.id == user.id) it.copy(isFollowing = r.data.following) else it
                    }
                }
                is FridgeResult.Error -> {
                    _users.value = originalList
                    _error.value = r.message
                }
                else -> {}
            }
        }
    }

    fun clearError() { _error.value = null }
}
