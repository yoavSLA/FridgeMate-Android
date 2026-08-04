package com.project.fridgemate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentOnboardingBinding
import com.project.fridgemate.ui.fridge.FridgeViewModel
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedFridgeViewModel by viewModels()
    private val fridgeViewModel: FridgeViewModel by activityViewModels()
    private var hasNavigated = false
    private var isJoining = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to prevent back navigation
            }
        })

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnCreate.setOnClickListener {
            val name = binding.etFridgeName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etFridgeName.error = getString(R.string.error_enter_fridge_name)
                return@setOnClickListener
            }
            viewModel.createFridge(name)
        }

        binding.btnJoin.setOnClickListener {
            val code = binding.etInviteCode.text.toString().trim()
            if (code.isEmpty()) {
                binding.etInviteCode.error = getString(R.string.error_enter_invite_code)
                return@setOnClickListener
            }
            viewModel.joinFridge(code)
        }
    }

    private fun setupObservers() {
        viewModel.hasFridge.observe(viewLifecycleOwner) { hasFridge ->
            if (hasFridge == true) {
                // Successful join/create - now check if the fridge is empty
                isJoining = true
                fridgeViewModel.loadItems()
            }
        }

        fridgeViewModel.state.observe(viewLifecycleOwner) { state ->
            if (hasNavigated || !isJoining) return@observe

            when (state) {
                is FridgeViewModel.State.Empty -> {
                    hasNavigated = true
                    findNavController().navigate(R.id.action_onboardingFragment_to_fridgeScanOnboardingFragment)
                }
                is FridgeViewModel.State.Items -> {
                    hasNavigated = true
                    findNavController().popBackStack(R.id.dashboardFragment, false)
                }
                else -> {}
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnCreate.isEnabled = !loading
            binding.btnJoin.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), it)
                ToastHelper.showToast(requireContext(), userFriendly, Toast.LENGTH_LONG)
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
