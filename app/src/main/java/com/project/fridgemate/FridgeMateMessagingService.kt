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
import com.project.fridgemate.data.remote.ApiClient
import com.project.fridgemate.data.repository.UserRepository
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

        message.notification?.let {
            showNotification(it.title, it.body)
        }

        message.data.let {
            handleDataPayload(it)
        }
    }

    private fun sendTokenToServer(token: String) {
        if (!ApiClient.getTokenManager().isLoggedIn) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { UserRepository(applicationContext).registerFcmToken(token) }
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"]

        when (type) {
            "like" -> {
                val postId = data["postId"]
                val userName = data["userName"]
                showNotification("New Like", "$userName liked your post")
            }
            "comment" -> {
                val postId = data["postId"]
                val userName = data["userName"]
                showNotification("New Comment", "$userName commented on your post")
            }
            "scan_complete" -> {
                showNotification("Scan Complete", "Your fridge scan is ready!")
            }
            "chat_message" -> {
                val userName = data["userName"]
                val message = data["message"]
                showNotification("New Message", "$userName: $message")
            }
        }
    }

    private fun showNotification(title: String?, body: String?) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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