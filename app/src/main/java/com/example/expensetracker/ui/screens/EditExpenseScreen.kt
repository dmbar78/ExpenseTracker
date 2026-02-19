package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.R
import com.example.expensetracker.data.Expense
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items

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
    defaultAccountUsed: Boolean = false,
    relatedDebtId: Int? = null,
    copyFromId: Int? = null
) {
    val context = LocalContext.current
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

    // CRITICAL: Clear any stale cloned data SYNCHRONOUSLY before observing flows
    // This fixes the race condition where stale cloned data is observed before LaunchedEffect runs
    remember(expenseId, copyFromId) {
        if (expenseId == 0 && copyFromId == null) {
            viewModel.clearClonedExpense()
        }
        true // Return value for remember
    }

    // Load from DB if editing, copying, OR creating new (to clear any previous clone state)
    LaunchedEffect(expenseId, copyFromId) {
        // Always call loadExpense to ensure clean state (clears _clonedExpense when creating new)
        viewModel.loadExpense(expenseId, copyFromId)
    }

    val expense by viewModel.selectedExpense.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val keywords by viewModel.allKeywords.collectAsState()
    // Observe selected/cloned keywords
    val loadedKeywordIds by viewModel.selectedExpenseKeywords.collectAsState()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsState()
    
    // Debt State
    // We need to know if this expense has a linked Debt record
    val debt by viewModel.getDebtForExpense(expenseId).collectAsState(initial = null)
    
    // If we have a debt, load payments for it
    val debtPayments by remember(debt?.id) {
        if (debt != null) viewModel.getPaymentsForDebt(debt!!.id) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    
    // Calculate paid amount and conversions
    var debtPaidAmount by remember { mutableStateOf(BigDecimal.ZERO) }
    var debtPaymentConvertedAmounts by remember { mutableStateOf<Map<Int, BigDecimal>>(emptyMap()) }
    
    LaunchedEffect(debt, debtPayments) {
        if (debt != null) {
             val account = accounts.find { it.name == expense?.account }
             val currency = account?.currency ?: expense?.currency ?: "USD" // Fallback
             debtPaidAmount = viewModel.calculateDebtPaidAmount(debt!!.id, currency)
             debtPaymentConvertedAmounts = viewModel.getConvertedPaymentAmounts(debtPayments, currency)
             debtPaymentConvertedAmounts = viewModel.getConvertedPaymentAmounts(debtPayments, currency)
        }
    }
    
    // Dialog state for "Add Existing"
    var showAddExistingDialog by remember { mutableStateOf(false) }
    var potentialPayments by remember { mutableStateOf<List<Expense>>(emptyList()) }
    
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
    
    // Debt Checkbox State
    // Initialize from existing debt existence, or false if new
    var isDebt by rememberSaveable { mutableStateOf(false) }
    
    // Update isDebt when debt data loads
    LaunchedEffect(debt) {
        if (debt != null) {
            isDebt = true
        }
    }

    // Error states using rememberSaveable
    var accountError by rememberSaveable { mutableStateOf(initialAccountError) }
    var categoryError by rememberSaveable { mutableStateOf(initialCategoryError) }
    
    // Selected keywords state
    var selectedKeywordIds by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    
    // Load keywords for existing expense or cloned expense
    // Load keywords for existing expense or cloned expense
    // CRITICAL FIX: Only update if the loaded keywords match the CURRENT expense ID
    LaunchedEffect(loadedKeywordIds, expenseId) {
        val (loadedId, keywords) = loadedKeywordIds
        
        // Match condition:
        // 1. If editing existing (expenseId > 0), loadedId must match expenseId
        // 2. If creating new/cloning (expenseId == 0), loadedId must be 0 (from our ViewModel logic)
        val isMatchingId = (expenseId > 0 && loadedId == expenseId) || (expenseId == 0 && (loadedId == 0 || loadedId == null))
        
        if (isMatchingId && keywords.isNotEmpty()) {
             // If we have matching keywords, use them.
             // We overwrite even if selectedKeywordIds is not empty because this is the source of truth from DB/Clone
             // BUT we should respect user changes if they've already edited?
             // Actually, this effect runs when 'loadedKeywordIds' changes (load from DB).
             // Ideally we only set it initially.
             if (selectedKeywordIds.isEmpty()) {
                 selectedKeywordIds = keywords
             } else if (selectedKeywordIds != keywords) {
                 // Should we overwrite?
                 // If the user navigates between expenses, 'selectedKeywordIds' is rememberSaveable.
                 // It might hold the OLD expense's keywords if the key is the same (nav graph node).
                 // So yes, if the DB says "Expense 2 has Keyword B", and we are viewing Expense 2,
                 // and our state says "Keyword A" (stale), we should update to "Keyword B".
                 // BUT what if the user just added a keyword?
                 // The flow updates from DB only if saved.
                 // Wait, loadedKeywordIds comes from DB.
                 // If I just opened the screen, selectedKeywordIds might be stale.
                 // So we should overwrite it.
                 selectedKeywordIds = keywords
             }
        } else if (isMatchingId && keywords.isEmpty()) {
            // If DB/Clone says no keywords, and we match ID, clear current keywords
            // This fixes the case where Expense 1 (Keywords) -> Expense 2 (No Keywords)
            // Stale keywords would persist if we didn't clear them.
             if (selectedKeywordIds.isNotEmpty()) {
                 selectedKeywordIds = emptySet()
             }
        }
    }

    // Track saving state to prevent double-saves
    var isSaving by remember { mutableStateOf(false) }


    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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

    // State to track if the form has been initialized to prevent resetting on navigation return
    var hasInitialized by rememberSaveable { mutableStateOf(false) }

    // Update state if loading an existing expense from DB OR if we have a cloned expense
    // Also RESET fields when expense becomes null for new expense creation
    LaunchedEffect(expense, expenseId, copyFromId) {
        if (expense != null && expense!!.id == expenseId) {
            amount = expense!!.amount.toPlainString()
            accountName = expense!!.account
            category = expense!!.category
            currency = expense!!.currency
            expenseDate = expense!!.expenseDate
            comment = expense!!.comment ?: ""
            type = expense!!.type
            // Reset errors when loading valid expense data
            accountError = false
            categoryError = false
            hasInitialized = true
        } else if (expenseId == 0 && copyFromId == null) {
            // Creating new expense (not editing, not copying)
            // Only reset if we haven't initialized yet (to preserve state when returning from sub-screens)
            if (!hasInitialized) {
                // Force reset to initial values (empty or voice detection) to clear any stale state
                // This is CRITICAL for Voice Flow: prevent rememberSaveable from restoring stale data
                amount = initialAmount?.toPlainString() ?: ""
                comment = ""
                selectedKeywordIds = emptySet()
                
                // Reset to current date for new expenses
                expenseDate = if (initialExpenseDateMillis > 0L) initialExpenseDateMillis else System.currentTimeMillis()
                
                // Reset account and category
                accountName = initialAccountName ?: ""
                category = initialCategoryName ?: ""
                
                // Type comes from nav arg for new expenses
                type = initialType ?: "Expense"
                isDebt = false
                
                // Currency will be set when account is selected or default is applied
                currency = ""
                
                // If creating new from voice (and no cloned expense), try to set currency if account is valid
                if (initialAccountName != null && !initialAccountError) {
                    val account = accounts.find { it.name.equals(initialAccountName, ignoreCase = true) }
                    if (account != null) {
                        currency = account.currency
                    }
                }
                hasInitialized = true
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
    // Add accountName to keys so it re-runs when accountName is reset to empty
    LaunchedEffect(accounts, defaultExpenseAccountId, accountName) {
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
    // Use Box instead of Scaffold to avoid unwanted padding
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditExpenseScreenContent(
            modifier = Modifier.fillMaxSize(),
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
            defaultAccountUsed = defaultAccountUsed,
            isDebt = isDebt,
            relatedDebtId = expense?.relatedDebtId ?: relatedDebtId,
            debtId = debt?.id,
            debtPayments = debtPayments,
            debtPaidAmount = debtPaidAmount,
            debtPaymentConvertedAmounts = debtPaymentConvertedAmounts
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
            onDebtCheckedChange = { isDebt = it },

            onPaymentClick = { payment -> 
                navController.navigate("editExpense/${payment.id}") 
            },
            onAddPaymentClick = {
                 val paymentType = if (type == "Expense") "Income" else "Expense"
                 val relatedDebtIdVal = debt?.id
                 if (relatedDebtIdVal != null) {
                     // Prefill category from parent expense
                     val categoryNameArg = if (category.isNotEmpty()) "&categoryName=$category" else ""
                     navController.navigate("editExpense/0?type=$paymentType&relatedDebtId=$relatedDebtIdVal$categoryNameArg")
                 }
            },
            onAddExistingPaymentClick = {
                 val paymentType = if (type == "Expense") "Income" else "Expense"
                 scope.launch {
                     potentialPayments = viewModel.getPotentialDebtPayments(paymentType)
                     showAddExistingDialog = true
                 }
            },
            onRemovePayment = { paymentToRemove ->
                 scope.launch {
                     viewModel.unlinkExpenseFromDebt(paymentToRemove)
                 }
            },
            onSaveDebt = { expenseToSave, keywordIds, isDebtChecked ->
                 if (isSaving) return@EditExpenseCallbacks
                 isSaving = true
                
                 scope.launch {
                    // Update relatedDebtId in expense if passed
                    val finalExpense = if (relatedDebtId != null) expenseToSave.copy(relatedDebtId = relatedDebtId) else expenseToSave

                     // Prepare localized strings
                     val typeLabel = if (type == "Income") context.getString(R.string.tab_income) else context.getString(R.string.tab_expense)
                     val successMsg = context.getString(R.string.msg_saved, typeLabel)
                     val errorMsgFallback = context.getString(R.string.err_save_failed, typeLabel)

                     if (expenseId > 0) {
                        val result = viewModel.updateExpenseWithKeywordsAndReturn(finalExpense, keywordIds)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.fold(
                                onSuccess = {
                                    handleSaveSuccess(expenseId, isDebtChecked, successMsg, snackbarHostState, scope, navController, viewModel)
                                },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(error.message ?: errorMsgFallback)
                                    isSaving = false
                                }
                            )
                        }
                    } else {
                        val result = viewModel.insertExpenseWithKeywordsAndReturn(finalExpense, keywordIds)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.fold(
                                onSuccess = { createdExpenseId ->
                                    handleSaveSuccess(createdExpenseId.toInt(), isDebtChecked, successMsg, snackbarHostState, scope, navController, viewModel)
                                },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(error.message ?: errorMsgFallback)
                                    isSaving = false
                                }
                            )
                        }
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
                scope.launch {
                    val message = when {
                        accError -> context.getString(R.string.err_account_not_found)
                        catError -> context.getString(R.string.err_category_not_found)
                        amtError -> context.getString(R.string.err_amount_empty)
                        else -> null
                    }
                    if (message != null) {
                        snackbarHostState.showSnackbar(message)
                    }
                }
            },
            onCreateKeyword = { name ->
                viewModel.insertKeyword(name)
            },
            onShowSnackbar = { message ->
                scope.launch {
                    snackbarHostState.showSnackbar(message)
                }
            },
            onCopy = { sourceId ->
                navController.navigate("editExpense/0?copyFromId=$sourceId")
            },
            onHideKeyboard = {
                keyboardController?.hide()
            }
        )
        )
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    
    if (showAddExistingDialog) {
        val paymentType = if (type == "Expense") stringResource(R.string.tab_income) else stringResource(R.string.tab_expense)
        AlertDialog(
            onDismissRequest = { showAddExistingDialog = false },
            title = { Text(stringResource(R.string.title_select_existing_payment)) },
            text = {
                if (potentialPayments.isEmpty()) {
                    Text(stringResource(R.string.msg_no_unlinked_records, paymentType))
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(potentialPayments.size) { index ->
                            val payment = potentialPayments[index]
                            ListItem(
                                headlineContent = { Text("${payment.amount} ${payment.currency}") },
                                supportingContent = { 
                                    Text("${payment.category} • ${java.text.SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(payment.expenseDate))}") 
                                },
                                modifier = Modifier.clickable {
                                    val currentDebtId = debt?.id
                                    if (currentDebtId != null) {
                                        scope.launch {
                                            viewModel.linkExpenseToDebt(payment.id, currentDebtId)
                                            showAddExistingDialog = false
                                        }
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddExistingDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}

private suspend fun handleSaveSuccess(
    targetId: Int,
    isDebtChecked: Boolean,
    message: String,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavController,
    viewModel: ExpenseViewModel
) {
    // Logic:
    // If isDebtChecked = true AND existing debt = null -> Create Debt
    // If isDebtChecked = false AND existing debt != null -> Delete Debt
    val currentDebt = viewModel.getDebtForExpenseSync(targetId)
    
    if (isDebtChecked && currentDebt == null) {
        viewModel.createDebt(targetId)
    } else if (!isDebtChecked && currentDebt != null) {
        // Verify conditions again just in case (empty history, 0 paid) - enforced by UI but safe to double check or just delete
        viewModel.deleteDebt(currentDebt.id) 
    }

    scope.launch {
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }
    navController.navigateUp()
}
