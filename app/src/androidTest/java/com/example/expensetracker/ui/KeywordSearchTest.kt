package com.example.expensetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Keyword
import com.example.expensetracker.ui.screens.content.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class KeywordSearchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Test data
    private val testAccounts = listOf(
        Account(id = 1, name = "Test Account", balance = BigDecimal("100.00"), currency = "USD")
    )

    private val testCategories = listOf(
        Category(id = 1, name = "General")
    )
    
    private val testKeywords = listOf(
        Keyword(id = 1, name = "Groceries"),
        Keyword(id = 2, name = "Travel"),
        Keyword(id = 3, name = "Utilities")
    )

    private val jan1_2026: Long = 1767225600000L // 2026-01-01

    @Test
    fun keywordSearch_autoExpandsOnTyping() {
        val state = EditExpenseState(
            expenseId = 0,
            amount = "10",
            accountName = "Test Account",
            category = "General",
            currency = "USD",
            expenseDate = jan1_2026
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                keywords = testKeywords,
                callbacks = EditExpenseCallbacks()
            )
        }

        // Verify dropdown is NOT expanded initially (Create New option should not be visible)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_NEW).assertDoesNotExist()

        // Type "Gro" into keyword search
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH).performTextInput("Gro")
        composeTestRule.waitForIdle()

        // Verify dropdown Is expanded
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_NEW).assertExists()
        
        // Check "Groceries" is displayed
        composeTestRule.onNodeWithText("Groceries").assertExists()
    }
    
    @Test
    fun keywordSearch_filteringWorks() {
        val state = EditExpenseState(
            expenseId = 0,
            amount = "10",
            accountName = "Test Account",
            category = "General",
            currency = "USD",
            expenseDate = jan1_2026
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                keywords = testKeywords,
                callbacks = EditExpenseCallbacks()
            )
        }

        // Type "Trav"
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH).performTextInput("Trav")
        composeTestRule.waitForIdle()

        // Verify "Travel" exists
        composeTestRule.onNodeWithText("Travel").assertExists()
        
        // Verify "Groceries" does NOT exist
        composeTestRule.onNodeWithText("Groceries").assertDoesNotExist()
    }
}
