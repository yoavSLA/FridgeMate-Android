package com.project.fridgemate.ui.recipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.project.fridgemate.databinding.FragmentRecipesBinding

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}