package com.project.fridgemate

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.project.fridgemate.data.local.ScanSummaryStorage
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.remote.dto.ScanChangesDto
import com.project.fridgemate.data.repository.UserRepository
import com.google.gson.Gson
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class FridgeMateMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        const val CHANNEL_ID = "fridgemate_notifications"
        private val notificationCounter = AtomicInteger((System.currentTimeMillis() % 100000).toInt())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")

        // TODO: send to server token
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message received: ${message.data}")

        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]

        if (title != null || body != null) {
            showNotification(title, body, message.data)
        }
    }

    private fun sendTokenToServer(token: String) {
        if (!ApiClient.getTokenManager().isLoggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { UserRepository(applicationContext).registerFcmToken(token) }
        }
    }

    private fun showNotification(
        title: String?,
        body: String?,
        data: Map<String, String> = emptyMap()
    ) {
        createNotificationChannel()

        val type = data["type"]
        val storage = ScanSummaryStorage(this)
        
        // If it's a scan notification, try to save the summary if present in data
        if (type == "SCAN_COMPLETE") {
            Log.d(TAG, "Scan complete notification received. Data: $data")
            data["metadata"]?.let { metadataJson ->
                try {
                    val metadata = JSONObject(metadataJson)
                    val createdAt = metadata.optString("createdAt")
                    if (metadata.has("changes")) {
                        val changes = try {
                            val obj = metadata.optJSONObject("changes")
                            if (obj != null) {
                                Gson().fromJson(obj.toString(), ScanChangesDto::class.java)
                            } else {
                                val str = metadata.optString("changes")
                                Gson().fromJson(str, ScanChangesDto::class.java)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse changes from metadata", e)
                            null
                        }

                        if (changes != null && createdAt.isNotBlank()) {
                            storage.saveLastScanSummary(changes, createdAt)
                            Log.d(TAG, "Successfully saved scan summary from FCM")
                        }
                    } else {
                        Log.w(TAG, "Scan complete notification missing 'changes' in metadata.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse scan summary from FCM data", e)
                }
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data.forEach { (k, v) -> putExtra(k, v) }
        }

        val notificationId = notificationCounter.incrementAndGet()
        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setNumber(1)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            // Use just the ID without the tag to ensure it's treated as a fresh notification
            // but with a unique ID every time.
            notificationManager.notify(notificationId, notificationBuilder.build())
        }
    }

    private fun createNotificationChannel() {
        // Now handled in FridgeMateApp or lazily if needed
        NotificationHelper.createNotificationChannel(this)
    }
}