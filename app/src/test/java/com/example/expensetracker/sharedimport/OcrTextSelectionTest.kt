package com.example.expensetracker.sharedimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class OcrTextSelectionTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 9, 21).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `requests enhanced OCR when amount or date is missing`() {
        assertTrue(shouldTryEnhancedOcr("TOTALE", Locale.ITALY, now, zone))
        assertTrue(shouldTryEnhancedOcr("TOTALE 12,50", Locale.ITALY, now, zone))
        assertTrue(shouldTryEnhancedOcr("19/09/2026", Locale.ITALY, now, zone))
        assertFalse(shouldTryEnhancedOcr("TOTALE 12,50\n19/09/2026", Locale.ITALY, now, zone))
    }

    @Test
    fun `selects enhanced text only when it parses more fields`() {
        val original = "TOTALE\n19/09/2026"
        val enhanced = "TOTALE 12,50\n19/09/2026"

        assertEquals(enhanced, selectBetterOcrText(original, enhanced, Locale.ITALY, now, zone))
        assertEquals(enhanced, selectBetterOcrText(enhanced, "noise", Locale.ITALY, now, zone))
    }
}