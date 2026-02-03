package com.example.expensetracker.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.expensetracker.MainActivity
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Currency
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.TransferHistory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AccountDeletionSafeguardTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun clearDatabase() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking {
            db.expenseDao().deleteAll()
            db.accountDao().deleteAll()
            db.categoryDao().deleteAll()
            db.currencyDao().deleteAll()
            db.transferHistoryDao().deleteAll()
        }
    }

    private fun navigateToEditAccount(accountName: String) {
        composeTestRule.waitForIdle()

        // Open Drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Accounts
        composeTestRule.onNodeWithText("Accounts").performClick()
        composeTestRule.waitForIdle()

        // Wait for the account to appear
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(accountName, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Find all "Edit" buttons and click the one corresponding to our account
        // Since accounts are displayed in a list, we find the Edit button by its index
        // matching the account's position
        val accountNodes = composeTestRule.onAllNodesWithText(accountName, substring = true)
            .fetchSemanticsNodes()
        
        if (accountNodes.isNotEmpty()) {
            // Find all Edit buttons
            val editButtons = composeTestRule.onAllNodesWithText("Edit")
            
            // For simplicity, if there's only one account with this name, click the first Edit button
            // In a real scenario with multiple accounts, we'd need more sophisticated matching
            editButtons[0].performClick()
        }

        composeTestRule.waitForIdle()
    }

    private fun attemptDeleteAndExpectError() {
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Yes").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Cannot delete account. It has associated expenses or transfers.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("OK").performClick()
    }

    private fun attemptDeleteAndExpectSuccess() {
        composeTestRule.onNodeWithText("Delete").performClick()
        composeTestRule.onNodeWithText("Yes").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Delete").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun accountDeletion_blockedIfExpenseExists_allowedIfCleared() {
        // Setup
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        
        // Use a clearer simplified flow with waits
        kotlinx.coroutines.runBlocking {
            clearDatabase()
            db.currencyDao().insert(Currency(code = "USD", name = "United States Dollar"))
            db.categoryDao().insert(Category(name = "General"))
            db.accountDao().insert(Account(name = "ExpenseTestAccount", currency = "USD", balance = BigDecimal("1000.00")))
            db.expenseDao().insert(Expense(
                account = "ExpenseTestAccount",
                amount = BigDecimal("50.00"),
                currency = "USD",
                category = "General",
                type = "Expense",
                expenseDate = System.currentTimeMillis()
            ))
        }

        navigateToEditAccount("ExpenseTestAccount")

        // Wait a bit to ensure screen is ready and loaded
        composeTestRule.waitForIdle()

        // Attempt delete - should fail
        attemptDeleteAndExpectError()

        // Clear the blocking data in a way that the UI might notice? 
        // The UI doesn't auto-refresh the blocking check unless we trigger the delete again.
        kotlinx.coroutines.runBlocking {
            db.expenseDao().deleteAll()
        }
        
        // Give time for any background sync (though Room is direct usually)
        Thread.sleep(500) 

        // Attempt delete again - should succeed
        attemptDeleteAndExpectSuccess()
    }

    @Test
    fun accountDeletion_blockedIfTransferExists_allowedIfCleared() {
        // Setup
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
       kotlinx.coroutines.runBlocking {
            clearDatabase()
            db.currencyDao().insert(Currency(code = "USD", name = "United States Dollar"))
            db.categoryDao().insert(Category(name = "General"))
            // Need two accounts for a transfer
            db.accountDao().insert(Account(name = "TransferSourceAccount", currency = "USD", balance = BigDecimal("1000.00")))
            db.accountDao().insert(Account(name = "TransferDestAccount", currency = "USD", balance = BigDecimal("500.00")))
            db.transferHistoryDao().insert(TransferHistory(
                sourceAccount = "TransferSourceAccount",
                destinationAccount = "TransferDestAccount",
                amount = BigDecimal("100.00"),
                currency = "USD",
                date = System.currentTimeMillis()
            ))
        }

        // Test source account deletion is blocked
        navigateToEditAccount("TransferSourceAccount")
        attemptDeleteAndExpectError()

        // Navigate back to accounts screen explicitly
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Accounts").performClick()
        composeTestRule.waitForIdle()
        
        // Wait for accounts to be visible again
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("TransferDestAccount", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Now navigate to destination account - but we're already on Accounts screen, so just find the Edit button
        val editButtons = composeTestRule.onAllNodesWithText("Edit")
        // The second Edit button should be for TransferDestAccount (accounts are likely ordered by insertion)
        editButtons[1].performClick()
        composeTestRule.waitForIdle()

        attemptDeleteAndExpectError()

        // Clear the blocking data
        kotlinx.coroutines.runBlocking {
            db.transferHistoryDao().deleteAll()
        }

        // Attempt delete again - should succeed
        attemptDeleteAndExpectSuccess()
    }
}
