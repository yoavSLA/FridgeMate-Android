package com.project.fridgemate.utils

import com.project.fridgemate.data.remote.dto.DetectedItemDto

/**
 * Filters out generic or unrecognizable items from scan results to ensure
 * high-quality inventory data for recipe generation.
 */
object ScanFilter {

    private val BANNED_TERMS = setOf(
        "leftovers",
        "food mix",
        "unknown container",
        "tupperware",
        "dish",
        "bowl",
        "plate",
        "leftover food",
        "meal",
        "assorted food",
        "small sauce bottle",
        "vegetable",
        "liquid container",
        "food container",
        "container",
        "bottle",
        "jar",
        "box",
        "bag",
        "packet",
        "unrecognizable",
        "ambiguous",
        "generic item",
        "unknown",
        "food"
    )

    /**
     * Returns true if the item name is considered generic or banned.
     */
    fun isGeneric(itemName: String): Boolean {
        val normalized = itemName.lowercase().trim()
        
        // Direct match
        if (BANNED_TERMS.contains(normalized)) return true
        
        // Partial match for generic container terms
        val genericContainerTerms = listOf("container", "bottle", "jar", "box", "bag")
        if (genericContainerTerms.any { normalized == it || normalized.contains("unknown $it") }) {
            return true
        }
        
        return false
    }

    /**
     * Filters a list of items by name, removing any that are generic.
     * Works with any list where the items have a 'name' property.
     */
    fun <T> filterByItemName(items: List<T>, nameSelector: (T) -> String): List<T> {
        return items.filterNot { isGeneric(nameSelector(it)) }
    }
}
