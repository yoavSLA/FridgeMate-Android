package com.project.fridgemate.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentRegisterBinding
import com.project.fridgemate.ui.auth.AuthFragment
import com.project.fridgemate.utils.AuthResult

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.validationResult.observe(viewLifecycleOwner) { result ->
            binding.tilFullName.error = result.nameErrorRes?.let { getString(it) }
            binding.tilEmail.error = result.emailErrorRes?.let { getString(it) }
            binding.tilPassword.error = result.passwordErrorRes?.let { getString(it) }
            binding.tilConfirmPassword.error = result.confirmPasswordErrorRes?.let { getString(it) }
        }

        viewModel.registerResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResult.Loading -> setLoadingState(true)
                is AuthResult.Success -> {
                    setLoadingState(false)
                    val registeredEmail = binding.etEmail.text.toString()
                    clearFormFields()
                    viewModel.clearState()
                    Snackbar.make(requireView(), getString(R.string.registration_successful), Snackbar.LENGTH_LONG).show()
                    (parentFragment as? AuthFragment)?.showLogin(registeredEmail)
                }
                is AuthResult.Error -> {
                    setLoadingState(false)
                    Snackbar.make(requireView(), result.message, Snackbar.LENGTH_LONG).show()
                    viewModel.resetRegisterResult()
                }
                is AuthResult.Idle -> setLoadingState(false)
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSignUp.isEnabled = !isLoading
        binding.btnSignUp.text = if (isLoading) getString(R.string.signing_up) else getString(R.string.sign_up)
    }

    private fun clearFormFields() {
        binding.etFullName.text?.clear()
        binding.etEmail.text?.clear()
        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
    }

    private fun setupListeners() {
        binding.btnSignUp.setOnClickListener {
            binding.tilFullName.error = null
            binding.tilEmail.error = null
            binding.tilPassword.error = null
            binding.tilConfirmPassword.error = null

            val fullName = binding.etFullName.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            viewModel.register(fullName, email, password, confirmPassword)
        }

        binding.tvLogin.setOnClickListener {
            (parentFragment as? AuthFragment)?.showLogin()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
