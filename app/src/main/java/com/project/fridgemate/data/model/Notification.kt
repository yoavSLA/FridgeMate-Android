
package com.project.fridgemate.data.model

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val relatedId: String? = null  // postId, scanId, etc.
)

enum class NotificationType {
    LIKE,
    COMMENT,
    SCAN_COMPLETE,
    CHAT_MESSAGE,
    SYSTEM
}