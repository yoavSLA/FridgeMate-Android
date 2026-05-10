package com.project.fridgemate.ui.fridge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentFridgeBinding
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections

class FridgeFragment : Fragment() {

    private var _binding: FragmentFridgeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FridgeViewModel by viewModels()

    private var unreadBadge: BadgeDrawable? = null
    private var badgeAttached = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFridgeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFridge.layoutManager = LinearLayoutManager(requireContext())
        binding.fabChat.setOnClickListener {
            val fridgeId = viewModel.activeFridgeId.value ?: return@setOnClickListener
            val fridgeName = viewModel.activeFridgeName.value.orEmpty()
            val action = DashboardFragmentDirections
                .actionDashboardFragmentToFridgeChatFragment(fridgeId, fridgeName)
            findNavController().navigate(action)
        }

        unreadBadge = BadgeDrawable.create(requireContext()).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.error_red)
            badgeTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            maxCharacterCount = 3
            isVisible = false
        }

        observeViewModel()
        viewModel.loadItems()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshUnreadCount()
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FridgeViewModel.State.Loading -> showLoading()
                is FridgeViewModel.State.Items -> showItems(state.items)
                is FridgeViewModel.State.Empty -> showEmptyState()
                is FridgeViewModel.State.NoFridge -> showNoFridge()
                is FridgeViewModel.State.NotLoggedIn -> showNotLoggedIn()
                is FridgeViewModel.State.Error -> showEmptyState()
            }
        }
        viewModel.activeFridgeId.observe(viewLifecycleOwner) { id ->
            binding.fabChat.isVisible = !id.isNullOrBlank()
        }
        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            applyUnreadBadge(count)
        }
    }

    private fun applyUnreadBadge(count: Int) {
        val badge = unreadBadge ?: return
        if (count <= 0) {
            if (badgeAttached) {
                BadgeUtils.detachBadgeDrawable(badge, binding.fabChat)
                badgeAttached = false
            }
            badge.isVisible = false
            return
        }
        badge.number = count
        badge.isVisible = true
        if (!badgeAttached) {
            binding.fabChat.post {
                if (_binding != null) {
                    BadgeUtils.attachBadgeDrawable(badge, binding.fabChat)
                    badgeAttached = true
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.rvFridge.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    private fun showItems(items: List<FridgeItem>) {
        binding.loadingOverlay.visibility = View.GONE
        binding.rvFridge.adapter = FridgeAdapter(items)
        binding.rvFridge.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.loadingOverlay.visibility = View.GONE
        binding.rvFridge.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = getString(R.string.fridge_empty_title)
        binding.tvEmptyDesc.text = getString(R.string.fridge_empty_desc)
    }

    private fun showNoFridge() {
        binding.loadingOverlay.visibility = View.GONE
        binding.rvFridge.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = getString(R.string.no_fridge_title)
        binding.tvEmptyDesc.text = getString(R.string.no_fridge_desc)
    }

    private fun showNotLoggedIn() {
        binding.loadingOverlay.visibility = View.GONE
        binding.rvFridge.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = getString(R.string.fridge_not_logged_in_title)
        binding.tvEmptyDesc.text = getString(R.string.fridge_not_logged_in_desc)
    }

    override fun onDestroyView() {
        unreadBadge?.let {
            if (badgeAttached) {
                BadgeUtils.detachBadgeDrawable(it, binding.fabChat)
                badgeAttached = false
            }
        }
        unreadBadge = null
        super.onDestroyView()
        _binding = null
    }
}
