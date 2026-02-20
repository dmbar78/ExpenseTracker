package com.example.expensetracker.data.gemini

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiParsingTest {

    private val gson = Gson()

    @Test
    fun testExtractJsonObject_ValidJson() {
        val rawResponse = """
            Here is your transaction:
            ```json
            {
                "expense_type": "expense",
                "account": "Sberbank",
                "amount": { "value": -500.0, "currency": "RUB" },
                "category": "Groceries",
                "date": "2023-10-25"
            }
            ```
        """.trimIndent()

        val jsonString = GeminiApiService.extractJsonObject(rawResponse)
        val extract = gson.fromJson(jsonString, TransactionExtract::class.java)

        assertEquals("expense", extract.expenseType)
        assertEquals("Sberbank", extract.account)
        assertEquals(-500.0, extract.amount?.value)
        assertEquals("RUB", extract.amount?.currency)
        assertEquals("Groceries", extract.category)
        assertEquals("2023-10-25", extract.date)
    }

    @Test
    fun testExtractJsonObject_Transfer() {
        val rawResponse = """
            {
                "expense_type": "transfer",
                "source_account": "Wallet",
                "destination_account": "Bank",
                "source_amount": { "value": -100.0, "currency": "USD" },
                "destination_amount": { "value": 100.0, "currency": "USD" },
                "date": "2023-10-01"
            }
        """.trimIndent()

        val jsonString = GeminiApiService.extractJsonObject(rawResponse)
        val extract = gson.fromJson(jsonString, TransactionExtract::class.java)

        assertEquals("transfer", extract.expenseType)
        assertEquals("Wallet", extract.sourceAccount)
        assertEquals("Bank", extract.destinationAccount)
        assertEquals(-100.0, extract.sourceAmount?.value)
        assertNull(extract.category)
    }

    @Test
    fun testExtractJsonObject_Income() {
        val rawResponse = """
            I found this: {"expense_type": "income", "account": "Revolut", "amount": {"value": 1200.0, "currency": "EUR"}, "category": "Salary"} Have a nice day!
        """.trimIndent()

        val jsonString = GeminiApiService.extractJsonObject(rawResponse)
        val extract = gson.fromJson(jsonString, TransactionExtract::class.java)

        assertEquals("income", extract.expenseType)
        assertEquals("Revolut", extract.account)
        assertEquals(1200.0, extract.amount?.value)
        assertEquals("Salary", extract.category)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testExtractJsonObject_NoJson() {
        GeminiApiService.extractJsonObject("I couldn't find a transaction.")
    }
}
