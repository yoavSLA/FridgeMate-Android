package com.project.fridgemate.ui.feed

import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.project.fridgemate.ui.feed.MapViewFragmentDirections
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.R
import com.project.fridgemate.databinding.FragmentMapViewBinding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.project.fridgemate.databinding.DialogCommentsViewerBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.squareup.picasso.Picasso
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MapViewFragment : Fragment() {

    private var _binding: FragmentMapViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedViewModel by activityViewModels()

    private var tabLayoutMediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentMapViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        setupListeners()
        observePosts()
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

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnCloseDetail.setOnClickListener {
            binding.cvPostDetail.visibility = View.GONE
        }

        binding.btnZoomIn.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }

        binding.btnZoomOut.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }

        // Dynamic height adjustment for ViewPager2
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
        
        // Try to find the view holder for the current position
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        
        if (viewHolder == null) {
            // If the view isn't ready (e.g., during a fast jump), try again in the next frame
            viewPager.post {
                val updatedViewHolder = recyclerView.findViewHolderForAdapterPosition(position)
                if (updatedViewHolder != null) {
                    measureAndSetHeight(updatedViewHolder.itemView)
                }
            }
        } else {
            measureAndSetHeight(viewHolder.itemView)
        }
    }

    private var heightAnimator: ValueAnimator? = null

    private fun measureAndSetHeight(itemView: View) {
        val container = itemView.findViewById<View>(R.id.llItemContainer) ?: return
        
        itemView.post {
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

    private fun observePosts() {
        val circleDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.bg_marker_circle)
        val pinDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin)
        
        val markerDrawable = if (circleDrawable != null && pinDrawable != null) {
            val tintedPin = DrawableCompat.wrap(pinDrawable).mutate()
            DrawableCompat.setTint(tintedPin, ContextCompat.getColor(requireContext(), R.color.teal_primary))
            val layers = arrayOf(circleDrawable, tintedPin)
            val layerDrawable = LayerDrawable(layers)
            val padding = 24
            layerDrawable.setLayerInset(1, padding, padding, padding, padding)
            layerDrawable
        } else {
            pinDrawable
        }

        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            if (binding.cvPostDetail.visibility == View.VISIBLE) {
                val currentAdapter = binding.vpPostDetail.adapter as? MapPostDetailAdapter
                if (currentAdapter != null) {
                    // Update the list of posts being displayed in the ViewPager
                    // We need to keep the same subset of posts that was originally shown
                    val currentItems = currentAdapter.currentList
                    val updatedItems = currentItems.map { current ->
                        posts.find { it.id == current.id } ?: current
                    }
                    currentAdapter.submitList(updatedItems)
                }
            }

            binding.mapView.overlays.clear()
            
            val validPosts = posts.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            
            if (validPosts.isEmpty()) {
                binding.cvNoPosts.visibility = View.VISIBLE
            } else {
                binding.cvNoPosts.visibility = View.GONE
                
                // Group posts by location to handle overlapping pins
                val groupedPosts = validPosts.groupBy { GeoPoint(it.latitude, it.longitude) }
                
                groupedPosts.forEach { (point, postsAtLocation) ->
                    val marker = Marker(binding.mapView)
                    marker.position = point
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.icon = markerDrawable
                    marker.title = if (postsAtLocation.size > 1) 
                        "${postsAtLocation.size} posts here" 
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
    }

    private var detailAdapter: MapPostDetailAdapter? = null

    private fun showPostDetails(posts: List<Post>) {
        binding.cvPostDetail.visibility = View.VISIBLE
        
        tabLayoutMediator?.detach()
        
        detailAdapter = MapPostDetailAdapter(
            onRecipeClick = { linkedRecipe ->
                val action = MapViewFragmentDirections.actionMapViewFragmentToRecipeDetailFragment(
                    serverRecipeId = linkedRecipe.id
                )
                findNavController().navigate(action)
            },
            onLikeClick = { post ->
                viewModel.toggleLike(post)
            },
            onCommentClick = { post ->
                showCommentsDialog(post)
            }
        )
        binding.vpPostDetail.adapter = detailAdapter
        detailAdapter?.submitList(posts)
        
        // Reset height for the first item
        binding.vpPostDetail.post {
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

        val commentAdapter = CommentAdapter(showOptions = false)
        dialogBinding.rvComments.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.rvComments.adapter = commentAdapter

        viewModel.loadComments(post.id)

        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            val updatedPost = posts.find { it.id == post.id }
            if (updatedPost != null) {
                commentAdapter.submitList(updatedPost.comments)
                dialogBinding.layoutEmptyComments.visibility = 
                    if (updatedPost.comments.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        _binding = null
    }
}