package com.project.fridgemate

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project.fridgemate.data.local.ScanSummaryStorage
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.socket.SocketManager
import com.project.fridgemate.data.repository.UserRepository
import com.project.fridgemate.databinding.ActivityMainBinding
import com.project.fridgemate.ui.notifications.NotificationViewModel
import com.project.fridgemate.ui.settings.ScanSummaryDialog
import com.project.fridgemate.utils.ToastHelper
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository

    private val notificationViewModel: NotificationViewModel by viewModels()

    private val bannerHandler = Handler(Looper.getMainLooper())
    private val hideBannerRunnable = Runnable { hideBanner() }
    private var bannerAnimator: ObjectAnimator? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeRequestNotificationPermission()
        handleNotificationIntent(intent)
        observeNotifications()

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        userRepository = UserRepository(applicationContext)
        userRepository.syncFcmToken()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (ApiClient.getTokenManager().isLoggedIn) {
            SocketManager.connect()
        }
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                notificationViewModel.incomingNotification.collect { notification ->
                    showBanner(notification)
                }
            }
        }

        notificationViewModel.pendingScanSummaryOpen.observe(this) { pending ->
            pending ?: return@observe
            showScanSummaryPopup()
            notificationViewModel.consumePendingScanSummary()
        }
    }

    private fun showBanner(notification: Notification) {
        val banner = binding.notificationBanner
        binding.bannerTitle.text = notification.title
        binding.bannerMessage.text = notification.message
        
        bannerHandler.removeCallbacks(hideBannerRunnable)
        bannerAnimator?.cancel()
        
        banner.translationY = 0f
        banner.alpha = 1f
        banner.visibility = View.VISIBLE
        
        banner.setOnClickListener {
            bannerHandler.removeCallbacks(hideBannerRunnable)
            hideBanner()
            notificationViewModel.handleNotificationClick(notification)
        }
        attachSwipeToDismiss(banner)
        
        bannerAnimator = ObjectAnimator.ofFloat(banner, "alpha", 0f, 1f).apply {
            duration = 200
            start()
        }

        bannerHandler.postDelayed(hideBannerRunnable, 4000)
    }

    private fun hideBanner() {
        val bannerView = binding.notificationBanner
        bannerAnimator?.cancel()
        
        bannerAnimator = ObjectAnimator.ofFloat(bannerView, "alpha", 1f, 0f).apply {
            duration = 300
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (bannerView.alpha == 0f) {
                        bannerView.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSwipeToDismiss(view: View) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downRawY = 0f
        var dragging = false
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    dragging = false
                    moved = false
                    v.animate().cancel()
                    bannerHandler.removeCallbacks(hideBannerRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (abs(dy) > slop) moved = true
                    if (dy < -slop) {
                        dragging = true
                        v.translationY = dy
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val threshold = v.height * 0.4f
                        if (-v.translationY > threshold) {
                            v.animate()
                                .translationY(-v.height.toFloat())
                                .alpha(0f)
                                .setDuration(180)
                                .withEndAction {
                                    binding.notificationBanner.visibility = View.GONE
                                    v.translationY = 0f
                                    v.alpha = 1f
                                }
                                .start()
                        } else {
                            v.animate().translationY(0f).setDuration(180).start()
                            bannerHandler.postDelayed(hideBannerRunnable, 3500)
                        }
                    } else if (!moved) {
                        v.performClick()
                    } else {
                        bannerHandler.postDelayed(hideBannerRunnable, 3500)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        v.animate().translationY(0f).setDuration(180).start()
                    }
                    bannerHandler.postDelayed(hideBannerRunnable, 3500)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        bannerHandler.removeCallbacks(hideBannerRunnable)
        super.onDestroy()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val type = intent?.getStringExtra("type") ?: return
        if (!ApiClient.getTokenManager().isLoggedIn) return

        val metadataJson = intent.getStringExtra("metadata")
        val metadata = runCatching {
            metadataJson?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        }.getOrNull()

        when (type) {
            "CHAT_MESSAGE" -> {
                val fridgeId = metadata?.optString("fridgeId")?.takeIf { it.isNotBlank() } ?: return
                val fridgeName = metadata.optString("fridgeName", "")
                notificationViewModel.requestNavToFridgeChat(fridgeId, fridgeName)
            }
            "SCAN_COMPLETE" -> {
                showScanSummaryPopup()
            }
        }

        intent.removeExtra("type")
        intent.removeExtra("metadata")
        intent.removeExtra("notificationId")
    }

    private fun showScanSummaryPopup() {
        val storage = ScanSummaryStorage(this)
        val summary = storage.getLastScanSummary()
        val createdAt = storage.getLastScanCreatedAt()

        if ((summary != null) && (createdAt != null)) {
            ScanSummaryDialog.newInstance(summary, createdAt)
                .show(supportFragmentManager, ScanSummaryDialog.TAG)
        } else {
            ToastHelper.showToast(this, getString(R.string.error_scan_summary_not_found))
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}