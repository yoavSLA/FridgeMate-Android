package com.project.fridgemate.ui.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentLoginBinding
import com.project.fridgemate.ui.auth.AuthFragment
import com.project.fridgemate.ui.auth.AuthFragmentDirections
import com.project.fridgemate.utils.AuthResult
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "LoginFragment"
        private const val ARG_EMAIL = "email"

        fun newInstance(email: String? = null): LoginFragment {
            return LoginFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_EMAIL, email)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        credentialManager = CredentialManager.create(requireContext())
        arguments?.getString(ARG_EMAIL)?.takeIf { it.isNotBlank() }?.let { email ->
            binding.etEmail.setText(email)
        }
        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewModel.validationResult.observe(viewLifecycleOwner) { result ->
            binding.tilEmail.error = result.emailError
            binding.tilPassword.error = result.passwordError
        }

        viewModel.loginResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is AuthResult.Loading -> setLoadingState(true)
                is AuthResult.Success -> {
                    setLoadingState(false)
                    val action = AuthFragmentDirections.actionAuthFragmentToDashboardFragment()
                    findNavController().navigate(action)
                }
                is AuthResult.Error -> {
                    setLoadingState(false)
                    binding.tilPassword.error = result.message
                    viewModel.resetLoginResult()
                }
                is AuthResult.Idle -> setLoadingState(false)
            }
        }

        viewModel.googleError.observe(viewLifecycleOwner) { message ->
            binding.tvGoogleError.text = message ?: ""
            binding.tvGoogleError.isVisible = !message.isNullOrBlank()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogleSignIn.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) getString(R.string.logging_in) else getString(R.string.log_in)
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            binding.tilEmail.error = null
            binding.tilPassword.error = null

            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            viewModel.clearGoogleError()
            startGoogleSignIn()
        }

        binding.tvForgotPassword.setOnClickListener {
            (parentFragment as? AuthFragment)?.showForgotPassword()
        }

        binding.tvSignUp.setOnClickListener {
            (parentFragment as? AuthFragment)?.showRegister()
        }
    }

    private fun startGoogleSignIn() {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (serverClientId.isBlank()) {
            viewModel.reportGoogleError(getString(R.string.google_sign_in_unconfigured))
            return
        }

        viewModel.clearGoogleError()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = credentialManager.getCredential(requireContext(), request)
                handleGoogleCredential(response)
            } catch (e: GetCredentialCancellationException) {
                Log.d(TAG, "Google sign-in cancelled by user: ${e.message}")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google sign-in failed: type=${e.type} msg=${e.message}", e)
                val base = getString(R.string.google_sign_in_failed)
                val message = if (BuildConfig.DEBUG) {
                    "$base (${e::class.java.simpleName}: ${e.message ?: e.type})"
                } else {
                    base
                }
                viewModel.reportGoogleError(message)
            }
        }
    }

    private fun handleGoogleCredential(response: androidx.credentials.GetCredentialResponse) {
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                viewModel.loginWithGoogle(googleCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Failed to parse Google ID token", e)
                viewModel.reportGoogleError(getString(R.string.google_sign_in_failed))
            }
        } else {
            Log.w(TAG, "Unexpected credential type: ${credential::class.java.name}")
            viewModel.reportGoogleError(getString(R.string.google_sign_in_failed))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
