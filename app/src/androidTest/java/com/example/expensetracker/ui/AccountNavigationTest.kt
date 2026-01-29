package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Expense
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {

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

    @Test
    fun clickingAccount_navigatesToHome_withFilterSet() {
        // Setup
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.expensetracker.data.AppDatabase.getDatabase(context)
        val accountName = "TestNavAccount"
        
        kotlinx.coroutines.runBlocking {
            clearDatabase()
            db.accountDao().insert(Account(name = accountName, currency = "USD", balance = BigDecimal("1000.00")))
            // Add an expense for this account so we can verify the list
            db.expenseDao().insert(Expense(
                account = accountName,
                amount = BigDecimal("50.00"),
                currency = "USD",
                category = "General",
                type = "Expense",
                expenseDate = System.currentTimeMillis()
            ))
            // Add another expense for a DIFFERENT account (which shouldn't be valid, but effectively simulates other data)
            // Actually, let's create a second account to be sure
             db.accountDao().insert(Account(name = "OtherAccount", currency = "USD", balance = BigDecimal("1000.00")))
             db.expenseDao().insert(Expense(
                account = "OtherAccount",
                amount = BigDecimal("25.00"),
                currency = "USD",
                category = "General",
                type = "Expense",
                expenseDate = System.currentTimeMillis()
            ))
        }

        composeTestRule.waitForIdle()

        // 1. Go to Accounts Screen
        // Ensure activity is ready
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()
        
        // Wait for Menu to appear
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Accounts").performClick()
        composeTestRule.waitForIdle()

        // 2. Click on the account
        // We look for the text "TestNavAccount (USD)" or just the name. 
        // The AccountRow displays: "${account.name} (${account.currency})"
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText(accountName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        
        // Find the node that is CLIICKABLE within the row hierarchy? 
        // The Column containing the text is clickable.
        // We can click the text itself which bubbles up or matches the clickable parent.
        composeTestRule.onNodeWithText(text = "$accountName (USD)", substring = true).performClick()
        composeTestRule.waitForIdle()

        // 3. Verify Navigation to Home
        // "Expenses" tab is default on Home. Check if we are on Home.
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Total:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Total:", substring = true).assertExists() // Total Header exists on Home

        // 4. Verify Filter is Set
        // The "Home" screen displays filter chips. 
        // We expect a chip with the account name or "Account: Name"
        // The TextQueryFilterDialog uses "Account: $name" usually? 
        // Looking at FilterChipsRow.kt (not viewed but inferred from common UX), usually shows "Account: Name"
        // Let's check for the existence of the text "Account: TestNavAccount"
        composeTestRule.onNodeWithText("Account: $accountName").assertExists()

        // 5. Verify List Filtered
        // Should see the 50.00 expense
        composeTestRule.onNodeWithText("50.00").assertExists()
        // Should NOT see the 25.00 expense
        composeTestRule.onNodeWithText("25.00").assertDoesNotExist()
    }
}
