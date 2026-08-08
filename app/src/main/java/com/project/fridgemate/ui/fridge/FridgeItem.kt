package com.project.fridgemate.ui.fridge

sealed class FridgeItem {
    /** Shows a summary; carries the full list so the banner can open it. */
    data class RunningLow(val items: List<Product>) : FridgeItem()

    data class CategoryHeader(val name: String) : FridgeItem()

    data class Product(
        val id: String,
        val name: String,
        val quantity: String,
        val category: String?,
        val isLowStock: Boolean,
        val ownerId: String?,
        val daysOfSupply: Double? = null,
        val suggestedRestockQuantity: String? = null
    ) : FridgeItem()
}
