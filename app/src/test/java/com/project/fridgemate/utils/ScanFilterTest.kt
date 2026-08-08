package com.project.fridgemate.utils

import com.project.fridgemate.data.remote.dto.DetectedItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilterTest {

    @Test
    fun `isGeneric returns true for banned terms`() {
        assertTrue(ScanFilter.isGeneric("vegetable"))
        assertTrue(ScanFilter.isGeneric("small sauce bottle"))
        assertTrue(ScanFilter.isGeneric("liquid container"))
        assertTrue(ScanFilter.isGeneric("food container"))
        assertTrue(ScanFilter.isGeneric("tupperware"))
        assertTrue(ScanFilter.isGeneric("leftovers"))
    }

    @Test
    fun `isGeneric returns true for generic container terms`() {
        assertTrue(ScanFilter.isGeneric("bottle"))
        assertTrue(ScanFilter.isGeneric("jar"))
        assertTrue(ScanFilter.isGeneric("container"))
        assertTrue(ScanFilter.isGeneric("unknown bottle"))
    }

    @Test
    fun `isGeneric returns false for specific items`() {
        assertFalse(ScanFilter.isGeneric("Milk"))
        assertFalse(ScanFilter.isGeneric("Carrot"))
        assertFalse(ScanFilter.isGeneric("Ketchup bottle"))
        assertFalse(ScanFilter.isGeneric("Cheddar Cheese"))
    }

    @Test
    fun `filterByItemName removes generic items`() {
        val items = listOf(
            DetectedItemDto("Milk", "1L"),
            DetectedItemDto("Vegetable", "3 units"),
            DetectedItemDto("Apple", "5"),
            DetectedItemDto("Small sauce bottle", "1")
        )

        val filtered = ScanFilter.filterByItemName(items) { it.name }

        assertEquals(2, filtered.size)
        assertEquals("Milk", filtered[0].name)
        assertEquals("Apple", filtered[1].name)
    }

    @Test
    fun `isGeneric is case insensitive and ignores whitespace`() {
        assertTrue(ScanFilter.isGeneric("  VEGETABLE  "))
        assertTrue(ScanFilter.isGeneric("Food Container"))
    }
}
