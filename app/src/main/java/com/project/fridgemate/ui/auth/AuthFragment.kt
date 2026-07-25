package com.project.fridgemate.ui.auth

import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.R
import com.project.fridgemate.ui.auth.AuthFragmentDirections
import com.project.fridgemate.databinding.FragmentAuthBinding
import com.project.fridgemate.ui.login.LoginFragment
import com.project.fridgemate.ui.profile.ProfileViewModel
import com.project.fridgemate.ui.register.RegisterFragment

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val profileViewModel: ProfileViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (profileViewModel.isLoggedIn) {
            val action = AuthFragmentDirections.actionAuthFragmentToDashboardFragment()
            findNavController().navigate(action)
            return
        }

        setupUI()

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.auth_container, LoginFragment())
                .commit()
        }
    }

    private fun setupUI() {
        binding.appName.post {
            val paint = binding.appName.paint
            val width = paint.measureText(binding.appName.text.toString())
            val startColor = ContextCompat.getColor(requireContext(), R.color.accent_green_gradient_start)
            val endColor = ContextCompat.getColor(requireContext(), R.color.title_gradient_end)

            val textShader: Shader = LinearGradient(
                0f, 0f, width, binding.appName.textSize,
                intArrayOf(startColor, endColor), null, Shader.TileMode.CLAMP
            )
            binding.appName.paint.shader = textShader
            binding.appName.invalidate()
        }
    }

    fun showRegister() {
        replaceFragment(RegisterFragment())
    }

    fun showLogin(email: String? = null) {
        childFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        replaceFragment(LoginFragment.newInstance(email), addToBackStack = false)
    }

    fun showForgotPassword() {
        replaceFragment(ForgotPasswordFragment())
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = childFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.auth_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        }
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
