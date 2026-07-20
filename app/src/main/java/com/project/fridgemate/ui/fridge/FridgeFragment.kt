package com.project.fridgemate.ui.fridge

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentFridgeBinding
import com.project.fridgemate.databinding.PopupAssignOwnerBinding
import com.project.fridgemate.ui.settings.MemberAdapter

class FridgeFragment : Fragment() {

    private var _binding: FragmentFridgeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FridgeViewModel by activityViewModels()

    private var currentItems: List<FridgeItem> = emptyList()

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
        viewModel.members.observe(viewLifecycleOwner) {
            if (currentItems.isNotEmpty()) bindAdapter()
        }
        viewModel.ownerAssignMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.consumeOwnerAssignMessage()
            }
        }
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.rvFridge.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
    }

    private fun showItems(items: List<FridgeItem>) {
        currentItems = items
        binding.loadingOverlay.visibility = View.GONE
        bindAdapter()
        binding.rvFridge.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun bindAdapter() {
        binding.rvFridge.adapter = FridgeAdapter(
            items = currentItems,
            members = viewModel.members.value ?: emptyMap(),
            onOwnerIconClick = { anchor, product -> showAssignOwnerPopup(anchor, product) },
            onOwnerRemoveClick = { product -> viewModel.assignOwner(product.id, null) }
        )
    }

    private fun showAssignOwnerPopup(anchor: View, product: FridgeItem.Product) {
        val members = viewModel.members.value?.values?.toList().orEmpty()
        if (members.isEmpty()) return

        val popupBinding = PopupAssignOwnerBinding.inflate(layoutInflater)
        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f

        popupBinding.rvAssignOwnerMembers.layoutManager = LinearLayoutManager(requireContext())
        popupBinding.rvAssignOwnerMembers.adapter = MemberAdapter(
            members = members,
            selectedUserId = product.ownerId,
            onMemberClick = { member ->
                viewModel.assignOwner(product.id, member.userId)
                popupWindow.dismiss()
            }
        )

        popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupBinding.root.measuredWidth
        val xOffset = anchor.width - popupWidth
        popupWindow.showAsDropDown(anchor, xOffset, 4)
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
        super.onDestroyView()
        _binding = null
    }
}
