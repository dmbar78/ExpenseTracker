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
    @Test
    fun addCategory_shouldAutoFocusName() {
        val state = com.example.expensetracker.ui.screens.content.AddCategoryState(
            categoryName = ""
        )
        
        composeTestRule.setContent {
            com.example.expensetracker.ui.screens.content.AddCategoryScreenContent(
                state = state,
                callbacks = com.example.expensetracker.ui.screens.content.AddCategoryCallbacks()
            )
        }

        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag(TestTags.ADD_CATEGORY_NAME_FIELD)
            .assertIsFocused()
    }

    @Test
    fun editCategory_shouldAutoFocusName() {
        // Need to test EditCategoryScreenContent directly
        composeTestRule.setContent {
            com.example.expensetracker.ui.screens.EditCategoryScreenContent(
                category = com.example.expensetracker.data.Category(id=1, name="Food"),
                isDefaultCategory = false,
                onSave = {},
                onDeleteRequest = {}
            )
        }

        composeTestRule.waitForIdle()
        
        composeTestRule.onNodeWithTag(TestTags.EDIT_CATEGORY_NAME_FIELD)
            .assertIsFocused()
    }

    @Test
    fun createKeywordDialog_shouldAutoFocusName() {
        val state = EditExpenseState(expenseId = 0)
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = emptyList(),
                callbacks = EditExpenseCallbacks()
            )
        }

        // 1. Open Keyword Dropdown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH)
            .performClick()
            
        composeTestRule.waitForIdle()

        // 2. Click "Create New"
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_NEW)
            .performClick()
            
        composeTestRule.waitForIdle()
        
        // 3. Verify Dialog Name Field is Focused
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_KEYWORD_NEW_NAME)
            .assertIsDisplayed()
            .assertIsFocused()
    }

    @Test
    fun editKeywordDialog_shouldAutoFocusName() {
        val keyword = com.example.expensetracker.data.Keyword(id=1, name="TestKeyword")
        val state = EditExpenseState(
            expenseId = 1, 
            selectedKeywordIds = setOf(1)
        )
        
        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = emptyList(),
                categories = emptyList(),
                keywords = listOf(keyword),
                callbacks = EditExpenseCallbacks()
            )
        }

        // 1. Long press keyword chip to show menu
        composeTestRule.onNodeWithText("TestKeyword")
            .performTouchInput { longClick() }
            
        composeTestRule.waitForIdle()

        // 2. Click "Edit" option
        composeTestRule.onNodeWithText("Edit") // Using text as there is no specific tag for menu item yet or I missed it
            .performClick()
            
        composeTestRule.waitForIdle()
        
        // 3. Verify Dialog Name Field is Focused
        // I need to check the tag used in the dialog, I used "EditKeywordName"
        composeTestRule.onNodeWithTag("EditKeywordName")
            .assertIsDisplayed()
            .assertIsFocused()
    }
}
