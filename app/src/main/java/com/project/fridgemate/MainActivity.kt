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
import android.util.Log
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
import com.google.firebase.messaging.FirebaseMessaging
import com.project.fridgemate.data.local.ScanSummaryStorage
import com.project.fridgemate.data.model.Notification
import com.project.fridgemate.data.remote.ApiClient
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
    ) { granted ->
        Log.d("Permissions", "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeRequestNotificationPermission()
        handleNotificationIntent(intent)
        observeNotifications()

        // Ensure status bar icons are white and navigation bar icons are dark
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = true

        // Handle system bars insets: only pad the top for the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = systemBars.top
            )
            insets
        }

        userRepository = UserRepository(applicationContext)

        // Get FCM Token and send to server
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_TOKEN", "Device FCM Token: $token")

                // Send to server
                lifecycleScope.launch {
                    if (ApiClient.getTokenManager().isLoggedIn) {
                        val result = userRepository.registerFcmToken(token)
                        if (result.isSuccess) {
                            Log.d("FCM_TOKEN", "Token registered successfully")
                        } else {
                            Log.e("FCM_TOKEN", "Failed to register token: ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        Log.d("FCM_TOKEN", "User not logged in, skipping FCM registration for now")
                    }
                }
            } else {
                Log.e("FCM_TOKEN", "Failed to get FCM token: ${task.exception?.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
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
        Log.d("NotificationHandling", "Handling notification of type: $type")
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
                Log.d("NotificationHandling", "Showing scan summary popup")
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

        Log.d("NotificationHandling", "Summary present: ${summary != null}, CreatedAt present: ${createdAt != null}")

        if (summary != null && createdAt != null) {
            ScanSummaryDialog.newInstance(summary, createdAt)
                .show(supportFragmentManager, ScanSummaryDialog.TAG)
        } else {
            Log.w("NotificationHandling", "Cannot show popup: missing summary or createdAt in storage")
            ToastHelper.showToast(this, "Scan summary not found")
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