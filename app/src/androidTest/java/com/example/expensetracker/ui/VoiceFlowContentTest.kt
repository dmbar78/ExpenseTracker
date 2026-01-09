package com.example.expensetracker.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.expensetracker.MainActivity
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.ui.screens.content.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.*

/**
 * Compose UI tests for voice flow screens.
 * These are "content tests" that render *Content composables directly
 * with fake state + callbacks, verifying prefill, validation, and callback args.
 */
@RunWith(AndroidJUnit4::class)
class VoiceFlowContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Test accounts
    private val testAccounts = listOf(
        Account(id = 1, name = "Test0", balance = BigDecimal("100.00"), currency = "RUB"),
        Account(id = 2, name = "Test1", balance = BigDecimal("100.00"), currency = "EUR"),
        Account(id = 3, name = "Test2", balance = BigDecimal("100.00"), currency = "EUR"),
        Account(id = 4, name = "Test3", balance = BigDecimal("100.00"), currency = "EUR")
    )

    // Test categories
    private val testCategories = listOf(
        Category(id = 1, name = "default"),
        Category(id = 2, name = "test"),
        Category(id = 3, name = "income")
    )

    // Fixed date: January 1, 2026
    private val jan1_2026: Long = run {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    // --- Flow 2: Expense with known account + known category ---
    @Test
    fun flow2_expenseWithKnownAccountAndCategory_saveCallbackInvoked() {
        var savedExpense: Expense? = null

        val state = EditExpenseState(
            expenseId = 0,
            amount = "20",
            accountName = "Test1",
            category = "default",
            currency = "EUR",
            expenseDate = System.currentTimeMillis(),
            type = "Expense",
            accountError = false,
            categoryError = false
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Verify prefill
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE).assertTextContains("Test1")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).assertTextContains("20")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE).assertTextContains("default")
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CURRENCY_VALUE).assertTextContains("EUR")

        // No errors shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertDoesNotExist()

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Verify callback invoked with correct args
        assertNotNull("Save callback should be invoked", savedExpense)
        assertEquals("Test1", savedExpense!!.account)
        assertEquals(BigDecimal("20.00"), savedExpense!!.amount)
        assertEquals("default", savedExpense!!.category)
        assertEquals("EUR", savedExpense!!.currency)
        assertEquals("Expense", savedExpense!!.type)
    }

    // --- Flow 3: Expense with unknown category - shows error, blocks save ---
    @Test
    fun flow3_expenseWithUnknownCategory_showsErrorAndBlocksSave() {
        var savedExpense: Expense? = null
        var createNewCategoryCalled = false
        var passedCategoryText: String? = null

        val state = EditExpenseState(
            expenseId = 0,
            amount = "20",
            accountName = "Test2",
            category = "unknownCategory", // Not in testCategories
            currency = "EUR",
            expenseDate = jan1_2026,
            type = "Expense",
            accountError = false,
            categoryError = true // Initially marked as error
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it },
            onCreateNewCategory = { categoryText ->
                createNewCategoryCalled = true
                passedCategoryText = categoryText
            }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Verify category error is shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertExists()

        // Click Save - should be blocked (category not valid)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Save callback should NOT be invoked
        assertNull("Save callback should not be invoked when category is invalid", savedExpense)

        // Open category dropdown and click "Create New..."
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_CREATE_NEW).performClick()

        // Verify create new callback invoked with current category text
        assertTrue("Create new category callback should be invoked", createNewCategoryCalled)
        assertEquals("unknownCategory", passedCategoryText)
    }

    // --- Flow 4: Income with known account + known category ---
    @Test
    fun flow4_incomeWithKnownAccountAndCategory_saveCallbackInvoked() {
        var savedExpense: Expense? = null

        val state = EditExpenseState(
            expenseId = 0,
            amount = "20",
            accountName = "Test1",
            category = "default",
            currency = "EUR",
            expenseDate = System.currentTimeMillis(),
            type = "Income", // Income mode
            accountError = false,
            categoryError = false
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Verify callback invoked with Income type
        assertNotNull("Save callback should be invoked", savedExpense)
        assertEquals("Income", savedExpense!!.type)
        assertEquals("Test1", savedExpense!!.account)
    }

    // --- Flow 14: Update expense - change amount ---
    @Test
    fun flow14_updateExpenseAmount_saveCallbackWithUpdatedAmount() {
        var savedExpense: Expense? = null

        val existingExpense = Expense(
            id = 100,
            account = "Test1",
            amount = BigDecimal("20.00"),
            category = "default",
            currency = "EUR",
            expenseDate = jan1_2026,
            type = "Expense",
            comment = ""
        )

        val state = EditExpenseState(
            expenseId = 100,
            amount = "20",
            accountName = "Test1",
            category = "default",
            currency = "EUR",
            expenseDate = jan1_2026,
            type = "Expense",
            existingExpense = existingExpense
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Change amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("50")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Verify callback invoked with updated amount
        assertNotNull("Save callback should be invoked", savedExpense)
        assertEquals(BigDecimal("50.00"), savedExpense!!.amount)
        assertEquals(100, savedExpense!!.id) // Same expense ID
    }

    // --- Flow 16: Delete expense ---
    @Test
    fun flow16_deleteExpense_deleteCallbackInvoked() {
        var deletedExpense: Expense? = null

        val existingExpense = Expense(
            id = 100,
            account = "Test1",
            amount = BigDecimal("20.00"),
            category = "default",
            currency = "EUR",
            expenseDate = jan1_2026,
            type = "Expense",
            comment = ""
        )

        val state = EditExpenseState(
            expenseId = 100,
            amount = "20",
            accountName = "Test1",
            category = "default",
            currency = "EUR",
            expenseDate = jan1_2026,
            type = "Expense",
            existingExpense = existingExpense
        )

        val callbacks = EditExpenseCallbacks(
            onDelete = { deletedExpense = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Delete button should exist for existing expense
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DELETE).assertExists()

        // Click Delete
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DELETE).performClick()

        // Confirm in dialog
        composeTestRule.onNodeWithText("Yes").performClick()

        // Verify delete callback invoked
        assertNotNull("Delete callback should be invoked", deletedExpense)
        assertEquals(100, deletedExpense!!.id)
    }

    // --- Flow 17: Expense with unknown account + unknown category ---
    @Test
    fun flow17_expenseWithUnknownAccountAndCategory_showsBothErrorsAndBlocksSave() {
        var savedExpense: Expense? = null
        var validationFailedCalled = false

        val state = EditExpenseState(
            expenseId = 0,
            amount = "20",
            accountName = "unknown",
            category = "test2",
            currency = "", // No currency since account unknown
            expenseDate = jan1_2026,
            type = "Expense",
            accountError = true,
            categoryError = true
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it },
            onValidationFailed = { _, _, _ -> validationFailedCalled = true }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Verify both errors are shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertExists()
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND).assertExists()

        // Click Save - should be blocked
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Save callback should NOT be invoked
        assertNull("Save callback should not be invoked", savedExpense)
    }

    // --- Flow 17 continued: Select account clears error and fills currency ---
    @Test
    fun flow17_selectAccountClearsErrorAndFillsCurrency() {
        var selectedAccount: Account? = null

        val state = EditExpenseState(
            expenseId = 0,
            amount = "20",
            accountName = "unknown",
            category = "test2",
            currency = "",
            expenseDate = jan1_2026,
            type = "Expense",
            accountError = true,
            categoryError = true
        )

        val callbacks = EditExpenseCallbacks(
            onAccountSelect = { selectedAccount = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Account error is shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertExists()

        // Open account dropdown and select Test1
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "2").performClick() // Test1 has id=2

        // Verify callback invoked
        assertNotNull("Account select callback should be invoked", selectedAccount)
        assertEquals("Test1", selectedAccount!!.name)

        // After selection, error should be cleared (local state update)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND).assertDoesNotExist()

        // Currency should be filled
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CURRENCY_VALUE).assertTextContains("EUR")
    }

    // --- Flow 18: Transfer with both accounts unknown - shows errors in EditTransferScreen ---
    @Test
    fun flow18_transferBothAccountsUnknown_showsBothErrorsInEditScreen() {
        val state = EditTransferState(
            transferId = 0,
            sourceAccountName = "unknown1",
            destAccountName = "unknown2",
            amount = "50",
            currency = "",
            date = jan1_2026,
            sourceAccountError = true,
            destAccountError = true
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = EditTransferCallbacks()
            )
        }

        // Verify both error messages are shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND).assertExists()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND).assertExists()

        // Verify prefilled values
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE).assertTextContains("unknown1")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE).assertTextContains("unknown2")
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).assertTextContains("50")
    }

    // --- Flow 18 continued: Select accounts clears errors ---
    @Test
    fun flow18_selectAccountsClearsErrors() {
        var selectedSourceAccount: Account? = null
        var selectedDestAccount: Account? = null

        val state = EditTransferState(
            transferId = 0,
            sourceAccountName = "unknown1",
            destAccountName = "unknown2",
            amount = "50",
            currency = "",
            date = jan1_2026,
            sourceAccountError = true,
            destAccountError = true
        )

        val callbacks = EditTransferCallbacks(
            onSourceAccountSelect = { selectedSourceAccount = it },
            onDestAccountSelect = { selectedDestAccount = it }
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = callbacks
            )
        }

        // Source error is shown initially
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND).assertExists()

        // Select source account (Test2, id=3)
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "source_3").performClick()

        // Verify callback invoked
        assertNotNull("Source account select callback should be invoked", selectedSourceAccount)
        assertEquals("Test2", selectedSourceAccount!!.name)

        // After selection, source error should be cleared
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND).assertDoesNotExist()

        // Dest error still shown
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND).assertExists()

        // Select dest account (Test3, id=4)
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "dest_4").performClick()

        // Verify callback invoked
        assertNotNull("Dest account select callback should be invoked", selectedDestAccount)
        assertEquals("Test3", selectedDestAccount!!.name)

        // After selection, dest error should be cleared
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND).assertDoesNotExist()
    }

    // --- AddCategory content test ---
    @Test
    fun addCategory_prefillAndSave() {
        var savedCategory: Category? = null

        val state = AddCategoryState(categoryName = "newCategory")
        val callbacks = AddCategoryCallbacks(
            onSave = { savedCategory = it }
        )

        composeTestRule.setContent {
            AddCategoryScreenContent(
                state = state,
                callbacks = callbacks
            )
        }

        // Verify prefill
        composeTestRule.onNodeWithTag(TestTags.ADD_CATEGORY_NAME_FIELD).assertTextContains("newCategory")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.ADD_CATEGORY_SAVE).performClick()

        // Verify callback
        assertNotNull("Save callback should be invoked", savedCategory)
        assertEquals("newCategory", savedCategory!!.name)
    }

    // --- EditTransfer content test - delete ---
    @Test
    fun editTransfer_delete_callbackInvoked() {
        var deletedTransfer: TransferHistory? = null

        val existingTransfer = TransferHistory(
            id = 50,
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            date = jan1_2026,
            comment = ""
        )

        val state = EditTransferState(
            transferId = 50,
            sourceAccountName = "Test1",
            destAccountName = "Test2",
            amount = "20",
            currency = "EUR",
            date = jan1_2026,
            existingTransfer = existingTransfer
        )

        val callbacks = EditTransferCallbacks(
            onDelete = { deletedTransfer = it }
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = callbacks
            )
        }

        // Click Delete
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DELETE).performClick()

        // Confirm
        composeTestRule.onNodeWithText("Yes").performClick()

        // Verify callback
        assertNotNull("Delete callback should be invoked", deletedTransfer)
        assertEquals(50, deletedTransfer!!.id)
    }

    // --- EditTransfer content test - update amount ---
    @Test
    fun editTransfer_updateAmount_callbackInvoked() {
        var savedTransfer: TransferHistory? = null

        val existingTransfer = TransferHistory(
            id = 50,
            sourceAccount = "Test1",
            destinationAccount = "Test2",
            amount = BigDecimal("20.00"),
            currency = "EUR",
            date = jan1_2026,
            comment = ""
        )

        val state = EditTransferState(
            transferId = 50,
            sourceAccountName = "Test1",
            destAccountName = "Test2",
            amount = "20",
            currency = "EUR",
            date = jan1_2026,
            existingTransfer = existingTransfer
        )

        val callbacks = EditTransferCallbacks(
            onSave = { savedTransfer = it }
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = callbacks
            )
        }

        // Change amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).performTextInput("75")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SAVE).performClick()

        // Verify callback
        assertNotNull("Save callback should be invoked", savedTransfer)
        assertEquals(BigDecimal("75.00"), savedTransfer!!.amount)
    }

    // --- Create-mode transfer: Save creates new transfer ---
    @Test
    fun createTransfer_fillFieldsAndSave_callbackInvoked() {
        var savedTransfer: TransferHistory? = null

        val state = EditTransferState(
            transferId = 0, // Create mode
            sourceAccountName = "",
            destAccountName = "",
            amount = "",
            currency = "",
            date = jan1_2026,
            existingTransfer = null,
            isEditMode = false
        )

        val callbacks = EditTransferCallbacks(
            onSave = { savedTransfer = it },
            onSourceAccountSelect = {},
            onDestAccountSelect = {}
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = callbacks
            )
        }

        // Verify shows "Add Transfer" title
        composeTestRule.onNodeWithText("Add Transfer").assertExists()

        // Delete button should NOT exist in create mode
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DELETE).assertDoesNotExist()

        // Select source account (Test2, id=3)
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SOURCE_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "source_3").performClick()

        // Select dest account (Test3, id=4)
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DESTINATION_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "dest_4").performClick()

        // Enter amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD).performTextInput("100")

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SAVE).performClick()

        // Verify callback invoked with new transfer
        assertNotNull("Save callback should be invoked", savedTransfer)
        assertEquals("Test2", savedTransfer!!.sourceAccount)
        assertEquals("Test3", savedTransfer!!.destinationAccount)
        assertEquals(BigDecimal("100.00"), savedTransfer!!.amount)
        assertEquals(0, savedTransfer!!.id) // New transfer has id=0
    }

    // --- Create-mode transfer: Validation blocks Save ---
    @Test
    fun createTransfer_missingFields_saveBlocked() {
        var savedTransfer: TransferHistory? = null

        val state = EditTransferState(
            transferId = 0,
            sourceAccountName = "",
            destAccountName = "",
            amount = "",
            currency = "",
            date = jan1_2026,
            isEditMode = false
        )

        val callbacks = EditTransferCallbacks(
            onSave = { savedTransfer = it }
        )

        composeTestRule.setContent {
            EditTransferScreenContent(
                state = state,
                accounts = testAccounts,
                callbacks = callbacks
            )
        }

        // Click Save without filling fields
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_SAVE).performClick()

        // Verify Save callback NOT invoked (validation failed)
        assertNull("Save callback should not be invoked when fields are empty", savedTransfer)
    }

    // --- Create-mode expense: Save creates new expense ---
    @Test
    fun createExpense_fillFieldsAndSave_callbackInvoked() {
        var savedExpense: Expense? = null

        val state = EditExpenseState(
            expenseId = 0, // Create mode
            amount = "",
            accountName = "",
            category = "",
            currency = "",
            expenseDate = jan1_2026,
            type = "Expense",
            accountError = false,
            categoryError = false
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it },
            onAccountSelect = {}
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Select account (Test1, id=2)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "2").performClick()

        // Enter amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("50")

        // Select category (default, id=1)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.CATEGORY_OPTION_PREFIX + "1").performClick()

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Verify callback invoked
        assertNotNull("Save callback should be invoked", savedExpense)
        assertEquals("Test1", savedExpense!!.account)
        assertEquals(BigDecimal("50.00"), savedExpense!!.amount)
        assertEquals("default", savedExpense!!.category)
        assertEquals("Expense", savedExpense!!.type)
    }

    // --- Create-mode income: Save creates new income ---
    @Test
    fun createIncome_fillFieldsAndSave_callbackInvoked() {
        var savedExpense: Expense? = null

        val state = EditExpenseState(
            expenseId = 0,
            amount = "",
            accountName = "",
            category = "",
            currency = "",
            expenseDate = jan1_2026,
            type = "Income", // Income mode
            accountError = false,
            categoryError = false
        )

        val callbacks = EditExpenseCallbacks(
            onSave = { savedExpense = it }
        )

        composeTestRule.setContent {
            EditExpenseScreenContent(
                state = state,
                accounts = testAccounts,
                categories = testCategories,
                callbacks = callbacks
            )
        }

        // Select account
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.ACCOUNT_OPTION_PREFIX + "2").performClick()

        // Enter amount
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD).performTextInput("200")

        // Select category
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN).performClick()
        composeTestRule.onNodeWithTag(TestTags.CATEGORY_OPTION_PREFIX + "3").performClick()

        // Click Save
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_SAVE).performClick()

        // Verify callback invoked with Income type
        assertNotNull("Save callback should be invoked", savedExpense)
        assertEquals("Income", savedExpense!!.type)
    }
}

