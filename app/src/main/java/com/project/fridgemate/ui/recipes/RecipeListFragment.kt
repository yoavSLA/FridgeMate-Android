package com.project.fridgemate.ui.recipes

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentRecipeListBinding
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections
import com.project.fridgemate.ui.journal.JournalViewModel
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import android.widget.TextView

class RecipeListFragment : Fragment() {

    companion object {
        private const val ARG_TYPE = "type"
        const val TYPE_RECOMMENDED = "RECOMMENDED"
        const val TYPE_FAVORITES = "FAVORITES"

        fun newInstance(type: String): RecipeListFragment {
            return RecipeListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TYPE, type)
                }
            }
        }
    }

    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipesViewModel by activityViewModels()
    private val journalViewModel: JournalViewModel by activityViewModels()
    private lateinit var adapter: RecipeAdapter
    
    private val cookingTips: Array<String> by lazy {
        resources.getStringArray(R.array.cooking_tips)
    }

    private val tipHandler = Handler(Looper.getMainLooper())
    private var tipIndex = 0
    private val tipRunnable = object : Runnable {
        override fun run() {
            if (_binding == null) return
            tipIndex = (tipIndex + 1) % cookingTips.size
            val fadeOut = AlphaAnimation(1f, 0f).apply { duration = 300 }
            val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 300 }
            binding.tvLoadingTip.startAnimation(fadeOut)
            tipHandler.postDelayed({
                if (_binding == null) return@postDelayed
                binding.tvLoadingTip.text = cookingTips[tipIndex]
                binding.tvLoadingTip.startAnimation(fadeIn)
            }, 300)
            tipHandler.postDelayed(this, 4000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val type = arguments?.getString(ARG_TYPE) ?: TYPE_RECOMMENDED

        val onFavoriteClick = { recipe: com.project.fridgemate.data.local.entity.RecipeEntity ->
            viewModel.toggleFavorite(recipe)
        }

        val onAddToJournalClick = { recipe: com.project.fridgemate.data.local.entity.RecipeEntity ->
            journalViewModel.addRecipeToJournal(recipe, System.currentTimeMillis())
            ToastHelper.showToast(requireContext(), getString(R.string.added_to_journal))
        }

        val onItemClick = { recipe: com.project.fridgemate.data.local.entity.RecipeEntity ->
            val action = DashboardFragmentDirections.actionDashboardFragmentToRecipeDetailFragment(recipe.id)
            requireParentFragment().requireParentFragment()
                .findNavController()
                .navigate(action)
        }

        adapter = RecipeAdapter(onFavoriteClick, onAddToJournalClick, onItemClick)
        binding.rvRecipes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecipes.adapter = adapter

        binding.root.findViewById<View>(R.id.error_state)?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            viewModel.loadRecommended()
        }

        val source = when (type) {
            TYPE_RECOMMENDED -> viewModel.recommended
            else -> viewModel.favorites
        }

        source.observe(viewLifecycleOwner) { recipes ->
            adapter.submitList(recipes)
            updateEmptyState(recipes.isEmpty(), type)
        }

        if (type == TYPE_RECOMMENDED) {
            viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
                if (loading) {
                    showLoading()
                } else {
                    hideLoading()
                    val currentError = viewModel.error.value
                    if (currentError != null) {
                        showError(currentError)
                    } else {
                        updateEmptyState(adapter.itemCount == 0, type)
                    }
                }
            }

            viewModel.fridgeEmpty.observe(viewLifecycleOwner) {
                updateEmptyState(adapter.itemCount == 0, type)
            }

            binding.btnGenerate.setOnClickListener {
                viewModel.loadRecommended()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (arguments?.getString(ARG_TYPE) == TYPE_RECOMMENDED) {
            viewModel.loadRecommended()
        }
    }

    fun showError(error: String) {
        if (_binding == null) return
        binding.loadingOverlay.visibility = View.GONE
        val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), error)
        val hasRecipes = adapter.itemCount > 0
        if (!hasRecipes) {
            binding.rvRecipes.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            binding.btnGenerate.visibility = View.GONE
            val errorView = binding.root.findViewById<View>(R.id.error_state)
            errorView?.visibility = View.VISIBLE
            errorView?.findViewById<TextView>(R.id.tv_error_desc)?.text = userFriendly
        } else {
            binding.rvRecipes.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.btnGenerate.visibility = View.VISIBLE
            binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
            ToastHelper.showToast(requireContext(), userFriendly, Toast.LENGTH_LONG)
            viewModel.clearError()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean, type: String) {
        if (type == TYPE_RECOMMENDED && viewModel.isLoading.value == true) return

        binding.loadingOverlay.visibility = View.GONE
        binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE

        if (isEmpty) {
            if (type == TYPE_FAVORITES) {
                showEmptyState(
                    title = getString(R.string.favorites_empty_title),
                    description = getString(R.string.favorites_empty_desc),
                    iconRes = R.drawable.ic_star_filled
                )
            } else if (viewModel.fridgeEmpty.value == true) {
                showEmptyState(
                    title = getString(R.string.recipes_empty_title),
                    description = getString(R.string.recipes_fridge_empty_desc),
                    iconRes = R.drawable.ic_recipes
                )
                binding.btnGenerate.visibility = View.GONE
            } else if (type == TYPE_RECOMMENDED) {
                showEmptyState(
                    title = getString(R.string.recommended_empty_title),
                    description = getString(R.string.recipes_no_results_desc),
                    iconRes = R.drawable.ic_recipes
                )
                binding.btnGenerate.visibility = View.VISIBLE
            }
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvRecipes.visibility = View.VISIBLE
            binding.btnGenerate.visibility = if (type == TYPE_RECOMMENDED) View.VISIBLE else View.GONE
        }
    }

    private fun showEmptyState(title: String, description: String? = null, iconRes: Int = R.drawable.ic_recipes) {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = title
        binding.ivEmptyIcon.setImageResource(iconRes)
        
        if (description != null) {
            binding.tvEmptyDesc.text = description
            binding.tvEmptyDesc.visibility = View.VISIBLE
        } else {
            binding.tvEmptyDesc.visibility = View.GONE
        }

        binding.rvRecipes.visibility = View.GONE
    }

    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.rvRecipes.visibility = View.GONE
        binding.btnGenerate.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
        tipIndex = (0 until cookingTips.size).random()
        binding.tvLoadingTip.text = cookingTips[tipIndex]
        tipHandler.postDelayed(tipRunnable, 4000)
    }

    private fun hideLoading() {
        tipHandler.removeCallbacks(tipRunnable)
        binding.loadingOverlay.visibility = View.GONE
    }

    override fun onDestroyView() {
        tipHandler.removeCallbacks(tipRunnable)
        super.onDestroyView()
        _binding = null
    }
}
