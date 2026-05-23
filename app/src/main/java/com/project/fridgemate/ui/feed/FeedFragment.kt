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
import androidx.recyclerview.widget.RecyclerView
class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private var isScrolling = false
    private val viewModel: FeedViewModel by activityViewModels()

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

        binding.swipeRefresh.setColorSchemeResources(R.color.teal_primary)
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

        binding.btnMyPosts.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToMyPostsFragment()
            requireParentFragment().findNavController().navigate(action)
        }

        setupPosts()
        observeLoading()
        observeErrors()
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
        }
    }

    private fun updateEmptyState(posts: List<Post>) {
        val isLoading = viewModel.isLoading.value == true
        if (posts.isEmpty() && !isLoading) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.VISIBLE
        } else {
            binding.rvPosts.visibility = View.VISIBLE
            binding.emptyStateFeed.visibility = View.GONE
        }
    }

    private fun observeLoading() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = false
            if (loading && (postAdapter?.itemCount ?: 0) == 0) {
                binding.progressBar.visibility = View.VISIBLE
                binding.emptyStateFeed.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                updateEmptyState(viewModel.posts.value ?: emptyList())
            }
        }
    }

    private fun observeErrors() {
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
                updateEmptyState(viewModel.posts.value ?: emptyList())
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        postAdapter = null
    }
}
