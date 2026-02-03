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
class CategorySearchTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Test data
    private val testAccounts = listOf(
        Account(id = 1, name = "Test Account", balance = BigDecimal("100.00"), currency = "USD")
    )

    private val testCategories = listOf(
        Category(id = 1, name = "Food"),
        Category(id = 2, name = "Transport"),
        Category(id = 3, name = "Housing")
    )
    
    private val testKeywords: List<Keyword> = emptyList()

    private val jan1_2026: Long = 1767225600000L // 2026-01-01

    @Test
    fun categorySearch_filteringWorks() {
        val state = EditExpenseState(
            expenseId = 0,
            amount = "10",
            accountName = "Test Account",
            category = "",
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

        // Click category field to focus
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE).performClick()
        
        // Type "Trans" - expect Transport to show, others to hide
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE).performTextInput("Trans")
        composeTestRule.waitForIdle()

        // Check "Transport" is displayed in dropdown
        composeTestRule.onNodeWithTag(TestTags.CATEGORY_OPTION_PREFIX + "2", useUnmergedTree = true).assertExists()
        
        // Check "Food" is NOT displayed
        composeTestRule.onNodeWithTag(TestTags.CATEGORY_OPTION_PREFIX + "1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun categorySearch_validation_invalidCategoryBlocksSave() {
        var validationFailed = false
        var categoryErrorState = false

        val callbacks = EditExpenseCallbacks(
            onValidationFailed = { _, catError, _ -> 
                validationFailed = true 
                categoryErrorState = catError
            },
            onSave = { fail("Should not save with invalid category") },
            onSaveWithKeywords = { _, _ -> fail("Should not save with invalid category") },
            onSaveDebt = { _, _, _ -> fail("Should not save with invalid category") }
        )

        var stateCategory = "InvalidCat"
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = EditExpenseState(
                    expenseId = 0,
                    amount = "10",
                    accountName = "Test Account",
                    category = stateCategory, // Simulating typed invalid category
                    currency = "USD",
                    expenseDate = jan1_2026,
                    categoryError = false
                ),
                accounts = testAccounts,
                categories = testCategories,
                keywords = testKeywords,
                callbacks = callbacks
            )
        }

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()
        
        // Verify validation failed
        assertTrue("Validation should have failed", validationFailed)
        assertTrue("Category error should be reported", categoryErrorState)
        
        // Verify error message is displayed
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertExists()
    }
}
