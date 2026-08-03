package com.project.fridgemate.ui.recipes

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.data.local.entity.RecipeEntity
import com.project.fridgemate.data.remote.dto.RecipeIngredientDto
import com.project.fridgemate.data.repository.FridgeChatRepository
import com.project.fridgemate.data.repository.FridgeRepository
import com.project.fridgemate.data.repository.FridgeResult
import com.project.fridgemate.data.repository.LastKnownFridge
import com.project.fridgemate.data.repository.RecipeSharePayload
import com.project.fridgemate.databinding.FragmentRecipeDetailBinding
import com.project.fridgemate.databinding.ItemDetailIngredientBinding
import com.project.fridgemate.utils.ToastHelper
import com.project.fridgemate.databinding.ItemDetailStepBinding
import com.project.fridgemate.ui.journal.JournalViewModel
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch

class RecipeDetailFragment : Fragment() {

    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!
    private val gson = Gson()
    private val viewModel: RecipesViewModel by activityViewModels()
    private val journalViewModel: JournalViewModel by activityViewModels()
    private val args: RecipeDetailFragmentArgs by navArgs()
    private val chatRepo by lazy { FridgeChatRepository() }
    private val fridgeRepo by lazy { FridgeRepository(requireContext().applicationContext) }
    private var activeFridgeId: String? = null
    private var activeFridgeName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        val recipeId = args.recipeId
        val serverRecipeId = args.serverRecipeId

