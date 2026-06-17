package com.project.fridgemate.ui.dashboard

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.firebase.messaging.FirebaseMessaging
import com.project.fridgemate.ui.dashboard.DashboardFragmentDirections
import com.project.fridgemate.BuildConfig
import com.project.fridgemate.MainActivity
import com.project.fridgemate.R
import com.squareup.picasso.Picasso
import com.project.fridgemate.data.repository.UserRepository
import com.project.fridgemate.databinding.FragmentDashboardBinding
import com.project.fridgemate.databinding.PopupProfileMenuBinding
import com.project.fridgemate.ui.fridge.FridgeFragment
import com.project.fridgemate.ui.fridge.FridgeViewModel
import com.project.fridgemate.ui.notifications.NotificationViewModel
import com.project.fridgemate.ui.profile.ProfileViewModel
import com.project.fridgemate.ui.recipes.RecipesFragment
import com.project.fridgemate.ui.feed.FeedFragment
import com.project.fridgemate.ui.journal.JournalFragment
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by activityViewModels()
    private val notificationViewModel: NotificationViewModel by activityViewModels()
    private val fridgeViewModel: FridgeViewModel by activityViewModels()

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { hideBanner() }

    private var currentTabId: Int = R.id.tab_feed

    private var chatBadge: BadgeDrawable? = null
    private var chatBadgeAttached = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!profileViewModel.isLoggedIn) {
            val action = DashboardFragmentDirections.actionDashboardFragmentToAuthFragment()
            findNavController().navigate(action)
            return
        }

        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt("selected_tab_id", R.id.tab_feed)
        }

        // Restore tab selection UI
        updateTabUI(currentTabId)

        // Restore or initialize fragment
        val currentFragment = childFragmentManager.findFragmentById(R.id.dashboard_nav_host)
        if (currentFragment == null || isWrongFragment(currentFragment, currentTabId)) {
            showFragmentForTab(currentTabId)
        }

        setupTabListeners()
        setupProfileMenu()
        setupNotificationsIcon()
        setupChatButton()
        loadGreeting()
        observeNotifications()
        observeFridgeChat()
        registerFcmToken()

        profileViewModel.loggedOut.observe(viewLifecycleOwner) { loggedOut ->
            if (loggedOut) {
                val intent = Intent(requireContext(), MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }

    private fun isWrongFragment(fragment: Fragment, tabId: Int): Boolean {
        return when (tabId) {
            R.id.tab_feed -> fragment !is FeedFragment
            R.id.tab_recipes -> fragment !is RecipesFragment
            R.id.tab_my_fridge -> fragment !is FridgeFragment
            R.id.tab_journal -> fragment !is JournalFragment
            else -> false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_tab_id", currentTabId)
    }

    private fun loadGreeting() {
        profileViewModel.loadProfile()
        profileViewModel.user.observe(viewLifecycleOwner) { user ->
            user?.let {
                val firstName = it.displayName.split(" ").firstOrNull() ?: it.displayName
                binding.tvGreeting.text = getString(R.string.greeting_format, firstName)
            }
        }
        profileViewModel.profileImageUrl.observe(viewLifecycleOwner) { imageUrl ->
            if (!imageUrl.isNullOrEmpty()) {
                val url = if (imageUrl.startsWith("/"))
                    BuildConfig.BASE_URL.trimEnd('/') + imageUrl
                else imageUrl
                Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_person)
            }
        }
    }

    private fun setupTabListeners() {
        binding.tabMyFridge.setOnClickListener {
            if (currentTabId != it.id) {
                currentTabId = it.id
                updateTabUI(currentTabId)
                showFragmentForTab(currentTabId)
            }
        }
        binding.tabFeed.setOnClickListener {
            if (currentTabId != it.id) {
                currentTabId = it.id
                updateTabUI(currentTabId)
                showFragmentForTab(currentTabId)
            }
        }
        binding.tabRecipes.setOnClickListener {
            if (currentTabId != it.id) {
                currentTabId = it.id
                updateTabUI(currentTabId)
                showFragmentForTab(currentTabId)
            }
        }
        binding.tabJournal.setOnClickListener {
            if (currentTabId != it.id) {
                currentTabId = it.id
                updateTabUI(currentTabId)
                showFragmentForTab(currentTabId)
            }
        }
    }

    private fun updateTabUI(tabId: Int) {
        resetTab(binding.tabFeed, binding.ivTabFeed, binding.tvTabFeed)
        resetTab(binding.tabMyFridge, binding.ivTabFridge, binding.tvTabFridge)
        resetTab(binding.tabRecipes, binding.ivTabRecipes, binding.tvTabRecipes)
        resetTab(binding.tabJournal, binding.ivTabJournal, binding.tvTabJournal)

        val accentColor = ContextCompat.getColor(requireContext(), R.color.teal_primary)
        when (tabId) {
            R.id.tab_my_fridge -> {
                binding.ivTabFridge.setColorFilter(accentColor)
                binding.tvTabFridge.setTextColor(accentColor)
                binding.tvTabFridge.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            R.id.tab_recipes -> {
                binding.ivTabRecipes.setColorFilter(accentColor)
                binding.tvTabRecipes.setTextColor(accentColor)
                binding.tvTabRecipes.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            R.id.tab_feed -> {
                binding.ivTabFeed.setColorFilter(accentColor)
                binding.tvTabFeed.setTextColor(accentColor)
                binding.tvTabFeed.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            R.id.tab_journal -> {
                binding.ivTabJournal.setColorFilter(accentColor)
                binding.tvTabJournal.setTextColor(accentColor)
                binding.tvTabJournal.setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun resetTab(tab: View, icon: android.widget.ImageView, text: android.widget.TextView) {
        val gray = ContextCompat.getColor(requireContext(), R.color.gray_text)
        icon.setColorFilter(gray)
        text.setTextColor(gray)
        text.setTypeface(null, android.graphics.Typeface.NORMAL)
    }

    private fun showFragmentForTab(tabId: Int) {
        val fragment = when (tabId) {
            R.id.tab_feed -> FeedFragment()
            R.id.tab_recipes -> RecipesFragment()
            R.id.tab_my_fridge -> FridgeFragment()
            R.id.tab_journal -> JournalFragment()
            else -> FeedFragment()
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.dashboard_nav_host, fragment)
            .commit()
    }

    private fun setupProfileMenu() {
        binding.ivProfile.setOnClickListener {
            showProfilePopup(it)
        }
    }

    private fun showProfilePopup(anchor: View) {
        val popupBinding = PopupProfileMenuBinding.inflate(layoutInflater)

        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupBinding.menuProfile.setOnClickListener {
            popupWindow.dismiss()
            val action = DashboardFragmentDirections.actionDashboardFragmentToUserProfileFragment("")
            findNavController().navigate(action)
        }

        popupBinding.menuSettings.setOnClickListener {
            popupWindow.dismiss()
            val action = DashboardFragmentDirections.actionDashboardFragmentToSettingsFragment()
            findNavController().navigate(action)
        }

        popupBinding.menuLogout.setOnClickListener {
            popupWindow.dismiss()
            profileViewModel.logout()
        }

        popupWindow.elevation = 8f

        // Measure the popup to calculate offset
        popupBinding.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupBinding.root.measuredWidth

        // Calculate xOffset to align the right edge of the popup with the right edge of the anchor
        val xOffset = anchor.width - popupWidth
        val yOffset = resources.getDimensionPixelSize(R.dimen.margin_small)

        popupWindow.showAsDropDown(anchor, xOffset, yOffset)
    }
    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            lifecycleScope.launch {
                runCatching { UserRepository(requireContext()).registerFcmToken(token) }
            }
        }
    }

    private fun setupNotificationsIcon() {
        binding.ivNotifications.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToNotificationsFragment()
            findNavController().navigate(action)
        }
    }

    private fun setupChatButton() {
        chatBadge = BadgeDrawable.create(requireContext()).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.error_red)
            badgeTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            maxCharacterCount = 3
            isVisible = false
        }
        binding.btnChat.setOnClickListener {
            val fridgeId = fridgeViewModel.activeFridgeId.value ?: return@setOnClickListener
            val fridgeName = fridgeViewModel.activeFridgeName.value.orEmpty()
            val action = DashboardFragmentDirections
                .actionDashboardFragmentToFridgeChatFragment(fridgeId, fridgeName)
            findNavController().navigate(action)
        }
            if (fridgeViewModel.activeFridgeId.value.isNullOrBlank()) {
            fridgeViewModel.loadItems()
        }
    }

    private fun observeFridgeChat() {
        fridgeViewModel.activeFridgeId.observe(viewLifecycleOwner) { id ->
            binding.btnChat.isVisible = !id.isNullOrBlank()
        }
        fridgeViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            applyChatUnreadBadge(count)
        }
    }

    private fun applyChatUnreadBadge(count: Int) {
        val badge = chatBadge ?: return
        if (count <= 0) {
            if (chatBadgeAttached) {
                BadgeUtils.detachBadgeDrawable(badge, binding.btnChat)
                chatBadgeAttached = false
            }
            badge.isVisible = false
            return
        }
        badge.number = count
        badge.isVisible = true
        if (!chatBadgeAttached) {
            binding.btnChat.post {
                if (_binding != null) {
                    BadgeUtils.attachBadgeDrawable(badge, binding.btnChat)
                    chatBadgeAttached = true
                }
            }
        }
    }

    private fun observeNotifications() {
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            binding.notificationDot.isVisible = count > 0
        }

        notificationViewModel.incomingNotification.observe(viewLifecycleOwner) { notification ->
            notification ?: return@observe
            showBanner(notification.title, notification.message)
            notificationViewModel.consumeIncoming()
        }

        notificationViewModel.pendingPostId.observe(viewLifecycleOwner) { postId ->
            postId ?: return@observe
            if (currentTabId != R.id.tab_feed) {
                currentTabId = R.id.tab_feed
                updateTabUI(currentTabId)
                showFragmentForTab(currentTabId)
            }
            // FeedFragment observes pendingPostId and scrolls once posts are available
        }
    }

    private fun showBanner(title: String, message: String) {
        binding.bannerTitle.text = title
        binding.bannerMessage.text = message
        binding.notificationBanner.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(binding.notificationBanner, "alpha", 0f, 1f).setDuration(200).start()

        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerHandler.postDelayed(hideBannerRunnable, 3500)
    }

    private fun hideBanner() {
        val animator = ObjectAnimator.ofFloat(binding.notificationBanner, "alpha", 1f, 0f).apply {
            duration = 300
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                binding.notificationBanner.visibility = View.GONE
            }
        })
        animator.start()
    }

    override fun onDestroyView() {
        bannerHandler.removeCallbacks(hideBannerRunnable)
        chatBadge?.let { badge ->
            if (chatBadgeAttached && _binding != null) {
                BadgeUtils.detachBadgeDrawable(badge, binding.btnChat)
            }
        }
        chatBadge = null
        chatBadgeAttached = false
        super.onDestroyView()
        _binding = null
    }
}
