package com.project.fridgemate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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

class FridgeMateMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        private const val CHANNEL_ID = "fridgemate_notifications"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token: $token")

        // TODO: send to server token
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message received from: ${message.from}")

        if (FridgeMateApp.isForeground) return

        message.notification?.let {
            showNotification(it.title, it.body, message.data)
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

        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Only make clickable if it's not a SCAN_COMPLETE, or if it IS a SCAN_COMPLETE and we have the summary info
        val isScanComplete = type == "SCAN_COMPLETE"
        val hasSummaryInfo = storage.getLastScanSummary() != null && storage.getLastScanCreatedAt() != null
        
        if (!isScanComplete || hasSummaryInfo) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(requestCode, notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FridgeMate Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for likes, comments, scans, and messages"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}