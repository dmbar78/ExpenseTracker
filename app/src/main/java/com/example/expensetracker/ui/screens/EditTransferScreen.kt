package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
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
import androidx.compose.ui.res.stringResource
import com.example.expensetracker.R
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
    initialDestAmount: BigDecimal? = null,
    initialTransferDateMillis: Long = 0L,
    initialSourceAccountError: Boolean = false,
    initialDestAccountError: Boolean = false,
    defaultAccountUsed: Boolean = false,
    copyFromId: Int? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val context = LocalContext.current
    val isEditMode = transferId > 0
    
    // Track error states
    var showSourceError by rememberSaveable { mutableStateOf(initialSourceAccountError) }
    var showDestError by rememberSaveable { mutableStateOf(initialDestAccountError) }

    // Load from DB if editing, copying, OR creating new (to clear any previous clone state)
    LaunchedEffect(transferId, copyFromId) {
        // Always call loadTransfer to ensure clean state
        viewModel.loadTransfer(transferId, copyFromId)
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

    // CRITICAL: Clear any stale cloned data SYNCHRONOUSLY before observing flows
    remember(transferId, copyFromId) {
        if (transferId == 0 && copyFromId == null) {
            viewModel.clearClonedTransfer()
        }
        true
    }

    val transfer by viewModel.selectedTransfer.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val defaultTransferAccountId by viewModel.defaultTransferAccountId.collectAsState()

    // Initialize state with prefill values if provided
    var sourceAccountName by rememberSaveable { mutableStateOf(initialSourceAccountName ?: "") }
    var destAccountName by rememberSaveable { mutableStateOf(initialDestAccountName ?: "") }
    var amount by rememberSaveable { mutableStateOf(initialAmount?.toPlainString() ?: "") }
    var destAmount by rememberSaveable { mutableStateOf(initialDestAmount?.toPlainString() ?: "") }
    var currency by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(if (initialTransferDateMillis > 0) initialTransferDateMillis else System.currentTimeMillis()) }
    var comment by rememberSaveable { mutableStateOf("") }


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

    // Track if we've already initialized to prevent overwriting user input on recomposition/restoration
    var hasInitialized by rememberSaveable { mutableStateOf(false) }

    // Update state from loaded transfer in edit mode or clone mode
    // Also RESET fields when creating new transfer
    LaunchedEffect(transfer, transferId, copyFromId) {
        if (isEditMode || copyFromId != null) {
            transfer?.let {
                sourceAccountName = it.sourceAccount
                destAccountName = it.destinationAccount
                amount = it.amount.toPlainString()
                destAmount = it.destinationAmount?.toPlainString() ?: ""
                currency = it.currency
                date = it.date
                comment = it.comment ?: ""
                hasInitialized = true
            }
        } else if (transferId == 0 && copyFromId == null && transfer == null) {
            // Creating new transfer - reset form fields ONLY ONCE
            if (!hasInitialized) {
                // Force reset to initial values (empty or voice) to clear any stale state
                // This is CRITICAL for Voice Flow to prevent rememberSaveable from restoring stale data
                sourceAccountName = initialSourceAccountName ?: ""
                destAccountName = initialDestAccountName ?: ""
                amount = initialAmount?.toPlainString() ?: ""
                destAmount = initialDestAmount?.toPlainString() ?: ""
                comment = ""
                
                date = if (initialTransferDateMillis > 0L) initialTransferDateMillis else System.currentTimeMillis()
                currency = ""
                
                // If creating new from voice, try to set currency if source account is valid
                // We replicate this here because the separate LaunchedEffect might have already run and we just overwrote currency=""
                if (initialSourceAccountName != null && !initialSourceAccountError) {
                    val sourceAccount = accounts.find { it.name.equals(initialSourceAccountName, ignoreCase = true) }
                    if (sourceAccount != null) {
                        currency = sourceAccount.currency
                    }
                }
                hasInitialized = true
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
    
    // Pre-populate default source account logic
    // Add sourceAccountName to keys to re-trigger when reset
    LaunchedEffect(accounts, defaultTransferAccountId, sourceAccountName) {
        if (!isEditMode && sourceAccountName.isEmpty() && initialSourceAccountName.isNullOrBlank()) {
             defaultTransferAccountId?.let { id ->
                 val defaultAccount = accounts.find { it.id == id }
                 if (defaultAccount != null) {
                     sourceAccountName = defaultAccount.name
                     currency = defaultAccount.currency
                 }
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

    // Use Box to avoid implicit Scaffold padding
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditTransferScreenContent(
            modifier = Modifier.fillMaxSize(),
            state = EditTransferState(
                transferId = transferId,
                sourceAccountName = sourceAccountName,
                destAccountName = destAccountName,
                amount = amount,
                destAmount = destAmount,
                currency = currency,
                date = date,
                comment = comment,
                existingTransfer = if (isEditMode) transfer else null,
                isEditMode = isEditMode,
                sourceAccountError = showSourceError,
                destAccountError = showDestError,
                defaultAccountUsed = defaultAccountUsed
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
                onDestAmountChange = { destAmount = it },
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
                                    val message = if (isEditMode) context.getString(R.string.msg_transfer_updated) else context.getString(R.string.msg_transfer_created)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    navController.popBackStack()
                                },
                                onFailure = { error ->
                                    snackbarHostState.showSnackbar(error.message ?: context.getString(R.string.err_transfer_save_failed))
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
                },
                onCopy = { sourceId ->
                     navController.navigate("editTransfer/0?copyFromId=$sourceId")
                }
            )
        )
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
