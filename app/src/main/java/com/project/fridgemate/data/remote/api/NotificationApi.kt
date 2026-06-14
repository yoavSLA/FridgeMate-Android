package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.NotificationsResponse
import com.project.fridgemate.data.remote.dto.NotificationUnreadCountResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(): Response<NotificationsResponse>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<NotificationUnreadCountResponse>

    @PUT("notifications/read-all")
    suspend fun markAllAsRead(): Response<Unit>

    @PUT("notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: String): Response<Unit>
}
