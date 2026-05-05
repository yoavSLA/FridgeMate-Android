package com.project.fridgemate.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project.fridgemate.data.repository.AuthRepository
import com.project.fridgemate.utils.AuthResult
import com.project.fridgemate.utils.AuthValidator
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _loginResult = MutableLiveData<AuthResult>(AuthResult.Idle)
    val loginResult: LiveData<AuthResult> = _loginResult

    private val _validationResult = MutableLiveData<AuthValidator.ValidationResult>()
    val validationResult: LiveData<AuthValidator.ValidationResult> = _validationResult

    private val _googleError = MutableLiveData<String?>(null)
    val googleError: LiveData<String?> = _googleError

    fun login(email: String, password: String) {
        val validation = AuthValidator.validateLogin(email, password)
        _validationResult.value = validation

        if (!validation.isValid) return

        _loginResult.value = AuthResult.Loading

        viewModelScope.launch {
            _loginResult.value = repository.login(email, password)
        }
    }

    fun loginWithGoogle(idToken: String) {
        _googleError.value = null
        _loginResult.value = AuthResult.Loading
        viewModelScope.launch {
            val result = repository.loginWithGoogle(idToken)
            if (result is AuthResult.Error) {
                _googleError.value = result.message
                _loginResult.value = AuthResult.Idle
            } else {
                _loginResult.value = result
            }
        }
    }

    fun reportGoogleError(message: String) {
        _googleError.value = message
        _loginResult.value = AuthResult.Idle
    }

    fun clearGoogleError() {
        _googleError.value = null
    }

    fun resetLoginResult() {
        _loginResult.value = AuthResult.Idle
    }
}
