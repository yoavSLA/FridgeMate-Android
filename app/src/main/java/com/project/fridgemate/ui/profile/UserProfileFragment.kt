package com.project.fridgemate.ui.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentUserProfileBinding
import com.project.fridgemate.ui.feed.PostAdapter
import com.project.fridgemate.utils.AvatarHelper
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import android.widget.TextView
import com.squareup.picasso.Picasso

/**
 * Profile view for the current user (when no [userId] is passed) or another user.
 *
 * - "Self" mode: shows an "Edit Profile" button that navigates to the existing
 *   [MyProfileFragment] settings/edit screen, plus this user's posts.
 * - "Other" mode: shows a Follow/Following toggle and that user's posts.
 *
 * Posts are rendered using the existing [PostAdapter] for visual consistency.
 */
class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

    private val args: UserProfileFragmentArgs by navArgs()
    private val viewModel: UserProfileViewModel by viewModels()
    private lateinit var postAdapter: PostAdapter

    private var resolvedUserId: String? = null
    private var isMe: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        resolvedUserId = viewModel.resolveTargetId(args.userId.ifEmpty { null })
        isMe = resolvedUserId != null && resolvedUserId == viewModel.meId

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.tvTitle.text = getString(
            if (isMe) R.string.my_profile_title else R.string.profile_title
        )

        binding.swipeRefresh.setColorSchemeResources(R.color.accent_green)
        binding.swipeRefresh.setOnRefreshListener {
            val uid = resolvedUserId
            if (uid != null) viewModel.refresh(uid, showIndicator = true)
            else binding.swipeRefresh.isRefreshing = false
        }

        binding.root.findViewById<View>(R.id.error_state)?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            resolvedUserId?.let { viewModel.load(it) }
        }

        setupPosts()
        setupActions()
        observe()

        resolvedUserId?.let { viewModel.load(it) }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from edit screen: refresh display
        resolvedUserId?.let { viewModel.refresh(it) }
    }

    private fun setupPosts() {
        postAdapter = PostAdapter(
            onLikeClick = { post -> viewModel.toggleLike(post) },
            onAddComment = { postId, text -> viewModel.addComment(postId, text) },
            onDeleteClick = { post -> viewModel.deletePost(post.id) },
            onEditClick = { post ->
                findNavController().navigate(
                    UserProfileFragmentDirections.actionUserProfileFragmentToEditPostFragment(
                        postId = post.id,
                        postTitle = post.postTitle,
                        postDescription = post.description,
                        postImageUrl = post.imageUrl,
                        linkedRecipeName = post.linkedRecipe?.title ?: "",
                        linkedRecipeTime = post.linkedRecipe?.cookingTime ?: "",
                        linkedRecipeDifficulty = post.linkedRecipe?.difficulty ?: ""
                    )
                )
            },
            onDeleteComment = { postId, commentId -> viewModel.deleteComment(postId, commentId) },
            onEditComment = { postId, commentId, newText -> viewModel.editComment(postId, commentId, newText) },
            onExpandComments = { postId -> viewModel.toggleExpanded(postId) },
            onRecipeClick = { recipe ->
                findNavController().navigate(
                    UserProfileFragmentDirections.actionUserProfileFragmentToRecipeDetailFragment(
                        serverRecipeId = recipe.id
                    )
                )
            },
            onLocationClick = {
                findNavController().navigate(
                    UserProfileFragmentDirections.actionUserProfileFragmentToMapViewFragment()
                )
            },
            onAuthorClick = { post ->
                // No-op when already on that user's profile
                if (post.authorId.isNotEmpty() && post.authorId != resolvedUserId) {
                    findNavController().navigate(
                        UserProfileFragmentDirections.actionUserProfileFragmentSelf(post.authorId)
                    )
                }
            },
            onFollowClick = { post -> viewModel.toggleAuthorFollow(post) }
        )
        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = postAdapter
        binding.rvPosts.isNestedScrollingEnabled = false
    }

    private fun setupActions() {
        binding.colFollowers.setOnClickListener {
            val uid = resolvedUserId ?: return@setOnClickListener
            findNavController().navigate(
                UserProfileFragmentDirections.actionUserProfileFragmentToUserListFragment(
                    userId = uid,
                    type = "followers"
                )
            )
        }
        binding.colFollowing.setOnClickListener {
            val uid = resolvedUserId ?: return@setOnClickListener
            findNavController().navigate(
                UserProfileFragmentDirections.actionUserProfileFragmentToUserListFragment(
                    userId = uid,
                    type = "following"
                )
            )
        }

        binding.btnPrimary.setOnClickListener {
            if (isMe) {
                findNavController().navigate(
                    UserProfileFragmentDirections.actionUserProfileFragmentToMyProfileFragment()
                )
            } else {
                viewModel.toggleFollow()
            }
        }

        binding.layoutLocationHeader.setOnClickListener {
            val locationText = binding.tvLocation.text.toString()
            if (locationText.isNotEmpty()) {
                val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(locationText)}")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    ToastHelper.showToast(requireContext(), "No map application found")
                }
            }
        }
    }

    private fun observe() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            user ?: return@observe
            binding.tvDisplayName.text = user.displayName
            binding.tvUserName.text = user.userName?.let { "@$it" } ?: ""
            binding.tvUserName.visibility = if (user.userName.isNullOrEmpty()) View.GONE else View.VISIBLE

            val locationText = listOfNotNull(
                user.address?.city?.takeIf { it.isNotEmpty() },
                user.address?.country?.takeIf { it.isNotEmpty() }
            ).joinToString(", ")
            binding.tvLocation.text = locationText
            binding.layoutLocationHeader.visibility = if (locationText.isEmpty()) View.GONE else View.VISIBLE

            // Bio (in details card)
            binding.tvBioLabel.visibility = if (user.bio.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvBio.text = user.bio ?: ""
            binding.tvBio.visibility = if (user.bio.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvPostsCount.text = user.postsCount.toString()
            binding.tvFollowersCount.text = user.followersCount.toString()
            binding.tvFollowingCount.text = user.followingCount.toString()

            // Email (in header card)
            binding.layoutEmailHeader.visibility = if (!user.email.isNullOrEmpty()) View.VISIBLE else View.GONE
            binding.tvEmail.text = user.email

            // Dietary Preference
            val dietText = when (user.dietPreference) {
                "VEGETARIAN" -> getString(R.string.diet_vegetarian)
                "VEGAN" -> getString(R.string.diet_vegan)
                "PESCATARIAN" -> getString(R.string.diet_pescatarian)
                else -> ""
            }
            binding.tvDietaryLabel.visibility = if (dietText.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvDietaryValue.visibility = if (dietText.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvDietaryValue.text = dietText

            // Allergies
            binding.tvAllergiesLabel.visibility = if (user.allergies.isNotEmpty()) View.VISIBLE else View.GONE
            binding.chipGroupAllergies.visibility = if (user.allergies.isNotEmpty()) View.VISIBLE else View.GONE
            binding.chipGroupAllergies.removeAllViews()
            user.allergies.forEach { allergy ->
                val chip = Chip(requireContext()).apply {
                    text = allergy
                    isClickable = false
                    isCheckable = false
                    chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.accent_green_light)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green_dark))
                    chipStrokeWidth = 0f
                }
                binding.chipGroupAllergies.addView(chip)
            }

            // Show cardDetails if any of the above are visible (Bio, Dietary, Allergies)
            val hasDetails = dietText.isNotEmpty() || user.allergies.isNotEmpty() || !user.bio.isNullOrBlank()
            binding.cardDetails.visibility = if (hasDetails) View.VISIBLE else View.GONE

            updatePrimaryButton(user.isFollowing)
            loadAvatar(user.displayName, user.profileImage)
        }

        viewModel.posts.observe(viewLifecycleOwner) { _ ->
            postAdapter.submitList(viewModel.posts.value)
            updateEmptyState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            val hasData = viewModel.user.value != null
            binding.progressBar.visibility = if (loading && !hasData) View.VISIBLE else View.GONE
            if (loading && !hasData) {
                binding.swipeRefresh.visibility = View.GONE
                binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
            }
            updateEmptyState()
        }

        viewModel.isRefreshing.observe(viewLifecycleOwner) { refreshing ->
            binding.swipeRefresh.isRefreshing = refreshing
            updateEmptyState()
        }

        viewModel.followBusy.observe(viewLifecycleOwner) { busy ->
            if (!isMe) binding.btnPrimary.isEnabled = !busy
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), err)
                if (viewModel.user.value != null) {
                    ToastHelper.showToast(requireContext(), userFriendly)
                    viewModel.clearError()
                } else {
                    showError(userFriendly)
                }
            } else {
                binding.root.findViewById<View>(R.id.error_state)?.visibility = View.GONE
                if (viewModel.user.value != null) binding.swipeRefresh.visibility = View.VISIBLE
                updateEmptyState()
            }
        }
    }

    private fun showError(message: String) {
        binding.swipeRefresh.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        val errorView = binding.root.findViewById<View>(R.id.error_state)
        errorView?.visibility = View.VISIBLE
        errorView?.findViewById<TextView>(R.id.tv_error_desc)?.text = message
    }

    private fun updateEmptyState() {
        val posts = viewModel.posts.value ?: emptyList()
        val stillLoading = viewModel.isLoading.value == true || viewModel.isRefreshing.value == true
        val showEmpty = posts.isEmpty() && !stillLoading && viewModel.user.value != null

        binding.emptyStatePosts.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.rvPosts.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE

        if (showEmpty) {
            binding.tvEmptyPostsDesc.setText(
                if (isMe) R.string.no_posts_me_desc else R.string.no_posts_user_desc
            )
        }
    }

    private fun updatePrimaryButton(isFollowing: Boolean) {
        val context = requireContext()
        if (isMe) {
            binding.btnPrimary.setText(R.string.edit_profile)
            binding.btnPrimary.setIconResource(R.drawable.ic_edit)
            binding.btnPrimary.iconTint = ColorStateList.valueOf(context.getColor(R.color.gray_text))
            binding.btnPrimary.setTextColor(context.getColor(R.color.gray_text))
            binding.btnPrimary.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.white))
            binding.btnPrimary.strokeColor = ColorStateList.valueOf(context.getColor(R.color.divider_color))
            binding.btnPrimary.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.button_stroke_width)
        } else {
            if (isFollowing) {
                binding.btnPrimary.setText(R.string.following_action)
                binding.btnPrimary.setIconResource(R.drawable.ic_check)
                binding.btnPrimary.iconTint = ColorStateList.valueOf(context.getColor(R.color.gray_text))
                binding.btnPrimary.setTextColor(context.getColor(R.color.gray_text))
                binding.btnPrimary.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.white))
                binding.btnPrimary.strokeColor = ColorStateList.valueOf(context.getColor(R.color.divider_color))
                binding.btnPrimary.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.button_stroke_width)
            } else {
                binding.btnPrimary.setText(R.string.follow_action)
                binding.btnPrimary.setIconResource(R.drawable.ic_plus_small)
                binding.btnPrimary.iconTint = ColorStateList.valueOf(context.getColor(R.color.white))
                binding.btnPrimary.setTextColor(context.getColor(R.color.white))
                binding.btnPrimary.backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.accent_green))
                binding.btnPrimary.strokeWidth = 0
            }
        }
    }

    private fun loadAvatar(name: String?, url: String?) {
        val placeholder = AvatarHelper.createPlaceholder(requireContext(), name)
        if (url.isNullOrEmpty()) {
            binding.ivAvatar.setImageDrawable(placeholder)
            return
        }
        val full = if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url
        Picasso.get()
            .load(full)
            .placeholder(placeholder)
            .error(placeholder)
            .into(binding.ivAvatar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
