package com.example.expensetracker.sharedimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class ReceiptTextParserTest {
    private val zone = ZoneId.of("UTC")

    @Test
    fun `prefers grand total over subtotal and tax`() {
        val result = ReceiptTextParser.parse("Subtotal 10.00\nTax 2.00\nGrand Total 12.00", zoneId = zone)

        assertEquals(BigDecimal("12.00"), result.amount)
    }

    @Test
    fun `parses european labeled total`() {
        val result = ReceiptTextParser.parse("TOTAL EUR 1.234,56", zoneId = zone)

        assertEquals(BigDecimal("1234.56"), result.amount)
    }

    @Test
    fun `leaves ambiguous equally ranked totals blank`() {
        assertNull(ReceiptTextParser.parseAmount("Total 10.00\nTotal 11.00"))
    }

    @Test
    fun `parses numeric date using device locale order`() {
        val us = ReceiptTextParser.parse("Date: 03/04/2026", Locale.US, zoneId = zone)
        val uk = ReceiptTextParser.parse("Date: 03/04/2026", Locale.UK, zoneId = zone)

        assertEquals(dateMillis(2026, 3, 4), us.dateMillis)
        assertEquals(dateMillis(2026, 4, 3), uk.dateMillis)
    }

    @Test
    fun `prefers labeled date and rejects invalid dates`() {
        val result = ReceiptTextParser.parse("Printed 2026-02-30\nTransaction Date: 2026-02-28", zoneId = zone)

        assertEquals(dateMillis(2026, 2, 28), result.dateMillis)
    }

    @Test
    fun `unambiguous numeric date overrides locale order`() {
        val result = ReceiptTextParser.parse("Date: 31/01/2026", Locale.US, zoneId = zone)

        assertEquals(dateMillis(2026, 1, 31), result.dateMillis)
    }

    @Test
    fun `parses english textual date with comma`() {
        val result = ReceiptTextParser.parse("Transaction Date: January 15, 2026", Locale.US, zoneId = zone)

        assertEquals(dateMillis(2026, 1, 15), result.dateMillis)
    }

    @Test
    fun `falls back to start of today and blank amount`() {
        val now = dateMillis(2026, 9, 12) + 43_200_000
        val result = ReceiptTextParser.parse("Thank you", nowMillis = now, zoneId = zone)

        assertNull(result.amount)
        assertEquals(dateMillis(2026, 9, 12), result.dateMillis)
    }

    @Test
    fun `parses ABN AMRO amount and execution date on following lines`() {
        val text = """
            Execution
            07 September 2026 04:04
            Amount
            € -80,54
            CounterAccount
            Allianz Direct Vers.
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.forLanguageTag("nl-NL"), zoneId = zone)

        assertEquals(BigDecimal("80.54"), result.amount)
        assertEquals(dateMillis(2026, 9, 7), result.dateMillis)
    }

    @Test
    fun `parses bunq table date and amount under headers`() {
        val text = """
            Date                 Interest Date        Counterparty              Description                         Amount
            2026-09-11           2026-09-11           BCK*PLUS s-Gravenland     BCK*PLUS s-Gravenland CAPELLE      - € 44,63
            Download date: 2026-09-12
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.forLanguageTag("nl-NL"), zoneId = zone)

        assertEquals(BigDecimal("44.63"), result.amount)
        assertEquals(dateMillis(2026, 9, 11), result.dateMillis)
    }

    @Test
    fun `parses amount when OCR puts currency on its own line`() {
        val result = ReceiptTextParser.parse("Amount\n€\n-80,54", zoneId = zone)

        assertEquals(BigDecimal("80.54"), result.amount)
    }

    @Test
    fun `parses SBB order overview instead of passenger birth date`() {
        val text = """
            Ihre Reiseübersicht: Zürich HB - Köln Hbf
            Reisende
            DMITRY BARYSHNIKOV                 05.06.1978
            Ihre Verbindung am Fr., 28.08.2026
            Zürich HB                         Fr., 28.08.2026 ab 15:59 Uhr
            Köln Hbf                          Fr., 28.08.2026 an 21:05 Uhr
            Gesamtpreis der Bestellung        CHF 211.10
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("211.10"), result.amount)
        assertEquals(dateMillis(2026, 8, 28), result.dateMillis)
    }

    @Test
    fun `parses SBB ticket fare among reservation and birth date values`() {
        val text = """
            Gültigkeit: 28.08.26 - 30.08.26
            BARYSHNIKOV DMITRY                 05.06.78
            Flexpreis Europa                   CHF 96.00
            Zusammenfassung:
            Billett                            CHF 96.00
            Reservierung(en)                   CHF 6.00
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("96.00"), result.amount)
        assertEquals(dateMillis(2026, 8, 28), result.dateMillis)
    }

    @Test
    fun `parses hotel invoice paid total and invoice date`() {
        val text = """
            Invoice date : Friday, 28 August 2026
            Due date invoice: Friday, 28 August 2026
            Room 217 - DBL SP - Online Rate RO (1 night)  € 81.00
            City Tax (28/08/2026)                         € 4.00
            Total excluding VAT                           € 76.32
            VAT 12.00%                                    € 6.68
            Total including VAT                           € 85.00
            Already paid                                  € 85.00
            Outstanding balance                           € 0.00
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.UK, zoneId = zone)

        assertEquals(BigDecimal("85.00"), result.amount)
        assertEquals(dateMillis(2026, 8, 28), result.dateMillis)
    }

    @Test
    fun `parses ZVV ticket validity and currency-only fare`() {
        val text = """
            Einzelbillett
            Gültig:                  28.08.2026 14:56 -
                                     28.08.2026 15:56
            Zonen 110 121
            2. Kl.                   Vollpreis
            CHF 7.20
            Artikel-Nr.: 52509
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("7.20"), result.amount)
        assertEquals(dateMillis(2026, 8, 28), result.dateMillis)
    }

    @Test
    fun `parses SBB reservation fare and infers year for travel date`() {
        val now = dateMillis(2026, 9, 12) + 43_200_000
        val text = """
            Ihre Reservierung(en)
            28.08   15:59   ZUERICH HB   ->   21:05 KOELN HBF
            Zusammenfassung:
            Reservierung(en)            CHF 6.00
            Geburtsdatum
            05/06/1978
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, nowMillis = now, zoneId = zone)

        assertEquals(BigDecimal("6.00"), result.amount)
        assertEquals(dateMillis(2026, 8, 28), result.dateMillis)
    }

    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()
}