package com.project.fridgemate.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.local.AppDatabase
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.api.NominatimApi
import com.project.fridgemate.data.remote.dto.AddressDto
import com.project.fridgemate.data.remote.dto.UpdateProfileRequest
import com.project.fridgemate.data.remote.dto.UserDto
import com.project.fridgemate.data.repository.AuthRepository
import com.project.fridgemate.data.repository.UserRepository
import com.project.fridgemate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application.applicationContext)
    private val authRepository = AuthRepository()

    private val _user = MutableLiveData<UserDto?>()
    val user: LiveData<UserDto?> = _user

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _saveSuccess = MutableLiveData<Boolean>(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _locationDisplay = MutableLiveData<String>("")
    val locationDisplay: LiveData<String> = _locationDisplay

    private val _profileImageUrl = MutableLiveData<String?>()
    val profileImageUrl: LiveData<String?> = _profileImageUrl

    private val _allergies = MutableLiveData<List<AllergyItem>>(
        listOf(
            AllergyItem("Peanuts"),
            AllergyItem("Tree Nuts"),
            AllergyItem("Dairy"),
            AllergyItem("Eggs"),
            AllergyItem("Soy"),
            AllergyItem("Wheat/Gluten"),
            AllergyItem("Fish"),
            AllergyItem("Shellfish"),
            AllergyItem("Sesame")
        )
    )
    val allergies: LiveData<List<AllergyItem>> = _allergies

    private val _selectedPreference = MutableLiveData<String>("NONE")
    val selectedPreference: LiveData<String> = _selectedPreference

    fun loadProfile() {
        val userId = ApiClient.getTokenManager().userId ?: return
        viewModelScope.launch {
            // Show cached data immediately — no spinner needed
            val cached = userRepository.getCachedUser(userId)
            if (cached != null) {
                applyUser(cached)
            } else {
                // No cache yet — show spinner while doing first load
                _loading.value = true
            }

            // Refresh from network in background
            try {
                val fresh = userRepository.getUserById(userId)
                if (fresh != null) applyUser(fresh)
            } catch (e: Exception) {
                if (cached == null) _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    private fun applyUser(user: UserDto) {
        _user.value = user
        _profileImageUrl.value = user.profileImage
        user.address?.let { addr ->
            val parts = listOfNotNull(
                addr.city?.takeIf { it.isNotEmpty() },
                addr.country?.takeIf { it.isNotEmpty() }
            )
            if (parts.isNotEmpty()) _locationDisplay.value = parts.joinToString(", ")
        }
        _selectedPreference.value = user.dietPreference.takeIf { it.isNotEmpty() } ?: "NONE"
    }

    /** Reverse-geocodes device coordinates, updates display and persists to API. */
    fun updateLocation(lat: Double, lng: Double) {
        val userId = ApiClient.getTokenManager().userId ?: return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    NominatimApi.instance.reverse(lat, lng, "json", 1)
                }
                val addr = result.address
                val city = addr?.city ?: addr?.town ?: addr?.village ?: ""
                val countryCode = addr?.countryCode?.uppercase() ?: ""
                val parts = listOf(city, countryCode).filter { it.isNotEmpty() }
                _locationDisplay.value = parts.joinToString(", ")

                val addressDto = AddressDto(
                    fullAddress = result.displayName,
                    city = city.ifEmpty { null },
                    country = countryCode.ifEmpty { null },
                    lat = lat,
                    lng = lng
                )
                userRepository.updateProfile(userId, UpdateProfileRequest(address = addressDto))
            } catch (e: Exception) {
                _error.value = getApplication<Application>().getString(R.string.error_location_detection, e.localizedMessage)
            }
        }
    }

    fun uploadProfileImage(bytes: ByteArray, mimeType: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val url = userRepository.uploadProfileImage(bytes, mimeType)
                if (url != null) {
                    _profileImageUrl.value = url
                } else {
                    _error.value = getApplication<Application>().getString(R.string.error_upload_image)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun saveProfile(name: String, allergies: List<String>, bio: String?) {
        val userId = ApiClient.getTokenManager().userId ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val request = UpdateProfileRequest(
                    displayName = name,
                    profileImage = _profileImageUrl.value,
                    bio = bio,
                    allergies = allergies,
                    dietPreference = _selectedPreference.value
                )
                val result = userRepository.updateProfile(userId, request)
                _user.value = result
                _saveSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun onPreferenceSelected(preference: String) {
        _selectedPreference.value = preference
    }

    fun toggleAllergy(name: String, isChecked: Boolean) {
        val updated = _allergies.value?.map {
            if (it.name == name) it.copy(isChecked = isChecked) else it
        } ?: return
        _allergies.value = updated
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearError() {
        _error.value = null
    }

    val isLoggedIn: Boolean
        get() = ApiClient.getTokenManager().isLoggedIn

    private val _loggedOut = MutableLiveData(false)
    val loggedOut: LiveData<Boolean> = _loggedOut

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(getApplication<Application>()).clearAllTables()
            }
            _loggedOut.value = true
        }
    }
}
