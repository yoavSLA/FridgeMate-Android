package com.project.fridgemate.utils

import android.util.Patterns
import com.project.fridgemate.R

object AuthValidator {

    private const val MIN_PASSWORD_LENGTH = 6

    data class ValidationResult(
        val isValid: Boolean,
        val emailErrorRes: Int? = null,
        val passwordErrorRes: Int? = null,
        val confirmPasswordErrorRes: Int? = null,
        val nameErrorRes: Int? = null
    )

    fun validateLogin(email: String, password: String): ValidationResult {
        val emailErr = validateEmail(email)
        val passwordErr = if (password.isBlank()) R.string.error_enter_password else null

        return ValidationResult(
            isValid = emailErr == null && passwordErr == null,
            emailErrorRes = emailErr,
            passwordErrorRes = passwordErr
        )
    }

    fun validateRegistration(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        val nameErr = if (name.isBlank()) R.string.error_enter_full_name else null
        val emailErr = validateEmail(email)
        val passwordErr = validatePassword(password)
        val confirmErr = when {
            confirmPassword.isBlank() -> R.string.error_confirm_password
            password != confirmPassword -> R.string.error_passwords_dont_match
            else -> null
        }

        return ValidationResult(
            isValid = nameErr == null && emailErr == null && passwordErr == null && confirmErr == null,
            nameErrorRes = nameErr,
            emailErrorRes = emailErr,
            passwordErrorRes = passwordErr,
            confirmPasswordErrorRes = confirmErr
        )
    }

    fun validateForgotPassword(email: String): ValidationResult {
        val emailErr = validateEmail(email)
        return ValidationResult(
            isValid = emailErr == null,
            emailErrorRes = emailErr
        )
    }

    fun validateResetPassword(code: String, password: String, confirmPassword: String): ValidationResult {
        val passwordErr = validatePassword(password)
        val confirmErr = when {
            confirmPassword.isBlank() -> R.string.error_confirm_password
            password != confirmPassword -> R.string.error_passwords_dont_match
            else -> null
        }
        val codeErr = when {
            code.isBlank() -> R.string.error_enter_reset_code
            code.length != 6 || !code.all { it.isDigit() } -> R.string.error_invalid_reset_code
            else -> null
        }

        return ValidationResult(
            isValid = codeErr == null && passwordErr == null && confirmErr == null,
            nameErrorRes = codeErr,
            passwordErrorRes = passwordErr,
            confirmPasswordErrorRes = confirmErr
        )
    }

    private fun validateEmail(email: String): Int? = when {
        email.isBlank() -> R.string.error_enter_email
        !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> R.string.error_invalid_email
        else -> null
    }

    private fun validatePassword(password: String): Int? = when {
        password.isBlank() -> R.string.error_enter_password
        password.length < MIN_PASSWORD_LENGTH -> R.string.error_password_too_short
        else -> null
    }
}
