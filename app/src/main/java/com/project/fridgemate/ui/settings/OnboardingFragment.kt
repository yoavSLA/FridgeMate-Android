package com.project.fridgemate.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedFridgeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnCreate.setOnClickListener {
            val name = binding.etFridgeName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etFridgeName.error = "Please enter a fridge name"
                return@setOnClickListener
            }
            viewModel.createFridge(name)
        }

        binding.btnJoin.setOnClickListener {
            val code = binding.etInviteCode.text.toString().trim()
            if (code.isEmpty()) {
                binding.etInviteCode.error = "Please enter an invite code"
                return@setOnClickListener
            }
            viewModel.joinFridge(code)
        }

        binding.btnSkip.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupObservers() {
        viewModel.hasFridge.observe(viewLifecycleOwner) { hasFridge ->
            if (hasFridge == true) {
                // If they successfully joined/created a fridge, go back to dashboard
                findNavController().navigateUp()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnCreate.isEnabled = !loading
            binding.btnJoin.isEnabled = !loading
            binding.btnSkip.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.actionSuccess.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearActionSuccess()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
