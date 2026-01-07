package com.example.expensetracker.util

import com.example.expensetracker.viewmodel.ParsedExpense
import com.example.expensetracker.viewmodel.ParsedTransfer
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Calendar

/**
 * JVM unit tests for VoiceCommandParser.
 * Tests parsing logic for expenses, transfers, dates, and money amounts.
 */
class VoiceCommandParserTest {

    companion object {
        // Use a fixed year for deterministic date tests
        private const val TEST_YEAR = 2026
        private val DEFAULT_MILLIS = System.currentTimeMillis()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Expense Parsing Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseExpense - basic expense command`() {
        val result = VoiceCommandParser.parseExpense("Expense from test1 20 category default")
        
        assertNotNull(result)
        assertEquals("test1", result!!.accountName)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertEquals("default", result.categoryName)
        assertEquals("Expense", result.type)
    }

    @Test
    fun `parseExpense - income command`() {
        val result = VoiceCommandParser.parseExpense("Income to Test1 20 category default")
        
        assertNotNull(result)
        assertEquals("Test1", result!!.accountName)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertEquals("default", result.categoryName)
        assertEquals("Income", result.type)
    }

    @Test
    fun `parseExpense - with decimal amount`() {
        val result = VoiceCommandParser.parseExpense("Expense from account 123.45 category food")
        
        assertNotNull(result)
        assertEquals(BigDecimal("123.45"), result!!.amount)
    }

    @Test
    fun `parseExpense - with EU decimal format`() {
        val result = VoiceCommandParser.parseExpense("Expense from account 123,45 category food")
        
        assertNotNull(result)
        assertEquals(BigDecimal("123.45"), result!!.amount)
    }

    @Test
    fun `parseExpense - case insensitive keywords`() {
        val result = VoiceCommandParser.parseExpense("EXPENSE FROM Account 50 CATEGORY Food")
        
        assertNotNull(result)
        assertEquals("Account", result!!.accountName)
        assertEquals("Food", result.categoryName)
    }

    @Test
    fun `parseExpense - missing category keyword returns null`() {
        val result = VoiceCommandParser.parseExpense("Expense from account 20")
        assertNull(result)
    }

    @Test
    fun `parseExpense - missing amount returns null`() {
        val result = VoiceCommandParser.parseExpense("Expense from account category food")
        assertNull(result)
    }

    @Test
    fun `parseExpense - unrecognized format returns null`() {
        val result = VoiceCommandParser.parseExpense("Buy something from the store")
        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Transfer Parsing Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTransfer - basic transfer command`() {
        val result = VoiceCommandParser.parseTransfer("transfer from test1 to test2 20")
        
        assertNotNull(result)
        assertEquals("test1", result!!.sourceAccountName)
        assertEquals("test2", result.destAccountName)
        assertEquals(BigDecimal("20.00"), result.amount)
    }

    @Test
    fun `parseTransfer - with decimal amount`() {
        val result = VoiceCommandParser.parseTransfer("Transfer from savings to checking 150.50")
        
        assertNotNull(result)
        assertEquals(BigDecimal("150.50"), result!!.amount)
    }

    @Test
    fun `parseTransfer - case insensitive`() {
        val result = VoiceCommandParser.parseTransfer("TRANSFER FROM Account1 TO Account2 100")
        
        assertNotNull(result)
        assertEquals("Account1", result!!.sourceAccountName)
        assertEquals("Account2", result.destAccountName)
    }

    @Test
    fun `parseTransfer - missing to keyword returns null`() {
        val result = VoiceCommandParser.parseTransfer("transfer from account1 account2 100")
        assertNull(result)
    }

    @Test
    fun `parseTransfer - missing amount returns null`() {
        val result = VoiceCommandParser.parseTransfer("transfer from accountA to accountB")
        assertNull(result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Date Parsing Tests - Pattern 1: <month> <day>
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTrailingSpokenDate - January 1`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "default January 1",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("default", text)
        assertDateEquals(1, 0, TEST_YEAR, millis) // month is 0-indexed
    }

    @Test
    fun `parseTrailingSpokenDate - January 1st`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "category January 1st",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("category", text)
        assertDateEquals(1, 0, TEST_YEAR, millis)
    }

    @Test
    fun `parseTrailingSpokenDate - January First (ordinal word)`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "test January First",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("test", text)
        assertDateEquals(1, 0, TEST_YEAR, millis)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Date Parsing Tests - Pattern 2: <day> of <month>
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTrailingSpokenDate - 1st of January`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "income 1st of January",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("income", text)
        assertDateEquals(1, 0, TEST_YEAR, millis)
    }

    @Test
    fun `parseTrailingSpokenDate - First of January`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "category First of January",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("category", text)
        assertDateEquals(1, 0, TEST_YEAR, millis)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Date Parsing Tests - Pattern 3: <day> <month>
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTrailingSpokenDate - 15 February`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "food 15 February",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("food", text)
        assertDateEquals(15, 1, TEST_YEAR, millis)
    }

    @Test
    fun `parseTrailingSpokenDate - 15th February`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "groceries 15th February",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("groceries", text)
        assertDateEquals(15, 1, TEST_YEAR, millis)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Date Parsing Tests - Special cases
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTrailingSpokenDate - no date returns original text and default`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "just some text",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("just some text", text)
        assertEquals(DEFAULT_MILLIS, millis)
    }

    @Test
    fun `parseTrailingSpokenDate - January One (cardinal number)`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "transfer January One",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("transfer", text)
        assertDateEquals(1, 0, TEST_YEAR, millis)
    }

