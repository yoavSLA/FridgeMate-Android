package com.project.fridgemate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationHelper {
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                FridgeMateMessagingService.CHANNEL_ID,
                "FridgeMate Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for likes, comments, and messages"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
