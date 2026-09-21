package com.example.expensetracker.sharedimport

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptImageEnhancerTest {
    @Test
    fun `clips one percent tails when calculating contrast`() {
        val histogram = IntArray(256)
        histogram[10] = 1
        histogram[80] = 97
        histogram[220] = 2

        assertEquals(80 to 220, calculateContrastBounds(histogram, 100))
    }

    @Test
    fun `expands nearly flat images to a usable minimum range`() {
        val histogram = IntArray(256)
        histogram[140] = 100

        assertEquals(124 to 156, calculateContrastBounds(histogram, 100))
    }
}