/**
 * Instrumented wiring tests for the global "+" create menu.
 * These run the real app NavGraph via MainActivity and verify the menu navigates correctly.
 */
@RunWith(AndroidJUnit4::class)
class PlusMenuWiringTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun plusMenu_opensAndShowsAllItems() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_MENU).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_INCOME).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_TRANSFER).assertIsDisplayed()
    }

    @Test
    fun plusMenu_createExpense_navigatesToEditExpense_andPlusStillVisible() {
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_EXPENSE).performClick()

        // Assert we reached EditExpenseScreen (real screen tag)
        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DATE_FIELD).assertIsDisplayed()

        // Plus button remains global across destinations
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun plusMenu_createIncome_navigatesToEditExpense_andPlusStillVisible() {
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_INCOME).performClick()

        composeTestRule.onNodeWithTag(TestTags.EDIT_EXPENSE_DATE_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun plusMenu_createTransfer_navigatesToEditTransfer_andPlusStillVisible() {
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_TRANSFER).performClick()

        // Assert we reached EditTransferScreen (real screen tag)
        composeTestRule.onNodeWithTag(TestTags.EDIT_TRANSFER_DATE_FIELD).assertIsDisplayed()

        composeTestRule.onNodeWithTag(TestTags.GLOBAL_CREATE_BUTTON).assertIsDisplayed()
    }
}
