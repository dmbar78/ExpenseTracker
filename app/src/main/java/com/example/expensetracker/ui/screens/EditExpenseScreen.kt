package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.EditExpenseCallbacks
import com.example.expensetracker.ui.screens.content.EditExpenseScreenContent
import com.example.expensetracker.ui.screens.content.EditExpenseState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import java.util.*

/**
 * Thin wrapper for EditExpenseScreen.
 * - Maps navigation args to state
 * - Owns SnackbarHostState for error and success messages
 * - Collects from ViewModel flows (selectedExpense, accounts, categories, errorFlow, navigateBackFlow)
 * - Handles savedStateHandle result for createdCategoryName
 * - Manages DatePickerDialog side-effect
 * - Delegates all UI rendering to EditExpenseScreenContent
 * - Shows success snackbar then pops on successful save (via navigateBackFlow)
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
    initialCategoryError: Boolean = false,
    defaultAccountUsed: Boolean = false
) {
    // Retrieve the result from AddCategoryScreen if available
    val createdCategoryName = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>("createdCategoryName")
        ?.observeAsState()

    // Retrieve the result from AddAccountScreen if available
    val createdAccountName = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>("createdAccountName")
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
    val keywords by viewModel.allKeywords.collectAsState()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsState()

    // Snackbar state for error and success messages
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
    
    // Selected keywords state
    var selectedKeywordIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    
    // Load keywords for existing expense
    LaunchedEffect(expenseId) {
        if (expenseId > 0) {
            selectedKeywordIds = viewModel.getKeywordIdsForExpense(expenseId)
        }
    }

    // Track saving state to prevent double-saves
    var isSaving by remember { mutableStateOf(false) }

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

    // Update account if a new one was created
    LaunchedEffect(createdAccountName?.value, accounts) {
        createdAccountName?.value?.let { newAccountName ->
            // First check if account is in the list yet
            val newAccount = accounts.find { it.name.equals(newAccountName, ignoreCase = true) }
            
            if (newAccount != null) {
                // Account is found - safe to update state
                accountName = newAccountName
                accountError = false // Clear error since we have a valid account now
                currency = newAccount.currency
                
                // Clear the result only after we've processed it
                navController.currentBackStackEntry?.savedStateHandle?.remove<String>("createdAccountName")
            }
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

    // Pre-populate default account if creating new expense and no account specified
    LaunchedEffect(accounts, defaultExpenseAccountId) {
        if (expenseId == 0 && accountName.isEmpty() && (initialAccountName.isNullOrBlank())) {
             defaultExpenseAccountId?.let { id ->
                 val defaultAccount = accounts.find { it.id == id }
                 if (defaultAccount != null) {
                     accountName = defaultAccount.name
                     currency = defaultAccount.currency
                 }
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

    // Collect error messages from ViewModel and show as Snackbar
    LaunchedEffect(Unit) {
        viewModel.errorFlow.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }


    // Wrap content in Scaffold with SnackbarHost
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Delegate UI to Content composable
        EditExpenseScreenContent(
            modifier = Modifier.padding(paddingValues),
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
            existingExpense = if (expenseId > 0) expense else null,
            selectedKeywordIds = selectedKeywordIds,
            defaultAccountUsed = defaultAccountUsed
        ),
        accounts = accounts,
        categories = categories,
        keywords = keywords,
        callbacks = EditExpenseCallbacks(
            onAmountChange = { amount = it },
            onAccountSelect = { selectedAccount ->
                accountName = selectedAccount.name
                currency = selectedAccount.currency
                accountError = false
            },
            onCreateNewAccount = { currentAccountText ->
                navController.navigate("addAccount?accountName=${currentAccountText.trim()}")
            },
            onCategorySelect = { selectedCategory ->
                category = selectedCategory.name
                categoryError = false
            },
            onCreateNewCategory = { currentCategoryText ->
                navController.navigate("addCategory?categoryName=${currentCategoryText.trim()}")
            },
            onDateClick = { datePickerDialog.show() },
            onCommentChange = { comment = it },
            onSave = { expenseToSave ->
                if (isSaving) return@EditExpenseCallbacks
                isSaving = true
                
                scope.launch {
                    val result = if (expenseId > 0) {
                        viewModel.updateExpenseAndReturn(expenseToSave)
                    } else {
                        viewModel.insertExpenseAndReturn(expenseToSave)
                    }
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                val message = if (expenseId > 0) "$type updated" else "$type created"
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                navController.navigateUp()
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar(error.message ?: "Failed to save $type.")
                                isSaving = false
                            }
                        )
                    }
                }
            },
            onSaveWithKeywords = { expenseToSave, keywordIds ->
                if (isSaving) return@EditExpenseCallbacks
                isSaving = true
                
                scope.launch {
                    val result = if (expenseId > 0) {
                        viewModel.updateExpenseWithKeywordsAndReturn(expenseToSave, keywordIds)
                    } else {
                        viewModel.insertExpenseWithKeywordsAndReturn(expenseToSave, keywordIds)
                    }
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        result.fold(
                            onSuccess = {
                                val message = if (expenseId > 0) "$type updated" else "$type created"
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                navController.navigateUp()
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar(error.message ?: "Failed to save $type.")
                                isSaving = false
                            }
                        )
                    }
                }
            },
            onDelete = { expenseToDelete ->
                viewModel.deleteExpense(expenseToDelete)
                navController.popBackStack()
            },
            onValidationFailed = { accError, catError, amtError ->
                accountError = accError
                categoryError = catError
                // amountError handled locally in Content
            },
            onCreateKeyword = { name ->
                viewModel.insertKeyword(name)
            }
        )
        )
    }
}
