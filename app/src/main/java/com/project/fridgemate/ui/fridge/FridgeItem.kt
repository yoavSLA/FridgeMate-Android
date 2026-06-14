package com.project.fridgemate.ui.fridge

sealed class FridgeItem {
    data class LastScanned(val timestamp: String) : FridgeItem()
    data class RunningLow(val ingredients: List<Pair<String, String>>) : FridgeItem()
    data class CategoryHeader(val name: String) : FridgeItem()
    data class Product(val name: String, val quantity: String, val isLowStock: Boolean) : FridgeItem()
}
