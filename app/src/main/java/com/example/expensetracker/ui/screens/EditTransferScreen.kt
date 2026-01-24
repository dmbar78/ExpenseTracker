package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.example.expensetracker.ui.screens.content.EditTransferCallbacks
import com.example.expensetracker.ui.screens.content.EditTransferScreenContent
import com.example.expensetracker.ui.screens.content.EditTransferState
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import java.util.*

/**
 * Thin wrapper for EditTransferScreen.
 * - Maps navigation args to state
 * - Owns SnackbarHostState for error messages
 * - Collects errorFlow from ViewModel for snackbar display
 * - Collects navigateBackFlow for successful save navigation
 * - Manages DatePickerDialog side-effect
 * - Delegates all UI rendering to EditTransferScreenContent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransferScreen(
    transferId: Int,
    viewModel: ExpenseViewModel,
    navController: NavController,
    initialSourceAccountName: String? = null,
    initialDestAccountName: String? = null,
    initialAmount: BigDecimal? = null,
    initialTransferDateMillis: Long = 0L,
    initialSourceAccountError: Boolean = false,
    initialDestAccountError: Boolean = false
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val isEditMode = transferId > 0
    
    // Track error states
    var showSourceError by rememberSaveable { mutableStateOf(initialSourceAccountError) }
    var showDestError by rememberSaveable { mutableStateOf(initialDestAccountError) }

    // Load existing transfer if in edit mode
    LaunchedEffect(transferId) {
        if (isEditMode) {
            viewModel.loadTransfer(transferId)
        }
    }

    // Track saving state to prevent double-saves
    var isSaving by remember { mutableStateOf(false) }


    // Observe result from AddAccountScreen (Create New Account flow)
    val createdAccountNameState = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>("createdAccountName")
        ?.observeAsState()
    
    // Track which dropdown triggered the create flow
    var lastCreateNewTarget by rememberSaveable { mutableStateOf("") } // "source" or "dest"

    // Collect error messages from ViewModel and show as Snackbar
    LaunchedEffect(Unit) {
        viewModel.errorFlow.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val transfer by viewModel.selectedTransfer.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()

    // Initialize state with prefill values if provided
    var sourceAccountName by rememberSaveable { mutableStateOf(initialSourceAccountName ?: "") }
    var destAccountName by rememberSaveable { mutableStateOf(initialDestAccountName ?: "") }
    var amount by rememberSaveable { mutableStateOf(initialAmount?.toPlainString() ?: "") }
    var currency by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(if (initialTransferDateMillis > 0) initialTransferDateMillis else System.currentTimeMillis()) }
    var comment by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth)
            date = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Update state from loaded transfer in edit mode
    LaunchedEffect(transfer) {
        if (isEditMode) {
            transfer?.let {
                sourceAccountName = it.sourceAccount
                destAccountName = it.destinationAccount
                amount = it.amount.toPlainString()
                currency = it.currency
                date = it.date
                comment = it.comment ?: ""
            }
        }
    }
    
    // For voice prefill: resolve currency from source account if source is valid
    LaunchedEffect(accounts, initialSourceAccountName, initialSourceAccountError) {
        if (!isEditMode && initialSourceAccountName != null && !initialSourceAccountError && accounts.isNotEmpty()) {
            val sourceAccount = accounts.find { it.name.equals(initialSourceAccountName, ignoreCase = true) }
            if (sourceAccount != null) {
                currency = sourceAccount.currency
            }
        }
    }
    
    // Reactive Error Clearing for Source Account
    LaunchedEffect(accounts, sourceAccountName, showSourceError) {
        if (showSourceError && sourceAccountName.isNotEmpty()) {
            val account = accounts.find { it.name.equals(sourceAccountName, ignoreCase = true) }
            if (account != null) {
                showSourceError = false
                currency = account.currency
            }
        }
        // Update currency even if no error, if it's missing
        if (currency.isEmpty() && sourceAccountName.isNotEmpty()) {
            val account = accounts.find { it.name.equals(sourceAccountName, ignoreCase = true) }
            if (account != null) {
                currency = account.currency
            }
        }
    }
    
    // Reactive Error Clearing for Destination Account
    LaunchedEffect(accounts, destAccountName, showDestError) {
        if (showDestError && destAccountName.isNotEmpty()) {
            val account = accounts.find { it.name.equals(destAccountName, ignoreCase = true) }
            if (account != null) {
                showDestError = false
            }
        }
    }
    
    // When a new account is created via "Create New...", set it as the selected account
    LaunchedEffect(createdAccountNameState?.value, accounts) {
        val newName = createdAccountNameState?.value
        if (!newName.isNullOrEmpty()) {
            // First check if account is in the list yet
            val createdAccount = accounts.find { it.name.equals(newName, ignoreCase = true) }
            if (createdAccount != null) {
                // Account is now in the list - update state and clear savedStateHandle
                if (lastCreateNewTarget == "source") {
                    sourceAccountName = newName
                    showSourceError = false
                    currency = createdAccount.currency
                } else if (lastCreateNewTarget == "dest") {
                    destAccountName = newName
                    showDestError = false
                }
                // Only clear AFTER we've found and processed the account
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<String>("createdAccountName")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Delegate UI to Content composable
        EditTransferScreenContent(
            state = EditTransferState(
                transferId = transferId,
                sourceAccountName = sourceAccountName,
                destAccountName = destAccountName,
                amount = amount,
                currency = currency,
                date = date,
                comment = comment,
                existingTransfer = if (isEditMode) transfer else null,
                isEditMode = isEditMode,
                sourceAccountError = showSourceError,
                destAccountError = showDestError
            ),
            accounts = accounts,
            callbacks = EditTransferCallbacks(
                onSourceAccountSelect = { selectedAccount ->
                    sourceAccountName = selectedAccount.name
                    currency = selectedAccount.currency
                    showSourceError = false
                },
                onCreateNewSourceAccount = { currentSourceText ->
                    lastCreateNewTarget = "source"
                    navController.navigate("addAccount?accountName=${currentSourceText.trim()}")
                },
                onDestAccountSelect = { selectedAccount ->
                    destAccountName = selectedAccount.name
                    showDestError = false
                },
                onCreateNewDestAccount = { currentDestText ->
                    lastCreateNewTarget = "dest"
                    navController.navigate("addAccount?accountName=${currentDestText.trim()}")
                },
                onAmountChange = { amount = it },
                onDateClick = { datePickerDialog.show() },
                onCommentChange = { comment = it },
                onSave = { transferToSave ->
                    if (isSaving) return@EditTransferCallbacks
                    isSaving = true
                    
                    scope.launch {
                        val result = if (isEditMode) {
                            viewModel.updateTransferAndReturn(transferToSave)
                        } else {
                            viewModel.insertTransferAndReturn(transferToSave)
                        }
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.fold(
                                onSuccess = {
                                    val message = if (isEditMode) "Transfer updated" else "Transfer created"
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    navController.popBackStack()
                                },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(error.message ?: "Failed to save transfer.")
                                    isSaving = false
                                }
                            )
                        }
                    }
                },
                onDelete = { transferToDelete ->
                    viewModel.deleteTransfer(transferToDelete)
                    navController.popBackStack()
                },
                onShowSnackbar = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            ),
            modifier = Modifier.padding(paddingValues)
        )
    }
}
