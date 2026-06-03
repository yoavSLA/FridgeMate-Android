package com.project.fridgemate.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentUserProfileBinding
import com.project.fridgemate.ui.feed.PostAdapter
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
            onLikeClick = { /* read-only on profile for now */ },
            onAddComment = { _, _ -> },
            onDeleteClick = { /* hidden via isOwner=false for other users */ },
            onEditClick = { },
            onDeleteComment = { _, _ -> },
            onEditComment = { _, _, _ -> },
            onExpandComments = { },
            onRecipeClick = { },
            onLocationClick = { },
            onAuthorClick = { post ->
                // No-op when already on that user's profile
                if (post.authorId.isNotEmpty() && post.authorId != resolvedUserId) {
                    findNavController().navigate(
                        UserProfileFragmentDirections.actionUserProfileFragmentSelf(post.authorId)
                    )
                }
            }
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
            binding.tvLocation.visibility = if (locationText.isEmpty()) View.GONE else View.VISIBLE

            binding.tvBio.text = user.bio ?: ""
            binding.tvBio.visibility = if (user.bio.isNullOrBlank()) View.GONE else View.VISIBLE

            binding.tvPostsCount.text = user.postsCount.toString()
            binding.tvFollowersCount.text = user.followersCount.toString()
            binding.tvFollowingCount.text = user.followingCount.toString()

            updatePrimaryButton(user.isFollowing)
            loadAvatar(user.profileImage)
        }

        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            postAdapter.submitList(posts)
            binding.tvEmptyPosts.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            binding.rvPosts.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading && viewModel.user.value == null) View.VISIBLE else View.GONE
        }

        viewModel.followBusy.observe(viewLifecycleOwner) { busy ->
            if (!isMe) binding.btnPrimary.isEnabled = !busy
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun updatePrimaryButton(isFollowing: Boolean) {
        if (isMe) {
            binding.btnPrimary.setText(R.string.edit_profile)
            binding.btnPrimary.setBackgroundColor(
                requireContext().getColor(R.color.teal_primary)
            )
        } else {
            binding.btnPrimary.setText(
                if (isFollowing) R.string.following_action else R.string.follow_action
            )
            binding.btnPrimary.setBackgroundColor(
                requireContext().getColor(
                    if (isFollowing) R.color.gray_text else R.color.teal_primary
                )
            )
        }
    }

    private fun loadAvatar(url: String?) {
        if (url.isNullOrEmpty()) {
            binding.ivAvatar.setImageResource(R.drawable.ic_person)
            return
        }
        val full = if (url.startsWith("/")) BuildConfig.BASE_URL.trimEnd('/') + url else url
        Picasso.get()
            .load(full)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .into(binding.ivAvatar)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
