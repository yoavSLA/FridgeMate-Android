package com.project.fridgemate.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentNotificationsBinding
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import android.widget.TextView

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationViewModel by activityViewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter { notification ->
            if (viewModel.handleNotificationClick(notification)) {
                findNavController().navigateUp()
            }
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter

        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.submitList(notifications)
            updateUIState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading && adapter.itemCount == 0) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.emptyState.visibility = View.GONE
                binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), error)
                if (adapter.itemCount > 0) {
                    if (!ErrorMapper.isGeneric(requireContext(), userFriendly)) {
                        ToastHelper.showToast(requireContext(), userFriendly)
                    }
                    viewModel.clearError()
                } else {
                    showError(userFriendly)
                }
            } else {
                updateUIState()
            }
        }

        binding.root.findViewById<View>(R.id.error_state)?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            viewModel.loadAndMarkAllAsRead()
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            findNavController().navigateUp()
        }
    }

    private fun updateUIState() {
        val isEmpty = adapter.itemCount == 0
        val hasError = viewModel.error.value != null
        val isLoading = viewModel.isLoading.value == true

        if (isLoading) return

        if (hasError && isEmpty) {
            binding.rvNotifications.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            // Error view is visible via showError()
        } else {
            binding.rvNotifications.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        binding.rvNotifications.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        val errorView = binding.root.findViewById<View>(R.id.error_state)
        errorView?.visibility = View.VISIBLE
        errorView?.findViewById<TextView>(R.id.tv_error_desc)?.text = message
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAndMarkAllAsRead()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
