package com.project.fridgemate.data.remote.dto

data class DetectedItemDto(
    val name: String,
    val quantity: String
)

data class InventoryChangeDto(
    val name: String,
    val quantity: String
)

data class UpdatedItemDto(
    val name: String,
    val oldQuantity: String,
    val newQuantity: String
)

data class ScanChangesDto(
    val added: List<InventoryChangeDto>,
    val updated: List<UpdatedItemDto>,
    val removed: List<InventoryChangeDto>
)

data class ScanDto(
    val id: String,
    val fridgeId: String,
    val userId: String,
    val status: String,
    val detectedItems: List<DetectedItemDto>,
    val addedItemIds: List<String>,
    val error: String?,
    val createdAt: String,
    val updatedAt: String,
    val changes: ScanChangesDto? = null
)
