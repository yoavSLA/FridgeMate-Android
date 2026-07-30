package com.project.fridgemate.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentFeedBinding
import com.project.fridgemate.ui.notifications.NotificationViewModel
import com.project.fridgemate.utils.ErrorMapper
import com.project.fridgemate.utils.ToastHelper
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private var isScrolling = false
    private val viewModel: FeedViewModel by activityViewModels()
    private val notifViewModel: NotificationViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeResources(R.color.accent_green)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPosts(refresh = true)
        }

        binding.btnMapView.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToMapViewFragment()
            requireParentFragment().findNavController().navigate(action)
        }

        binding.fabAddPost.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToAddPostFragment()
            requireParentFragment().findNavController().navigate(action)
        }

        binding.btnFindPeople.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToUserListFragment(
                userId = "",
                type = "search"
            )
            requireParentFragment().findNavController().navigate(action)
        }

        setupScopeToggle()
        setupPosts()
        setupErrorState()
        observeLoading()
        observeErrors()
    }

    private fun setupErrorState() {
        binding.root.findViewById<View>(R.id.error_state_feed)?.findViewById<View>(R.id.btn_retry)?.setOnClickListener {
            viewModel.loadPosts(refresh = true)
        }
    }

    private fun setupScopeToggle() {
        val initial = if (viewModel.scope == "following") binding.scopeFollowing.id else binding.scopeAll.id
        binding.scopeToggle.check(initial)
        binding.scopeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newScope = if (checkedId == binding.scopeFollowing.id) "following" else null
            viewModel.setScope(newScope)
        }
    }

    private var postAdapter: PostAdapter? = null

    private fun setupPosts() {
        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        postAdapter = PostAdapter(
            onLikeClick = { post -> viewModel.toggleLike(post) },
            onAddComment = { postId, text -> viewModel.addComment(postId, text) },
            onDeleteClick = { post -> viewModel.deletePost(post.id) },
            onEditClick = { post ->
                val action = DashboardFragmentDirections
                    .actionDashboardFragmentToEditPostFragment(
                        postId = post.id,
                        postTitle = post.postTitle,
                        postDescription = post.description,
                        postImageUrl = post.imageUrl,
                        linkedRecipeName = post.linkedRecipe?.title ?: "",
                        linkedRecipeTime = post.linkedRecipe?.cookingTime ?: "",
                        linkedRecipeDifficulty = post.linkedRecipe?.difficulty ?: ""
                    )
                requireParentFragment().findNavController().navigate(action)
            },
            onDeleteComment = { postId, commentId -> viewModel.deleteComment(postId, commentId) },
            onEditComment = { postId, commentId, newText -> viewModel.editComment(postId, commentId, newText) },
            onExpandComments = { postId -> viewModel.toggleExpanded(postId) },
            onRecipeClick = { recipe ->
                val action = DashboardFragmentDirections
                    .actionDashboardFragmentToRecipeDetailFragment(
                        serverRecipeId = recipe.id
                    )
                requireParentFragment().findNavController().navigate(action)
            },
            onLocationClick = { post ->
                val action = DashboardFragmentDirections
                    .actionDashboardFragmentToMapViewFragment(focusPostId = post.id)
                requireParentFragment().findNavController().navigate(action)
            },
            onAuthorClick = { post ->
                if (post.authorId.isNotEmpty()) {
                    val action = DashboardFragmentDirections.actionDashboardFragmentToUserProfileFragment(post.authorId)
                    requireParentFragment().findNavController().navigate(action)
                }
            },
            onFollowClick = { post -> viewModel.toggleAuthorFollow(post) },
            onCommentAuthorClick = { comment ->
                if (comment.authorId.isNotEmpty()) {
                    val action = DashboardFragmentDirections.actionDashboardFragmentToUserProfileFragment(comment.authorId)
                    requireParentFragment().findNavController().navigate(action)
                }
            }
        )
        binding.rvPosts.adapter = postAdapter
        binding.rvPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if ( dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 3
                    && firstVisibleItemPosition >= 0) {
                    viewModel.loadMorePosts()
                }
            }
        })
        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            postAdapter?.submitList(posts)
            updateEmptyState(posts)
            notifViewModel.pendingPostId.value?.let { postId ->
                val idx = posts.indexOfFirst { it.id == postId }
                if (idx >= 0) {
                    binding.rvPosts.post { binding.rvPosts.scrollToPosition(idx) }
                    notifViewModel.consumePendingPostId()
                }
            }
        }

        notifViewModel.pendingPostId.observe(viewLifecycleOwner) { postId ->
            if (postId == null) return@observe
            val posts = viewModel.posts.value ?: return@observe
            val idx = posts.indexOfFirst { it.id == postId }
            if (idx >= 0) {
                binding.rvPosts.post { binding.rvPosts.scrollToPosition(idx) }
                notifViewModel.consumePendingPostId()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh on resume to catch offline state or sync new data
        viewModel.loadPosts(refresh = true)
    }

    private fun updateEmptyState(posts: List<Post>) {
        val isLoading = viewModel.isLoading.value == true
        val error = viewModel.error.value
        val errorContainer = binding.root.findViewById<View>(R.id.error_state_feed)
        
        val hasData = posts.isNotEmpty()
        val hasError = error != null

        if (isLoading) {
            binding.emptyStateFeed.visibility = View.GONE
            errorContainer?.visibility = View.GONE
            if (!hasData) {
                binding.rvPosts.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh.visibility = View.GONE
            } else {
                binding.rvPosts.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = true
            }
            return
        }

        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (hasError && !hasData) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.GONE
            errorContainer?.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
            binding.fabAddPost.visibility = View.GONE
            binding.root.findViewById<TextView>(R.id.tv_error_desc)?.text = 
                ErrorMapper.mapToUserFriendly(requireContext(), error)
        } else if (!hasData) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.VISIBLE
            errorContainer?.visibility = View.GONE
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.fabAddPost.visibility = View.VISIBLE
        } else {
            binding.rvPosts.visibility = View.VISIBLE
            binding.emptyStateFeed.visibility = View.GONE
            errorContainer?.visibility = View.GONE
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.fabAddPost.visibility = View.VISIBLE
            
            if (hasError) {
                // If we have data and error, it must be a background refresh failure
                val userFriendly = ErrorMapper.mapToUserFriendly(requireContext(), error)
                if (!ErrorMapper.isGeneric(requireContext(), userFriendly)) {
                    ToastHelper.showToast(requireContext(), userFriendly)
                }
                viewModel.clearError()
            }
        }
    }

    private fun observeLoading() {
        viewModel.isLoading.observe(viewLifecycleOwner) { _ ->
            updateEmptyState(viewModel.posts.value ?: emptyList())
        }
    }

    private fun observeErrors() {
        viewModel.error.observe(viewLifecycleOwner) { _ ->
            updateEmptyState(viewModel.posts.value ?: emptyList())
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        postAdapter = null
    }
}