        if (serverRecipeId.isNotEmpty()) {
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.contentScroll.visibility = View.GONE

            viewModel.fetchRecipeDetail(serverRecipeId)

            viewModel.detailLoading.observe(viewLifecycleOwner) { loading ->
                if (!loading && viewModel.error.value != null) {
                    ToastHelper.showToast(requireContext(), getString(R.string.error_recipe_load_failed))
                    findNavController().navigateUp()
                }
            }

            viewModel.getRecipeByServerId(serverRecipeId).observe(viewLifecycleOwner) { recipe ->
                if (recipe == null) return@observe
                binding.loadingOverlay.visibility = View.GONE
                binding.contentScroll.visibility = View.VISIBLE
                bindRecipe(recipe)
            }
        } else if (recipeId != 0L) {
            viewModel.getRecipeByRoomId(recipeId).observe(viewLifecycleOwner) { recipe ->
                if (recipe == null) return@observe
                bindRecipe(recipe)
            }
        } else {
            findNavController().navigateUp()
        }
    }

    private fun bindRecipe(recipe: RecipeEntity) {
        binding.tvTitle.text = recipe.title
        binding.tvDescription.text = recipe.description.ifBlank { getString(R.string.recipe_default_desc) }
        binding.tvRecipeTime.text = recipe.cookingTime.ifBlank { "—" }
        binding.tvRecipeDifficulty.text = recipe.difficulty

        binding.tvCalories.text = recipe.calories.ifBlank { "—" }
        binding.tvProtein.text = recipe.protein.ifBlank { "—" }
        binding.tvCarbs.text = recipe.carbs.ifBlank { "—" }
        binding.tvFat.text = recipe.fat.ifBlank { "—" }

        if (recipe.imageUrl.isNotBlank()) {
            val fullUrl = if (recipe.imageUrl.startsWith("/")) {
                BuildConfig.BASE_URL.trimEnd('/') + recipe.imageUrl
            } else {
                recipe.imageUrl
            }
            Picasso.get().load(fullUrl).fit().centerCrop()
                .placeholder(R.color.accent_green_light)
                .into(binding.ivRecipeHero)
        }

        updateFavoriteIcon(recipe.isFavorite)

        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite(recipe)
        }

        binding.btnAddToJournal.setOnClickListener {
            journalViewModel.addRecipeToJournal(recipe, System.currentTimeMillis())
            ToastHelper.showToast(requireContext(), getString(R.string.added_to_journal))
        }

        if (recipe.serverId != null) {
            binding.btnShareAsPost.visibility = View.VISIBLE
            binding.btnShareAsPost.setOnClickListener {
                val action = RecipeDetailFragmentDirections
                    .actionRecipeDetailFragmentToAddPostFragment(
                        prefillTitle = "",
                        prefillDescription = "\uD83C\uDF73 I made \"${recipe.title}\"! Check out this recipe:",
                        prefillRecipeId = recipe.serverId ?: "",
                        prefillRecipeName = recipe.title,
                        prefillRecipeTime = recipe.cookingTime,
                        prefillRecipeDifficulty = recipe.difficulty
                    )
                findNavController().navigate(action)
            }

            setupShareToChat(recipe)
        } else {
            binding.btnShareAsPost.visibility = View.GONE
            binding.btnShareChat.visibility = View.GONE
        }

        populateIngredients(recipe.ingredientsJson)
        populateSteps(recipe.stepsJson)
    }

    private fun setupShareToChat(recipe: RecipeEntity) {
        val serverId = recipe.serverId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val cached = fridgeRepo.peekLastKnownFridge()) {
                is LastKnownFridge.Present -> {
                    applyShareToChatFridge(cached.fridge.id, cached.fridge.name, serverId, recipe)
                }
                is LastKnownFridge.None -> {
                    if (_binding != null) binding.btnShareChat.visibility = View.GONE
                }
                is LastKnownFridge.Unknown -> Unit
            }

            when (val result = fridgeRepo.getMyFridge()) {
                is FridgeResult.Success -> {
                    applyShareToChatFridge(result.data.id, result.data.name, serverId, recipe)
                }
                is FridgeResult.NoFridge -> {
                    if (_binding != null) binding.btnShareChat.visibility = View.GONE
                    activeFridgeId = null
                    activeFridgeName = null
                }
                is FridgeResult.Error -> Unit
            }
        }
    }

    private fun applyShareToChatFridge(
        fridgeId: String,
        fridgeName: String,
        serverId: String,
        recipe: RecipeEntity,
    ) {
        activeFridgeId = fridgeId
        activeFridgeName = fridgeName
        if (_binding == null) return
        binding.btnShareChat.visibility = View.VISIBLE
        binding.btnShareChat.setOnClickListener {
            showShareToChatDialog(serverId, recipe)
        }
    }

    private fun showShareToChatDialog(serverId: String, recipe: RecipeEntity) {
        val fridgeId = activeFridgeId ?: run {
            ToastHelper.showToast(requireContext(), getString(R.string.share_to_chat_no_fridge))
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.share_to_chat_confirm_title)
            .setMessage(getString(R.string.share_to_chat_confirm_msg, recipe.title))
            .setNegativeButton(R.string.share_to_chat_cancel, null)
            .setPositiveButton(R.string.share_to_chat_action) { _, _ ->
                chatRepo.sendRecipeShare(
                    fridgeId = fridgeId,
                    snapshot = RecipeSharePayload(
                        recipeId = serverId,
                        title = recipe.title,
                        imageUrl = recipe.imageUrl.ifBlank { null },
                        cookingTime = recipe.cookingTime.ifBlank { null },
                        difficulty = recipe.difficulty.ifBlank { null },
                    ),
                )
                showSharedSnackbar(fridgeId)
            }
            .show()
    }

    private fun showSharedSnackbar(fridgeId: String) {
        if (_binding == null) return
        val fridgeName = activeFridgeName.orEmpty()
        val accent = ContextCompat.getColor(requireContext(), R.color.accent_green)
        Snackbar.make(binding.root, R.string.share_to_chat_success, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.btnShareChat)
            .setActionTextColor(accent)
            .setAction(R.string.share_to_chat_success_action) {
                val action = RecipeDetailFragmentDirections
                    .actionRecipeDetailFragmentToFridgeChatFragment(fridgeId, fridgeName)
                findNavController().navigate(action)
            }
            .show()
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        if (isFavorite) {
            binding.btnFavorite.setImageResource(R.drawable.ic_star_filled)
            binding.btnFavorite.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.favorite_gold)
            )
        } else {
            binding.btnFavorite.setImageResource(R.drawable.ic_star_outline)
            binding.btnFavorite.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.gray_text)
            )
        }
    }

    private fun populateIngredients(json: String) {
        binding.llIngredients.removeAllViews()
        val type = object : TypeToken<List<RecipeIngredientDto>>() {}.type
        val ingredients: List<RecipeIngredientDto> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        for (ingredient in ingredients) {
            val itemBinding = ItemDetailIngredientBinding.inflate(layoutInflater, binding.llIngredients, false)
            itemBinding.tvIngredientText.text = if (ingredient.amount.isNotBlank()) {
                "${ingredient.name}  \u2014  ${ingredient.amount}"
            } else {
                ingredient.name
            }
            binding.llIngredients.addView(itemBinding.root)
        }
    }

    private fun populateSteps(json: String) {
        binding.llSteps.removeAllViews()
        val type = object : TypeToken<List<String>>() {}.type
        val steps: List<String> = try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }

        for ((index, step) in steps.withIndex()) {
            val itemBinding = ItemDetailStepBinding.inflate(layoutInflater, binding.llSteps, false)
            itemBinding.tvStepNumber.text = (index + 1).toString()
            itemBinding.tvStepText.text = step
            binding.llSteps.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
