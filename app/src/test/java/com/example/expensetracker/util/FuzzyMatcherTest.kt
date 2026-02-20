package com.example.expensetracker.util

import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FuzzyMatcherTest {

    private val sampleAccounts = listOf(
        Account(1, "Cash", "EUR", BigDecimal.ZERO),
        Account(2, "Sberbank", "RUB", BigDecimal.ZERO),
        Account(3, "Tinkoff", "RUB", BigDecimal.ZERO),
        Account(4, "Revolut", "EUR", BigDecimal.ZERO)
    )

    private val sampleCategories = listOf(
        Category(1, "Groceries"),
        Category(2, "Transport"),
        Category(3, "Dining Out"),
        Category(4, "Rent")
    )

    @Test
    fun testMatchAccount_ExactName_ReturnsMatch() {
        val result = FuzzyMatcher.matchAccount("Sberbank", sampleAccounts)
        assertEquals("Sberbank", result?.name)
    }

    @Test
    fun testMatchAccount_Typo_ReturnsMatch() {
        val result = FuzzyMatcher.matchAccount("Sbrebank", sampleAccounts)
        assertEquals("Sberbank", result?.name)
    }

    @Test
    fun testMatchAccount_DifferentCase_ReturnsMatch() {
        val result = FuzzyMatcher.matchAccount("revoLUt", sampleAccounts)
        assertEquals("Revolut", result?.name)
    }

    @Test
    fun testMatchAccount_CompletelyWrong_ReturnsNull() {
        // Below confidence threshold
        val result = FuzzyMatcher.matchAccount("UnknownBank", sampleAccounts)
        assertNull(result)
    }

    @Test
    fun testMatchCategory_Typo_ReturnsMatch() {
        val result = FuzzyMatcher.matchCategory("Grocries", sampleCategories)
        assertEquals("Groceries", result?.name)
        
        val result2 = FuzzyMatcher.matchCategory("Dining", sampleCategories)
        assertEquals("Dining Out", result2?.name)
    }
}