    @Test
    fun `parseTrailingSpokenDate - December 31st`() {
        val (text, millis) = VoiceCommandParser.parseTrailingSpokenDate(
            "new year December 31st",
            DEFAULT_MILLIS,
            TEST_YEAR
        )
        
        assertEquals("new year", text)
        assertDateEquals(31, 11, TEST_YEAR, millis) // December is month 11
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Money Parsing Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseMoneyAmount - integer`() {
        assertEquals(BigDecimal("100.00"), VoiceCommandParser.parseMoneyAmount("100"))
    }

    @Test
    fun `parseMoneyAmount - US decimal format`() {
        assertEquals(BigDecimal("1234.56"), VoiceCommandParser.parseMoneyAmount("1234.56"))
    }

    @Test
    fun `parseMoneyAmount - EU decimal format`() {
        assertEquals(BigDecimal("1234.56"), VoiceCommandParser.parseMoneyAmount("1234,56"))
    }

    @Test
    fun `parseMoneyAmount - US thousand separator`() {
        assertEquals(BigDecimal("1234.56"), VoiceCommandParser.parseMoneyAmount("1,234.56"))
    }

    @Test
    fun `parseMoneyAmount - EU thousand separator`() {
        assertEquals(BigDecimal("1234.56"), VoiceCommandParser.parseMoneyAmount("1.234,56"))
    }

    @Test
    fun `parseMoneyAmount - large number with US formatting`() {
        assertEquals(BigDecimal("1234567.89"), VoiceCommandParser.parseMoneyAmount("1,234,567.89"))
    }

    @Test
    fun `parseMoneyAmount - blank returns null`() {
        assertNull(VoiceCommandParser.parseMoneyAmount(""))
        assertNull(VoiceCommandParser.parseMoneyAmount("   "))
    }

    @Test
    fun `parseMoneyAmount - invalid returns null`() {
        assertNull(VoiceCommandParser.parseMoneyAmount("abc"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Integration Tests - Full parsing with dates
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseExpense - flow 3 - expense with category and date`() {
        // "expense from test2 20 category test January First"
        val defaultMillis = VoiceCommandParser.buildDateMillis(0, 7, TEST_YEAR) // Jan 7
        val result = VoiceCommandParser.parseExpense(
            "expense from test2 20 category test January First",
            defaultMillis
        )
        
        assertNotNull(result)
        assertEquals("test2", result!!.accountName)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertEquals("test", result.categoryName)
        assertEquals("Expense", result.type)
        assertDateEquals(1, 0, TEST_YEAR, result.expenseDate)
    }

    @Test
    fun `parseExpense - flow 5 - income with category and ordinal date`() {
        // "Income to Test2 20 category income First of January"
        val defaultMillis = VoiceCommandParser.buildDateMillis(0, 7, TEST_YEAR)
        val result = VoiceCommandParser.parseExpense(
            "Income to Test2 20 category income First of January",
            defaultMillis
        )
        
        assertNotNull(result)
        assertEquals("Test2", result!!.accountName)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertEquals("income", result.categoryName)
        assertEquals("Income", result.type)
        assertDateEquals(1, 0, TEST_YEAR, result.expenseDate)
    }

    @Test
    fun `parseTransfer - flow 9 - transfer with date`() {
        // "transfer from test2 to test1 20 January One"
        val defaultMillis = VoiceCommandParser.buildDateMillis(0, 7, TEST_YEAR)
        val result = VoiceCommandParser.parseTransfer(
            "transfer from test2 to test1 20 January One",
            defaultMillis
        )
        
        assertNotNull(result)
        assertEquals("test2", result!!.sourceAccountName)
        assertEquals("test1", result.destAccountName)
        assertEquals(BigDecimal("20.00"), result.amount)
        assertDateEquals(1, 0, TEST_YEAR, result.transferDate)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────────────

    private fun assertDateEquals(expectedDay: Int, expectedMonth: Int, expectedYear: Int, actualMillis: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = actualMillis
        
        assertEquals("Day mismatch", expectedDay, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals("Month mismatch", expectedMonth, calendar.get(Calendar.MONTH))
        assertEquals("Year mismatch", expectedYear, calendar.get(Calendar.YEAR))
    }
}
