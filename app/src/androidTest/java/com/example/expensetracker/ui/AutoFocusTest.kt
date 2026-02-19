package com.example.expensetracker.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.TransferHistory
// removed EditExpenseCallback
import com.example.expensetracker.ui.screens.content.EditExpenseCallbacks
import com.example.expensetracker.ui.screens.content.EditExpenseScreenContent
import com.example.expensetracker.ui.screens.content.EditExpenseState
import com.example.expensetracker.ui.screens.content.EditTransferCallbacks
import com.example.expensetracker.ui.screens.content.EditTransferScreenContent
import com.example.expensetracker.ui.screens.content.EditTransferState
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class AutoFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newExpense_shouldAutoFocusAmount() {
        val state = EditExpenseState(
            expenseId = 0, // NEW
            amount = "" // EMPTY
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        // Assert Amount field is focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD)
            .assertIsFocused()
    }

    @Test
    fun existingExpense_shouldNotAutoFocus() {
        val state = EditExpenseState(
            expenseId = 1, // EXISTING
            amount = "10.00",
            existingExpense = Expense(
                id = 1, amount = BigDecimal("10.00"), 
                account = "Cash", category = "Food", 
                expenseDate = System.currentTimeMillis(),
                type = "Expense",
                currency = "USD"
            )
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        // Assert Amount field is NOT focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD)
            .assertIsNotFocused()
    }
    
    @Test
    fun copiedExpense_shouldNotAutoFocus_ifAmountPresent() {
        // Technically copied expense has ID 0, but Amount is pre-filled.
        // My implementation checks amount.isEmpty().
        val state = EditExpenseState(
            expenseId = 0, // NEW
            amount = "10.00" // PRE-FILLED (Copy)
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        // Assert Amount field is NOT focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD)
            .assertIsNotFocused()
    }

    @Test
    fun newTransfer_shouldAutoFocusSourceAmount() {
        val state = EditTransferState(
            transferId = 0, // NEW
            amount = "" // EMPTY
        )
        
        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = emptyList(),
                callbacks = EditTransferCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        // Assert Source Amount field is focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD)
            .assertIsFocused()
    }
    
    @Test
    fun existingTransfer_shouldNotAutoFocus() {
        val state = EditTransferState(
            transferId = 1, // EXISTING
            amount = "50.00",
            existingTransfer = TransferHistory(
                id = 1, amount = BigDecimal("50.00"),
                sourceAccount = "A", destinationAccount = "B",
                date = System.currentTimeMillis(),
                currency = "USD", // Added missing param
                destinationCurrency = "USD"
            )
        )
        
        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = emptyList(),
                callbacks = EditTransferCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        // Assert Source Amount field is NOT focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD)
            .assertIsNotFocused()
    }
}
