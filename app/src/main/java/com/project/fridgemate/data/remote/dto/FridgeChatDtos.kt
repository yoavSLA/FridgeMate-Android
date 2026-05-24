package com.project.fridgemate.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatMessageSenderDto(
    @SerializedName(value = "_id", alternate = ["id"]) val id: String,
    val displayName: String?,
    val profileImage: String?
)

data class ChatMessageDto(
    @SerializedName(value = "_id", alternate = ["id"]) val id: String,
    val sender: ChatMessageSenderDto?,
    val content: String,
    val createdAt: String,
    val type: String? = null,
    val payload: ChatMessagePayloadDto? = null,
)

// Polymorphic payload — only one shape today (recipe), more later if needed.
// All fields nullable; the consumer reads them based on the parent message's `type`.
data class ChatMessagePayloadDto(
    val recipeId: String? = null,
    val title: String? = null,
    val imageUrl: String? = null,
    val cookingTime: String? = null,
    val difficulty: String? = null,
)

data class ChatMessagesResponse(
    val items: List<ChatMessageDto>,
    val hasMore: Boolean
)

data class UnreadCountResponse(
    val unreadCount: Int
)
