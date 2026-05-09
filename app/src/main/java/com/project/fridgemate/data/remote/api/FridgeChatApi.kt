package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ChatMessagesResponse
import com.project.fridgemate.data.remote.dto.UnreadCountResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FridgeChatApi {

    @GET("fridges/{fridgeId}/chat/messages")
    suspend fun getMessages(
        @Path("fridgeId") fridgeId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<ChatMessagesResponse>

    @POST("fridges/{fridgeId}/chat/read")
    suspend fun markRead(@Path("fridgeId") fridgeId: String): Response<Unit>

    @GET("fridges/{fridgeId}/chat/unread-count")
    suspend fun getUnreadCount(@Path("fridgeId") fridgeId: String): Response<UnreadCountResponse>
}
