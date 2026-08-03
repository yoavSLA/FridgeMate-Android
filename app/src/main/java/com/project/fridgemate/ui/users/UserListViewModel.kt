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
            val startTime = System.currentTimeMillis()
            delay(300) // debounce typing
            _isLoading.value = true
            _error.value = null

            val result = userRepository.searchUsers(query)
            
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1500) delay(1500 - elapsed)

            when (result) {
                is FridgeResult.Success -> _users.value = result.data
                is FridgeResult.Error -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private fun load(block: suspend () -> FridgeResult<List<UserListItemDto>>) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _isLoading.value = true
            _error.value = null
            
            val result = block()
            
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1500) delay(1500 - elapsed)

            when (result) {
                is FridgeResult.Success -> _users.value = result.data
                is FridgeResult.Error -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

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
