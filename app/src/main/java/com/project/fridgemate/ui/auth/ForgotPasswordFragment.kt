package com.project.fridgemate.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.snackbar.Snackbar
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentForgotPasswordBinding
import com.project.fridgemate.utils.AuthResult

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.isCodeSent.observe(viewLifecycleOwner) { codeSent ->
            if (codeSent) {
                binding.stepEmail.visibility = View.GONE
                binding.stepReset.visibility = View.VISIBLE
                binding.tvDescription.text = getString(R.string.reset_code_desc)
            }
        }

        viewModel.validationResult.observe(viewLifecycleOwner) { result ->
            binding.tilEmail.error = result.emailErrorRes?.let { getString(it) }
            binding.tilCode.error = result.nameErrorRes?.let { getString(it) }
            binding.tilNewPassword.error = result.passwordErrorRes?.let { getString(it) }
            binding.tilConfirmPassword.error = result.confirmPasswordErrorRes?.let { getString(it) }
        }

        viewModel.sendCodeResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResult.Loading -> setSendCodeLoading(true)
                is AuthResult.Success -> {
                    setSendCodeLoading(false)
                    Snackbar.make(requireView(), getString(R.string.reset_code_sent), Snackbar.LENGTH_LONG).show()
                }
                is AuthResult.Error -> {
                    setSendCodeLoading(false)
                    binding.tilEmail.error = result.message
                    viewModel.resetSendCodeResult()
                }
                is AuthResult.Idle -> setSendCodeLoading(false)
            }
        }

        viewModel.resetResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResult.Loading -> setResetLoading(true)
                is AuthResult.Success -> {
                    setResetLoading(false)
                    val resetEmail = viewModel.email
                    clearFormFields()
                    viewModel.clearState()
                    Snackbar.make(requireView(), getString(R.string.password_reset_success), Snackbar.LENGTH_LONG).show()
                    (parentFragment as? AuthFragment)?.showLogin(resetEmail)
                }
                is AuthResult.Error -> {
                    setResetLoading(false)
                    binding.tilCode.error = result.message
                    viewModel.resetResetResult()
                }
                is AuthResult.Idle -> setResetLoading(false)
            }
        }
    }

    private fun setSendCodeLoading(isLoading: Boolean) {
        binding.btnSendReset.isEnabled = !isLoading
        binding.btnSendReset.text = if (isLoading) getString(R.string.sending) else getString(R.string.send_reset_code)
    }

    private fun setResetLoading(isLoading: Boolean) {
        binding.btnResetPassword.isEnabled = !isLoading
        binding.btnResetPassword.text = if (isLoading) getString(R.string.resetting) else getString(R.string.reset_password)
    }

    private fun clearFormFields() {
        binding.etEmail.text?.clear()
        binding.etCode.text?.clear()
        binding.etNewPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
    }

    private fun setupListeners() {
        binding.btnSendReset.setOnClickListener {
            binding.tilEmail.error = null
            val email = binding.etEmail.text.toString()
            viewModel.sendResetCode(email)
        }

        binding.btnResetPassword.setOnClickListener {
            binding.tilCode.error = null
            binding.tilNewPassword.error = null
            binding.tilConfirmPassword.error = null

            val code = binding.etCode.text.toString()
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()
            viewModel.resetPassword(code, newPassword, confirmPassword)
        }

        binding.tvBackToLogin.setOnClickListener {
            (parentFragment as? AuthFragment)?.showLogin()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
