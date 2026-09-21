package com.example.expensetracker.sharedimport

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextLayoutTest {
    @Test
    fun `merges receipt columns into visual rows`() {
        val text = reconstructOcrRows(
            listOf(
                fragment("TOTALE COMPLESSIVO", 20, 200, 260, 230),
                fragment("43,59", 510, 202, 590, 232),
                fragment("DI CUI IVA", 20, 240, 180, 270),
                fragment("0,00", 520, 242, 590, 272),
                fragment("IMPORTO PAGATO", 20, 280, 230, 310),
                fragment("43,59", 510, 282, 590, 312)
            )
        )

        assertEquals(
            "TOTALE COMPLESSIVO 43,59\nDI CUI IVA 0,00\nIMPORTO PAGATO 43,59",
            text
        )
    }

    @Test
    fun `keeps nearby baselines on separate rows`() {
        val text = reconstructOcrRows(
            listOf(
                fragment("SUBTOTALE", 20, 100, 180, 128),
                fragment("37,83", 510, 102, 590, 130),
                fragment("TOTALE COMPLESSIVO", 20, 135, 280, 163),
                fragment("37,83", 510, 137, 590, 165)
            )
        )

        assertEquals("SUBTOTALE 37,83\nTOTALE COMPLESSIVO 37,83", text)
    }

    private fun fragment(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        OcrTextFragment(text, left, top, right, bottom)
}