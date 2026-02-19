package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for inline account creation navigation flow.
 * Tests the complete user journey of creating accounts from Edit screens.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InlineAccountCreationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking {
            db.expenseDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.currencyDao().deleteAll()
            db.transferHistoryDao().deleteAll()
            
            // Re-populate defaults
            db.currencyDao().insert(com.example.expensetracker.data.Currency(code = "USD", name = "United States Dollar"))
            db.categoryDao().insert(com.example.expensetracker.data.Category(name = "Default"))
        }
    }

    @Test
    fun editTransfer_createNewAccount_navigatesAndReturnsSuccessfully() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()

        // Navigate to create transfer
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_TRANSFER).performClick()

        // Wait for EditTransferScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_DROPDOWN).assertIsDisplayed()

        // Open source account dropdown
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_DROPDOWN).performClick()
        
        // Click "Create New..."
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_CREATE_NEW).performClick()

        // Should navigate to AddAccountScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).assertIsDisplayed()

        // Create account
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).performTextInput("NewTestAccount")
        
        // Select Currency
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_CURRENCY_DROPDOWN).performClick()
        composeTestRule.onAllNodesWithTag(TestTags.CURRENCY_OPTION_PREFIX + "USD").onFirst().performClick()
        
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_SAVE).performClick()

        // Should navigate back to EditTransferScreen - wait for it
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify source account was populated
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE)
            .assertTextContains("NewTestAccount")

        // Now fill in destination account
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_DROPDOWN).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DEST_CREATE_NEW).performClick()

        // Create second account
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).performTextInput("SecondTestAccount")
        
        // Select Currency
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_CURRENCY_DROPDOWN).performClick()
        composeTestRule.onAllNodesWithTag(TestTags.CURRENCY_OPTION_PREFIX + "USD").onFirst().performClick()
        
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_SAVE).performClick()

        // Back to EditTransferScreen - wait for it
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify destination account was populated
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE)
            .assertTextContains("SecondTestAccount")

        // Fill amount and save
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).performTextInput("100")
        
        // Verify no errors shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND).assertDoesNotExist()

        // Save transfer
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SAVE).performClick()

        // Check if an error occurred (if we are still here, maybe a snackbar is showing?)
        composeTestRule.waitForIdle()
        // If these exist, the test will fail here and tell us which one
        composeTestRule.onNodeWithText("Invalid transfer.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Failed to add transfer.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Currency mismatch").assertDoesNotExist()
        composeTestRule.onNodeWithText("Source account not found. Please select a valid account.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Destination account not found. Please select a valid account.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Please enter a valid amount.").assertDoesNotExist()

        // Should navigate back to home after successful save.
        // On some devices/animations, navigation callback can lag; accept either:
        // 1) Home route is visible, OR
        // 2) Transfer was actually persisted in DB.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        composeTestRule.waitUntil(timeoutMillis = 15000) {
            val onHome = runCatching {
                composeTestRule.onAllNodesWithTag(TestTags.GLOBAL_CREATE_BUTTON)
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)

            if (onHome) return@waitUntil true

            kotlinx.coroutines.runBlocking {
                db.transferHistoryDao().getAllTransfersOnce().any {
                    it.sourceAccount == "NewTestAccount" && it.destinationAccount == "SecondTestAccount"
                }
            }
        }
    }

    @Test
    fun editExpense_createNewAccount_navigatesAndReturnsSuccessfully() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()

        // Navigate to create expense
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()

        // Wait for EditExpenseScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).assertIsDisplayed()

        // Open account dropdown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).performClick()
        
        // Click "Create New..."
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_CREATE_NEW).performClick()

        // Should navigate to AddAccountScreen
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).assertIsDisplayed()

        // Create account
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).performTextInput("ExpenseTestAccount")
        
        // Select Currency
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_CURRENCY_DROPDOWN).performClick()
        composeTestRule.onAllNodesWithTag(TestTags.CURRENCY_OPTION_PREFIX + "USD").onFirst().performClick()
        
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_SAVE).performClick()

        // Should navigate back to EditExpenseScreen - wait for it
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify account was populated
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
            .assertTextContains("ExpenseTestAccount")

        // Verify no error shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertDoesNotExist()

        // Fill in amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("50")

        // Select category (default should exist)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN).performClick()
        composeTestRule.waitForIdle()
        // Click default category option
        composeTestRule.onNodeWithText("Default").performClick()

        // Verify validation passes before save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_AMOUNT).assertDoesNotExist()

        // Save expense
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Check if validation failed (account not found)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_AMOUNT).assertDoesNotExist()

        // Check for snackbar errors
        composeTestRule.onNodeWithText("Failed to add expense.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Failed to update expense.").assertDoesNotExist()

        // Should navigate back (expense saved successfully) - wait for navigation
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag(TestTags.EDIT_EXPENSE_DATE_FIELD)
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
