package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createUnsavedDebt_addPayment_showsSnackbar() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Create Expense
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()

        // 2. Wait for screen
        composeTestRule.waitForIdle()
        // Header text is "Add Expense"
        composeTestRule.onNodeWithText("Add Expense").assertIsDisplayed()

        // 3. Find and check "Debt" checkbox
        composeTestRule.onNodeWithText("Debt").performClick()

        // 4. Verify "Payment History" section appears (since we made it visible for any debt)
        composeTestRule.onNodeWithText("Payment History").assertIsDisplayed()

        // 5. Click "Add Payment"
        composeTestRule.onNodeWithTag("addPaymentButton").performClick()

        // 6. Verify Snackbar message
        // The message is "Save expense before creating the payment!"
        composeTestRule.onNodeWithText("Save expense before creating the payment!").assertIsDisplayed()

        // 7. Verify we are still on the "Add Expense" screen
        composeTestRule.onNodeWithTag("addPaymentButton").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add Expense").assertIsDisplayed()
    }
}
