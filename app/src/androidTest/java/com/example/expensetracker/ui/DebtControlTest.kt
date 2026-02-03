package com.example.expensetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Expense
import com.example.expensetracker.ui.screens.content.EditExpenseCallbacks
import com.example.expensetracker.ui.screens.content.EditExpenseScreenContent
import com.example.expensetracker.ui.screens.content.EditExpenseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class DebtControlTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Use test tags
    val ADD_NEW_BUTTON = "addPaymentButton"
    val ADD_EXISTING_BUTTON = "addExistingPaymentButton"
    
    // Sample data
    val testAccounts = listOf(Account(1, "Main", "USD", BigDecimal("1000")))
    val testCategories = listOf(Category(1, "Debt"))

    @Test
    fun verifyDebtControls_buttonsExistAndRename() {
        val state = EditExpenseState(
            expenseId = 1,
            amount = "100.00",
            accountName = "Main",
            category = "Debt",
            currency = "USD",
            type = "Expense",
            isDebt = true,
            debtId = 1
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks()
            )
        }

        // Verify "Add New" button exists and has correct text
        composeTestRule.onNodeWithTag(ADD_NEW_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(ADD_NEW_BUTTON, useUnmergedTree = true)
            .onChildren().filter(hasText("Add New")).assertCountEquals(1)

        // Verify "Add Existing" button exists
        composeTestRule.onNodeWithTag(ADD_EXISTING_BUTTON).assertExists()
        composeTestRule.onNodeWithTag(ADD_EXISTING_BUTTON, useUnmergedTree = true)
             .onChildren().filter(hasText("Add Existing")).assertCountEquals(1)
    }

    @Test
    fun verifyRemovePayment_callbackInvoked() {
        var removedExpense: Expense? = null
        val payment = Expense(
            id = 101,
            account = "Main",
            amount = BigDecimal("50.00"),
            category = "Debt",
            currency = "USD",
            expenseDate = System.currentTimeMillis(),
            type = "Income",
            relatedDebtId = 1
        )
        
        val state = EditExpenseState(
            expenseId = 1,
            amount = "100.00",
            accountName = "Main",
            category = "Debt",
            currency = "USD",
            type = "Expense",
            isDebt = true,
            debtId = 1,
            debtPayments = listOf(payment)
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks(
                    onRemovePayment = { removedExpense = it }
                )
            )
        }

        // Verify Remove button exists for the row
        val removeBtnTag = "removePaymentButton_${payment.id}"
        composeTestRule.onNodeWithTag(removeBtnTag).assertExists()
        
        // Click remove
        composeTestRule.onNodeWithTag(removeBtnTag).performClick()
        
        // Verify callback
        assertNotNull(removedExpense)
        assertEquals(101, removedExpense?.id)
    }
}
