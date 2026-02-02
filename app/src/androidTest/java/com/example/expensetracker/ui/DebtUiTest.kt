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

    @Test
    fun createPayment_debtCheckboxIsHidden() {
        composeTestRule.waitForIdle()

        // 1. Navigate to Create Expense
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()

        // 2. Check "Debt" and wait for UI update
        composeTestRule.onNodeWithText("Debt").performClick()
        composeTestRule.waitForIdle()
        
        // 3. Create parent debt process
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("100")
        
        // Create account
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_CREATE_NEW).performClick()
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_NAME_FIELD).performTextInput("DebtAccount")
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_CURRENCY_DROPDOWN).performClick()
        composeTestRule.onAllNodesWithTag(TestTags.CURRENCY_OPTION_PREFIX + "USD").onFirst().performClick()
        composeTestRule.onNodeWithTag(TestTags.ADD_ACCOUNT_SAVE).performClick()
        
        // Select Category
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN).performClick()
        composeTestRule.onNodeWithText("Default").performClick()
        
        // Save Parent Debt
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()
        composeTestRule.waitForIdle()
        
        // 4. Navigate to the created Debt Expense
        composeTestRule.onNodeWithText("DebtAccount").performClick()
        composeTestRule.waitForIdle()
        
        // 5. Add Payment
        composeTestRule.onNodeWithText("Add Payment").performClick() // Using text matcher as button inside lazy column might need scrolling or specific tag access
        composeTestRule.waitForIdle()
        
        // 6. Verify we are on "Add Income"
        composeTestRule.onNodeWithText("Add Income").assertIsDisplayed()
        
        // 7. CRITICAL CHECK: "Debt" checkbox should NOT be visible
        composeTestRule.onNodeWithText("Debt").assertDoesNotExist()
    }
}
