package com.project.fridgemate.data.model

import com.project.fridgemate.data.remote.dto.NotificationDto

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val relatedId: String? = null
)

enum class NotificationType {
    POST_LIKE,
    POST_COMMENT,
    FOLLOW,
    CHAT_MESSAGE,
    FRIDGE_INVITE,
    EXPIRING_ITEM,
    SCAN_COMPLETE,
    SYSTEM
}

fun NotificationDto.toNotification(): Notification {
    val ts = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(createdAt)?.time ?: System.currentTimeMillis()
    }.getOrDefault(System.currentTimeMillis())

    val notifType = when (type) {
        "POST_LIKE" -> NotificationType.POST_LIKE
        "POST_COMMENT" -> NotificationType.POST_COMMENT
        "FOLLOW" -> NotificationType.FOLLOW
        "CHAT_MESSAGE" -> NotificationType.CHAT_MESSAGE
        "FRIDGE_INVITE" -> NotificationType.FRIDGE_INVITE
        "EXPIRING_ITEM" -> NotificationType.EXPIRING_ITEM
        "SCAN_COMPLETE" -> NotificationType.SCAN_COMPLETE
        else -> NotificationType.SYSTEM
    }

    val relatedId = when (type) {
        "POST_LIKE", "POST_COMMENT" -> metadata?.get("postId") as? String
        "FOLLOW" -> metadata?.get("followerId") as? String
        else -> null
    }

    return Notification(
        id = id,
        type = notifType,
        title = title,
        message = message,
        timestamp = ts,
        isRead = isRead,
        relatedId = relatedId
    )
}
