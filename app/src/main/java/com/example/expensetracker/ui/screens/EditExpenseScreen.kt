package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.EditExpenseCallbacks
import com.example.expensetracker.ui.screens.content.EditExpenseScreenContent
import com.example.expensetracker.ui.screens.content.EditExpenseState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal
import java.util.*

/**
 * Thin wrapper for EditExpenseScreen.
 * - Maps navigation args to state
 * - Collects from ViewModel flows (selectedExpense, accounts, categories, navigateBackFlow)
 * - Handles savedStateHandle result for createdCategoryName
 * - Manages DatePickerDialog side-effect
 * - Delegates all UI rendering to EditExpenseScreenContent
 * - Pops only on successful save (via navigateBackFlow)
 */
@Composable
fun EditExpenseScreen(
    expenseId: Int,
    viewModel: ExpenseViewModel,
    navController: NavController,
    initialAccountName: String? = null,
    initialAmount: BigDecimal? = null,
    initialCategoryName: String? = null,
    initialType: String? = null,
    initialExpenseDateMillis: Long = 0L,
    initialAccountError: Boolean = false,
    initialCategoryError: Boolean = false
) {
    // Retrieve the result from AddCategoryScreen if available
    val createdCategoryName = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>("createdCategoryName")
        ?.observeAsState()

    // Only load from DB if it's an existing expense (ID > 0)
    LaunchedEffect(expenseId) {
        if (expenseId > 0) {
            viewModel.loadExpense(expenseId)
        }
    }

    val expense by viewModel.selectedExpense.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()

    // State variables using rememberSaveable to persist across navigation
    var amount by rememberSaveable { mutableStateOf(initialAmount?.toPlainString() ?: "") }
    var accountName by rememberSaveable { mutableStateOf(initialAccountName ?: "") }
    var category by rememberSaveable { mutableStateOf(initialCategoryName ?: "") }
    var currency by rememberSaveable { mutableStateOf("") }
    // Use parsed date from voice if available (> 0), else default to now
    val initialDateMillis = if (initialExpenseDateMillis > 0L) initialExpenseDateMillis else System.currentTimeMillis()
    var expenseDate by rememberSaveable { mutableStateOf(initialDateMillis) }
    var comment by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType ?: "Expense") }
    
    // Error states using rememberSaveable
    var accountError by rememberSaveable { mutableStateOf(initialAccountError) }
    var categoryError by rememberSaveable { mutableStateOf(initialCategoryError) }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    
    // Keep calendar in sync with expenseDate state
    LaunchedEffect(expenseDate) {
        calendar.timeInMillis = expenseDate
    }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            null, // Set listener below
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    // Update picker's initial date and set listener
    LaunchedEffect(expenseDate) {
        datePickerDialog.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    // Set the date selection listener (stable reference)
    DisposableEffect(Unit) {
        datePickerDialog.setOnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth)
            expenseDate = calendar.timeInMillis
        }
        onDispose { }
    }

    // Update category if a new one was created
    LaunchedEffect(createdCategoryName?.value) {
        createdCategoryName?.value?.let {
            category = it
            categoryError = false // Clear error since we have a valid category now
            // Clear the result so it doesn't re-trigger if we navigate back and forth
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("createdCategoryName")
        }
    }

    // Update state if loading an existing expense from DB
    LaunchedEffect(expense) {
        if (expenseId > 0) {
            expense?.let {
                amount = it.amount.toPlainString()
                accountName = it.account
                category = it.category
                currency = it.currency
                expenseDate = it.expenseDate
                comment = it.comment ?: ""
                type = it.type
                // Reset errors when loading existing expense
                accountError = false
                categoryError = false
            }
        } else {
             // If creating new from voice, try to set currency if account is valid
             if (initialAccountName != null && !initialAccountError) {
                 val account = accounts.find { it.name.equals(initialAccountName, ignoreCase = true) }
                 if (account != null) {
                     currency = account.currency
                 }
             }
        }
    }

    // Reactive Error Clearing:
    // If the account list changes OR an error is flagged, check if the error is still valid.
    LaunchedEffect(accounts, accountName, accountError) {
        if (accountError && accountName.isNotEmpty()) {
            val account = accounts.find { it.name.equals(accountName, ignoreCase = true) }
            if (account != null) {
                accountError = false
                currency = account.currency // Also update currency
            }
        }
         // Update currency even if no error, if it's missing
        if (currency.isEmpty() && accountName.isNotEmpty()) {
             val account = accounts.find { it.name.equals(accountName, ignoreCase = true) }
             if (account != null) {
                 currency = account.currency
             }
        }
    }

    // Reactive Error Clearing:
    // If the category list changes OR an error is flagged, check if the error is still valid.
    LaunchedEffect(categories, category, categoryError) {
        if (categoryError && category.isNotEmpty()) {
             if (categories.any { it.name.equals(category, ignoreCase = true) }) {
                 categoryError = false
             }
        }
    }

    // Listen for navigateBackFlow to pop after successful save
    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest {
            navController.popBackStack()
        }
    }

    // Delegate UI to Content composable
    EditExpenseScreenContent(
        state = EditExpenseState(
            expenseId = expenseId,
            amount = amount,
            accountName = accountName,
            category = category,
            currency = currency,
            expenseDate = expenseDate,
            comment = comment,
            type = type,
            accountError = accountError,
            categoryError = categoryError,
            amountError = false,
            existingExpense = if (expenseId > 0) expense else null
        ),
        accounts = accounts,
        categories = categories,
        callbacks = EditExpenseCallbacks(
            onAmountChange = { amount = it },
            onAccountSelect = { selectedAccount ->
                accountName = selectedAccount.name
                currency = selectedAccount.currency
                accountError = false
            },
            onCategorySelect = { selectedCategory ->
                category = selectedCategory.name
                categoryError = false
            },
            onCreateNewCategory = { currentCategoryText ->
                navController.navigate("addCategory?categoryName=$currentCategoryText")
            },
            onDateClick = { datePickerDialog.show() },
            onCommentChange = { comment = it },
            onSave = { expenseToSave ->
                if (expenseId > 0) {
                    viewModel.updateExpense(expenseToSave)
                } else {
                    viewModel.insertExpense(expenseToSave)
                }
                // Don't pop here - wait for navigateBackFlow
            },
            onDelete = { expenseToDelete ->
                viewModel.deleteExpense(expenseToDelete)
                navController.popBackStack()
            },
            onValidationFailed = { accError, catError, amtError ->
                accountError = accError
                categoryError = catError
                // amountError handled locally in Content
            }
        )
    )
}
