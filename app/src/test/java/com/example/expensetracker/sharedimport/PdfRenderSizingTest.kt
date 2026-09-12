package com.example.expensetracker.sharedimport

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfRenderSizingTest {
    @Test
    fun `upscales a standard portrait PDF page for OCR`() {
        assertEquals(PdfRenderSize(1447, 2048), calculatePdfRenderSize(595, 842))
    }

    @Test
    fun `upscales a landscape bank statement for OCR`() {
        assertEquals(PdfRenderSize(2048, 1582), calculatePdfRenderSize(792, 612))
    }

    @Test
    fun `downscales oversized pages to the same memory bound`() {
        assertEquals(PdfRenderSize(1536, 2048), calculatePdfRenderSize(3000, 4000))
    }
}