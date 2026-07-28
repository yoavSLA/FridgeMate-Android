package com.project.fridgemate.ui.recipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.project.fridgemate.databinding.FragmentRecipesBinding
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper

class RecipesFragment : Fragment() {

    private var _binding: FragmentRecipesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipesViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        viewModel.loadRecommendedIfNeeded()
        observeDataState()
        observeErrors()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> RecipeListFragment.newInstance(RecipeListFragment.TYPE_RECOMMENDED)
                    else -> RecipeListFragment.newInstance(RecipeListFragment.TYPE_FAVORITES)
                }
            }
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Recommended"
                else -> "Favorites"
            }
        }.attach()
    }

    private fun observeDataState() {
        viewModel.noFridge.observe(viewLifecycleOwner) { noFridge ->
            when (noFridge) {
                null -> {
                    binding.emptyState.visibility = View.GONE
                    binding.tabLayout.visibility = View.GONE
                    binding.viewPager.visibility = View.GONE
                }
                true -> {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.tabLayout.visibility = View.GONE
                    binding.viewPager.visibility = View.GONE
                }
                false -> {
                    binding.emptyState.visibility = View.GONE
                    binding.tabLayout.visibility = View.VISIBLE
                    binding.viewPager.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun observeErrors() {
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null && viewModel.isLoading.value != true) {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), error)
                
                // If we have data in Recommended (active or cached), show a Toast
                val hasData = viewModel.recommended.value?.isNotEmpty() == true
                
                if (hasData) {
                    ToastHelper.showToast(requireContext(), userFriendly, Toast.LENGTH_LONG)
                    viewModel.clearError()
                } else {
                    // Find the active fragment in ViewPager and tell it to show the full screen error
                    val currentFrag = childFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem)
                    if (currentFrag is RecipeListFragment) {
                        currentFrag.showError(error)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}