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
import android.animation.ValueAnimator
import android.graphics.drawable.LayerDrawable
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.viewpager2.widget.ViewPager2
import com.project.fridgemate.databinding.DialogCommentsViewerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private var isScrolling = false
    private val viewModel: FeedViewModel by activityViewModels()
    private val notifViewModel: NotificationViewModel by activityViewModels()

    private var tabLayoutMediator: TabLayoutMediator? = null
    private var heightAnimator: ValueAnimator? = null
    private var detailAdapter: MapPostDetailAdapter? = null
    private var detailPostIds: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeResources(R.color.accent_green)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPosts(refresh = true)
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
        setupMap()
        setupMapListeners()
        observeViewMode()
        observeFollowingCount()
        observeLoading()
        observeErrors()
        observeMapErrors()
    }

    private fun observeFollowingCount() {
        viewModel.followingCount.observe(viewLifecycleOwner) { _ ->
            updateEmptyState(viewModel.posts.value ?: emptyList())
        }
    }

    private fun setupErrorState() {
        binding.errorStateFeed.btnRetry.setOnClickListener {
            viewModel.loadPosts(refresh = true)
        }
    }

    private fun setupScopeToggle() {
        val initialId = when (viewModel.viewMode.value) {
            FeedViewMode.FOLLOWING -> binding.scopeFollowing.id
            FeedViewMode.MAP -> binding.scopeMap.id
            else -> binding.scopeAll.id
        }
        binding.scopeToggle.check(initialId)
        binding.scopeToggle.post {
            if (_binding == null) return@post
            animateToggle(initialId, animate = false)
        }

        binding.scopeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                binding.scopeFollowing.id -> FeedViewMode.FOLLOWING
                binding.scopeMap.id -> FeedViewMode.MAP
                else -> FeedViewMode.ALL
            }
            viewModel.setViewMode(newMode)
        }
    }

    private fun animateToggle(checkedId: Int, animate: Boolean = true) {
        val button = binding.scopeToggle.findViewById<View>(checkedId) ?: return
        val slider = binding.toggleSlider

        slider.visibility = View.VISIBLE
        
        val targetX = button.x
        val targetWidth = button.width

        if (!animate || slider.width == 0) {
            slider.x = targetX
            val params = slider.layoutParams
            params.width = targetWidth
            slider.layoutParams = params
            return
        }

        slider.animate()
            .x(targetX)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        val widthAnimator = ValueAnimator.ofInt(slider.width, targetWidth)
        widthAnimator.addUpdateListener { animator ->
            val params = slider.layoutParams
            params.width = animator.animatedValue as Int
            slider.layoutParams = params
        }
        widthAnimator.duration = 250
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.start()
    }

    private var lastRenderedMode: FeedViewMode? = null
    private var syncPending = false

    private fun requestSyncUi() {
        if (syncPending) return
        syncPending = true
        binding.root.post {
            syncPending = false
            if (_binding == null) return@post
            
            val posts = viewModel.posts.value ?: emptyList()
            val mode = viewModel.viewMode.value ?: FeedViewMode.ALL
            
            val isMap = mode == FeedViewMode.MAP
            val wasMap = lastRenderedMode == FeedViewMode.MAP
            val modeChanged = mode != lastRenderedMode

            // 1. Handle Map/Feed transition
            if (modeChanged && isMap != wasMap) {
                val transition = android.transition.TransitionSet().apply {
                    ordering = android.transition.TransitionSet.ORDERING_TOGETHER
                    addTransition(android.transition.Fade())
                    addTransition(android.transition.ChangeBounds())
                    duration = 350
                    interpolator = AccelerateDecelerateInterpolator()
                }
                android.transition.TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)
            }

            if (modeChanged && !isMap && !wasMap) {
                binding.rvPosts.itemAnimator = null
            } else if (binding.rvPosts.itemAnimator == null) {
                binding.rvPosts.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
            }

            // 3. Submit list and sync UI
            postAdapter?.submitList(posts) {
                if (_binding == null) return@submitList
                
                binding.swipeRefresh.visibility = if (isMap) View.GONE else View.VISIBLE
                binding.clMapView.visibility = if (isMap) View.VISIBLE else View.GONE
                binding.fabAddPost.visibility = if (mode == FeedViewMode.ALL) View.VISIBLE else View.GONE
                
                if (modeChanged) {
                    lastRenderedMode = mode
                    val checkedId = when (mode) {
                        FeedViewMode.FOLLOWING -> binding.scopeFollowing.id
                        FeedViewMode.MAP -> binding.scopeMap.id
                        else -> binding.scopeAll.id
                    }
                    animateToggle(checkedId)
                }

                updateEmptyState(posts)
            }

            // Sync Map Detail Adapter if visible
            if (isMap && binding.cvPostDetail.visibility == View.VISIBLE && detailAdapter != null) {
                // If we have current items in the adapter, update them from the fresh posts list
                // but don't remove items that are temporarily missing (e.g. paginated out)
                val adapterItems = detailAdapter?.currentList ?: emptyList()
                val newList = adapterItems.map { adapterPost ->
                    posts.find { it.id == adapterPost.id } ?: adapterPost
                }
                
                if (newList.isNotEmpty()) {
                    detailAdapter?.submitList(newList)
                }
            }
            
            updateMapMarkers(posts)
        }
    }

    private fun observeViewMode() {
        viewModel.viewMode.observe(viewLifecycleOwner) { _ ->
            requestSyncUi()
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(false)
        
        val mapController = binding.mapView.controller
        mapController.setZoom(4.0)
        val startPoint = GeoPoint(39.8283, -98.5795) // Center of US
        mapController.setCenter(startPoint)
    }

    private fun setupMapListeners() {
        binding.btnCloseDetail.setOnClickListener {
            binding.cvPostDetail.visibility = View.GONE
        }

        binding.btnZoomIn.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }

        binding.btnZoomOut.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }

        binding.vpPostDetail.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateViewPagerHeight(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    updateViewPagerHeight(binding.vpPostDetail.currentItem)
                }
            }
        })
    }

    private fun updateViewPagerHeight(position: Int) {
        val viewPager = binding.vpPostDetail
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        
        if (viewHolder == null) {
            viewPager.post {
                if (_binding == null) return@post
                val updatedViewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                if (updatedViewHolder != null) {
                    measureAndSetHeight(updatedViewHolder.itemView)
                }
            }
        } else {
            measureAndSetHeight(viewHolder.itemView)
        }
    }

    private fun measureAndSetHeight(itemView: View) {
        val container = itemView.findViewById<View>(R.id.llItemContainer) ?: return
        itemView.post {
            if (_binding == null) return@post
            val width = binding.vpPostDetail.width
            if (width <= 0) return@post
            val wMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val hMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            container.measure(wMeasureSpec, hMeasureSpec)
            val targetHeight = container.measuredHeight
            val currentHeight = binding.vpPostDetail.height
            if (currentHeight != targetHeight && targetHeight > 0) {
                heightAnimator?.cancel()
                heightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
                    addUpdateListener { animator ->
                        val value = animator.animatedValue as Int
                        val params = binding.vpPostDetail.layoutParams
                        params.height = value
                        binding.vpPostDetail.layoutParams = params
                    }
                    duration = 100
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }

    private fun updateMapMarkers(posts: List<Post>) {
        if (viewModel.viewMode.value != FeedViewMode.MAP) return

        val circleDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_marker_circle)
        val pinDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin)
        val markerDrawable = if (circleDrawable != null && pinDrawable != null) {
            val tintedPin = DrawableCompat.wrap(pinDrawable).mutate()
            DrawableCompat.setTint(tintedPin, ContextCompat.getColor(requireContext(), R.color.accent_green))
            val layers = arrayOf(circleDrawable, tintedPin)
            val layerDrawable = LayerDrawable(layers)
            val padding = 24
            layerDrawable.setLayerInset(1, padding, padding, padding, padding)
            layerDrawable
        } else pinDrawable

        binding.mapView.overlays.clear()
        val validPosts = posts.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        
        if (validPosts.isEmpty()) {
            binding.cvNoPosts.visibility = View.VISIBLE
        } else {
            binding.cvNoPosts.visibility = View.GONE
            
            val clusters = mutableListOf<MutableList<Post>>()
            val threshold = 100.0 // meters - cluster posts within 100m to account for indoor GPS drift
            
            for (post in validPosts) {
                val postPoint = GeoPoint(post.latitude, post.longitude)
                var foundCluster = false
                for (cluster in clusters) {
                    val clusterCenter = GeoPoint(cluster[0].latitude, cluster[0].longitude)
                    if (postPoint.distanceToAsDouble(clusterCenter) < threshold) {
                        cluster.add(post)
                        foundCluster = true
                        break
                    }
                }
                if (!foundCluster) {
                    clusters.add(mutableListOf(post))
                }
            }

            clusters.forEach { postsAtLocation ->
                val point = GeoPoint(postsAtLocation[0].latitude, postsAtLocation[0].longitude)
                val marker = Marker(binding.mapView)
                marker.position = point
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marker.icon = markerDrawable
                marker.title = if (postsAtLocation.size > 1) 
                    getString(R.string.posts_at_location, postsAtLocation.size)
                else postsAtLocation[0].postTitle
                marker.setOnMarkerClickListener { _, _ ->
                    showPostDetails(postsAtLocation)
                    true
                }
                binding.mapView.overlays.add(marker)
            }
        }
        binding.mapView.invalidate()
    }

    private fun showPostDetails(posts: List<Post>) {
        binding.cvPostDetail.visibility = View.VISIBLE
        tabLayoutMediator?.detach()
        detailPostIds = posts.map { it.id }
        detailAdapter = MapPostDetailAdapter(
            onRecipeClick = { linkedRecipe ->
                val action = DashboardFragmentDirections.actionDashboardFragmentToRecipeDetailFragment(
                    serverRecipeId = linkedRecipe.id
                )
                requireParentFragment().findNavController().navigate(action)
            },
            onLikeClick = { post -> viewModel.toggleLike(post) },
            onCommentClick = { post -> showCommentsDialog(post) }
        )
        binding.vpPostDetail.adapter = detailAdapter
        detailAdapter?.submitList(posts)
        binding.vpPostDetail.post {
            if (_binding == null) return@post
            updateViewPagerHeight(0)
        }
        if (posts.size > 1) {
            binding.tlDots.visibility = View.VISIBLE
            tabLayoutMediator = TabLayoutMediator(binding.tlDots, binding.vpPostDetail) { _, _ -> }
            tabLayoutMediator?.attach()
        } else {
            binding.tlDots.visibility = View.GONE
        }
    }

    private fun showCommentsDialog(post: Post) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogCommentsViewerBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        val commentAdapter = CommentAdapter(
            onDeleteComment = { comment -> viewModel.deleteComment(post.id, comment.id) },
            onEditComment = { comment, newText -> viewModel.editComment(post.id, comment.id, newText) },
            onAuthorClick = { comment ->
                if (comment.authorId.isNotEmpty()) {
                    dialog.dismiss()
                    val action = DashboardFragmentDirections.actionDashboardFragmentToUserProfileFragment(comment.authorId)
                    requireParentFragment().findNavController().navigate(action)
                }
            },
            showOptions = true
        )
        dialogBinding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvComments.adapter = commentAdapter
        viewModel.loadComments(post.id)
        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            val updatedPost = posts.find { it.id == post.id }
            if (updatedPost != null) {
                commentAdapter.submitList(updatedPost.comments)
                dialogBinding.layoutEmptyComments.visibility = if (updatedPost.comments.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        dialogBinding.btnSendComment.setOnClickListener {
            val text = dialogBinding.etComment.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.addComment(post.id, text)
                dialogBinding.etComment.text?.clear()
            }
        }
        dialog.show()
    }

    private fun observeMapErrors() {
        // Map errors are mostly handled by the main error observer, but we can add specific ones if needed
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
                viewModel.setViewMode(FeedViewMode.MAP)
                binding.scopeToggle.check(binding.scopeMap.id)
                binding.mapView.controller.setZoom(15.0)
                binding.mapView.controller.setCenter(GeoPoint(post.latitude, post.longitude))
                showPostDetails(listOf(post))
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
            requestSyncUi()
            
            notifViewModel.pendingPostId.value?.let { postId ->
                val idx = posts.indexOfFirst { it.id == postId }
                if (idx >= 0) {
                    binding.rvPosts.post {
                        if (_binding == null) return@post
                        binding.rvPosts.scrollToPosition(idx)
                    }
                    notifViewModel.consumePendingPostId()
                }
            }
        }

        notifViewModel.pendingPostId.observe(viewLifecycleOwner) { postId ->
            if (postId == null) return@observe
            val posts = viewModel.posts.value ?: return@observe
            val idx = posts.indexOfFirst { it.id == postId }
            if (idx >= 0) {
                binding.rvPosts.post {
                    if (_binding == null) return@post
                    binding.rvPosts.scrollToPosition(idx)
                }
                notifViewModel.consumePendingPostId()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (!isHidden) {
            viewModel.loadPosts(refresh = true, silent = true)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            viewModel.loadPosts(refresh = true, silent = true)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    private fun updateEmptyState(posts: List<Post>) {
        val isLoading = viewModel.isLoading.value == true
        val error = viewModel.error.value
        val mode = viewModel.viewMode.value ?: FeedViewMode.ALL
        
        val hasData = posts.isNotEmpty()
        val hasError = error != null
        val isMap = mode == FeedViewMode.MAP

        if (isLoading && !binding.swipeRefresh.isRefreshing) {
            binding.emptyStateFeed.visibility = View.GONE
            binding.errorStateFeed.errorStateContainer.visibility = View.GONE
            if (!hasData && !isMap) {
                binding.rvPosts.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
            }
            return
        }

        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (isMap) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.GONE
            binding.errorStateFeed.errorStateContainer.visibility = View.GONE
            return
        }

        if (hasError && !hasData) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.GONE
            binding.errorStateFeed.errorStateContainer.visibility = View.VISIBLE
            binding.fabAddPost.visibility = View.GONE
            binding.errorStateFeed.tvErrorDesc.text = 
                ErrorMapper.mapToUserFriendly(requireContext(), error)
        } else if (!hasData) {
            binding.rvPosts.visibility = View.GONE
            binding.emptyStateFeed.visibility = View.VISIBLE
            binding.errorStateFeed.errorStateContainer.visibility = View.GONE
            binding.fabAddPost.visibility = if (mode == FeedViewMode.ALL) View.VISIBLE else View.GONE
            
            if (viewModel.viewMode.value == FeedViewMode.FOLLOWING) {
                val fCount = viewModel.followingCount.value
                if (fCount == null) {
                    binding.tvEmptyTitleFeed.text = getString(R.string.loading)
                    binding.tvEmptyDescFeed.text = ""
                } else if (fCount == 0) {
                    binding.tvEmptyTitleFeed.text = getString(R.string.empty_following_no_people_title)
                    binding.tvEmptyDescFeed.text = getString(R.string.empty_following_no_people_desc)
                } else {
                    binding.tvEmptyTitleFeed.text = getString(R.string.empty_following_no_posts_title)
                    binding.tvEmptyDescFeed.text = getString(R.string.empty_following_no_posts_desc)
                }
                binding.ivEmptyIconFeed.setImageResource(R.drawable.ic_group)
            } else {
                binding.tvEmptyTitleFeed.text = getString(R.string.no_posts_yet)
                binding.tvEmptyDescFeed.text = getString(R.string.be_first_to_post)
                binding.ivEmptyIconFeed.setImageResource(R.drawable.ic_feed)
            }
        } else {
            binding.rvPosts.visibility = View.VISIBLE
            binding.emptyStateFeed.visibility = View.GONE
            binding.errorStateFeed.errorStateContainer.visibility = View.GONE
            binding.fabAddPost.visibility = if (mode == FeedViewMode.ALL) View.VISIBLE else View.GONE
            
            if (hasError) {
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
            requestSyncUi()
        }
    }

    private fun observeErrors() {
        viewModel.error.observe(viewLifecycleOwner) { _ ->
            requestSyncUi()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        heightAnimator?.cancel()
        heightAnimator = null
        detailAdapter = null
        _binding = null
        postAdapter = null
    }
}
