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
    fun `parses bunq PDF when OCR splits table headers across lines`() {
        val text = """
            No rights can be derived from this overview. 1/1
            Transaction
            Account holder
            Dmitry Baryshnikov
            Account details
            IBAN: NL35 BUNQ 2046 8972 18
            BIC: BUNQNL2A
            Mastercard
            xxxx xxxx xxxx 0771
            Bank information
            bunq B.V.
            Chamber of Commerce: 54992060
            Basisweg 32
            1043AP Amsterdam
            Netherlands
            Date Interest
            Date
            Counterparty Description Amount
            2026-09-15 2026-09-15 IMBISS SNC IMBISS SNC CORVARA, IT - € 14,30
            Download date: 2026-09-16
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.forLanguageTag("nl-NL"), zoneId = zone)

        assertEquals(BigDecimal("14.30"), result.amount)
        assertEquals(dateMillis(2026, 9, 15), result.dateMillis)
    }

    @Test
    fun `parses Italian weighed item label sum and date`() {
        val text = """
            FAMIGLIA COOPERATIVA CAMPITELLO
            16.09.2026 18:49:40
            ASIAGO MEZZANO DOP
            0,366                 19,50                 7,14
            1 Voce Somma          €                     7,14
            16.09.2026 18:49:40
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.ITALY, zoneId = zone)

        assertEquals(BigDecimal("7.14"), result.amount)
        assertEquals(dateMillis(2026, 9, 16), result.dateMillis)
    }

    @Test
    fun `parses Italian sum when OCR separates label currency and value`() {
        val result = ReceiptTextParser.parseAmount("1 Voce Somma\n€\n7,14")

        assertEquals(BigDecimal("7.14"), result)
    }

    @Test
    fun `parses Italian sum when OCR keeps quantity label and currency together`() {
        val result = ReceiptTextParser.parseAmount("1 Voce Somma €\n7,14")

        assertEquals(BigDecimal("7.14"), result)
    }

    @Test
    fun `parses Italian sum when OCR puts quantity and total on the same row`() {
        val result = ReceiptTextParser.parseAmount("1 Voce Somma € 7,14")

        assertEquals(BigDecimal("7.14"), result)
    }

    @Test
    fun `does not accept quantity as total when OCR loses the monetary value`() {
        val result = ReceiptTextParser.parseAmount("1 Voce Somma €\n16.09.2026 18:49:40")

        assertNull(result)
    }

    @Test
    fun `recovers Italian sum when OCR drops decimal separator`() {
        assertEquals(BigDecimal("7.14"), ReceiptTextParser.parseAmount("1 Voce Somma € 714"))
        assertEquals(BigDecimal("7.14"), ReceiptTextParser.parseAmount("1 Voce Somma € 7 14"))
    }

    @Test
    fun `parses Italian sum when OCR puts amount row before label`() {
        val text = """
            0,366 19,50 7,14
            1Voce Somma €
            16.09.2026 18:49:40
        """.trimIndent()

        assertEquals(BigDecimal("7.14"), ReceiptTextParser.parseAmount(text))
    }

    @Test
    fun `parses Italian receipt total instead of VAT and item prices`() {
        val text = """
            DESCRIZIONE                         IVA        Prezzo(€)
            Caffe macchiato                     10,00%       3,00
            SKIWASSER                           10,00%       3,50
            STRUDEL + GEL                       10,00%       6,00
            TOTALE COMPLESSIVO                              12,50
            di cui IVA                                      1,14
            Pagamento contante                             12,50
            Importo pagato                                12,50
            16-09-2026 19:36
            DOCUMENTO N. 1506-0068
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.ITALY, zoneId = zone)

        assertEquals(BigDecimal("12.50"), result.amount)
        assertEquals(dateMillis(2026, 9, 16), result.dateMillis)
    }

    @Test
    fun `parses Italian supermarket total around discounts and VAT`() {
        val text = """
            50% SCONTO % ART.       -0,90
            YOGURT NATURALE CASE     3,78 L
            SUBTOTALE               37,83
            Ventilazione IVA        -3,32 L
            TOTALE COMPLESSIVO      37,83
            DI CUI IVA               0,00
            PAGAMENTO ELETTRONICO   37,83
            IMPORTO PAGATO          37,83
            16/09/26 19:02
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.ITALY, zoneId = zone)

        assertEquals(BigDecimal("37.83"), result.amount)
        assertEquals(dateMillis(2026, 9, 16), result.dateMillis)
    }

    @Test
    fun `parses fuel terminal amount and US ordered date`() {
        val text = """
            Date 09/14/2026 - 02:10:19
            Product: Super SP
            Price: 2,119 €/L
            Volume: 47,19 L
            AMOUNT: 100,00 €
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.US, zoneId = zone)

        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals(dateMillis(2026, 9, 14), result.dateMillis)
    }

    @Test
    fun `parses German fuel sum and labeled date`() {
        val text = """
            SuperPlus
            57,38 Liter
            2,229 EUR / Liter
            ZwSumme EUR 127,90
            19,00 % MWST. A EUR 20,42
            Summe EUR 127,90
            Mastercard EUR 127,90
            Datum 13.09.26 11:58 Uhr
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("127.90"), result.amount)
        assertEquals(dateMillis(2026, 9, 13), result.dateMillis)
    }

    @Test
    fun `parses Italian restaurant receipts with dash and slash dates`() {
        val cases = listOf(
            Triple("TOTALE COMPLESSIVO 20,60\ndi cui IVA 1,87\nImporto pagato 20,60\n17-09-2026 15:15", "20.60", dateMillis(2026, 9, 17)),
            Triple("TOTALE COMPLESSIVO 64,00\ndi cui IVA 5,82\nImporto pagato 64,00\n18-09-2026 21:35", "64.00", dateMillis(2026, 9, 18)),
            Triple("TOTALE COMPLESSIVO 29,00\nDI CUI IVA 2,64\nIMPORTO PAGATO 29,00\n19/09/26 14:17", "29.00", dateMillis(2026, 9, 19)),
            Triple("TOTALE COMPLESSIVO 10,20\ndi cui IVA 1,28\nImporto pagato 10,20\n19-09-2026 17:52", "10.20", dateMillis(2026, 9, 19))
        )

        cases.forEach { (text, amount, date) ->
            val result = ReceiptTextParser.parse(text, Locale.ITALY, zoneId = zone)
            assertEquals(BigDecimal(amount), result.amount)
            assertEquals(date, result.dateMillis)
        }
    }

    @Test
    fun `parses German Gesamtbetrag receipt and Date Time footer`() {
        val text = """
            Beleg-Nr. 3188/019/00002 19.09.2026 21:21
            Super E10 66,84 EUR
            Gesamtbetrag 66,84 EUR
            Mastercard 66,84 EUR
            Date/Time 19.09.2026 21:21:07
            Amount EUR 66.84
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("66.84"), result.amount)
        assertEquals(dateMillis(2026, 9, 19), result.dateMillis)
    }

    @Test
    fun `parses Austrian Bruttobetrag receipt`() {
        val text = """
            Rechnung
            ausgestellt am: 19.09.2026 19:06:43
            Betrag in EUR 12,50
            Bruttobetrag: 12,50
            A=20% USt. 2,08
            Unbare Zahlung: 12,50 EUR
            BETRAG (AMOUNT): 12,50 EUR
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.GERMANY, zoneId = zone)

        assertEquals(BigDecimal("12.50"), result.amount)
        assertEquals(dateMillis(2026, 9, 19), result.dateMillis)
    }

    @Test
    fun `parses Austrian Bruttobetrag when OCR separates its value`() {
        val result = ReceiptTextParser.parseAmount("Betrag in EUR 12,50 A\nBruttobetrag:\n12,50\nA=20% USt. 2,08")

        assertEquals(BigDecimal("12.50"), result)
    }

    @Test
    fun `parses Dutch subtotal when payment footer total is missed`() {
        val text = """
            Kruidvat
            Aant. Artikel Bedrag
            BLOEDDRUKMETER 27,99
            OMEGA-3 1000MG 11,99
            MULTI A-Z MAN 5,99
            3 SUBTOTAAL 45,97
            MASTERCARD DEBIT 45,97
            BTW 9% EUR42,17 EUR3,80
            PAYMENT
            11/09/2026 17:47
        """.trimIndent()

        val result = ReceiptTextParser.parse(text, Locale.forLanguageTag("nl-NL"), zoneId = zone)

        assertEquals(BigDecimal("45.97"), result.amount)
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