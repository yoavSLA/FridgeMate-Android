package com.project.fridgemate.data.remote.dto

data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String,
    val metadata: Map<String, Any>? = null
)

data class NotificationsResponse(
    val notifications: List<NotificationDto>,
    val page: Int = 1,
    val totalPages: Int = 1,
    val total: Int = 0
)

data class NotificationUnreadCountResponse(val unreadCount: Int)
