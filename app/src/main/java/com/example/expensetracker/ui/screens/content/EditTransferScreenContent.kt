package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.ui.TestTags
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.example.expensetracker.R
import java.util.*

/**
 * State holder for EditTransferScreen content.
 */
data class EditTransferState(
    val transferId: Int = 0,
    val sourceAccountName: String = "",
    val destAccountName: String = "",
    val amount: String = "",
    val currency: String = "",
    val destAmount: String = "",
    val destCurrency: String = "",
    val date: Long = System.currentTimeMillis(),
    val comment: String = "",
    val existingTransfer: TransferHistory? = null,
    val isEditMode: Boolean = transferId > 0,
    val sourceAccountError: Boolean = false,
    val destAccountError: Boolean = false,
    val defaultAccountUsed: Boolean = false
)

/**
 * Callbacks for EditTransferScreen content.
 */
data class EditTransferCallbacks(
    val onSourceAccountSelect: (Account) -> Unit = {},
    val onCreateNewSourceAccount: (currentAccountText: String) -> Unit = {},
    val onDestAccountSelect: (Account) -> Unit = {},
    val onCreateNewDestAccount: (currentAccountText: String) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onDestAmountChange: (String) -> Unit = {},
    val onDateClick: () -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSave: (TransferHistory) -> Unit = {},
    val onDelete: (TransferHistory) -> Unit = {},
    val onShowSnackbar: (String) -> Unit = {},
    val onCopy: (Int) -> Unit = {}
)

/**
 * Pure UI content composable for EditTransferScreen.
 * Accepts state + callbacks, no ViewModel or NavController dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransferScreenContent(
    state: EditTransferState,
    accounts: List<Account>,
    callbacks: EditTransferCallbacks,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSourceAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isDestAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var localSourceAccountName by remember(state.sourceAccountName) { mutableStateOf(state.sourceAccountName) }
    var localDestAccountName by remember(state.destAccountName) { mutableStateOf(state.destAccountName) }
    var localAmount by remember(state.amount) { mutableStateOf(state.amount) }
    var localCurrency by remember(state.currency) { mutableStateOf(state.currency) }
    var localDestAmount by remember(state.destAmount) { mutableStateOf(state.destAmount) }
    var localDestCurrency by remember(state.destCurrency) { mutableStateOf(state.destCurrency) }
    var localComment by remember(state.comment) { mutableStateOf(state.comment) }
    
    // Track error states locally so they can be cleared on selection
    var showSourceError by remember(state.sourceAccountError, state.defaultAccountUsed) { mutableStateOf(state.sourceAccountError || state.defaultAccountUsed) }
    var showDestError by remember(state.destAccountError) { mutableStateOf(state.destAccountError) }

    // Conditional visibility of Destination Amount/Currency
    // Logic: Visible if destination currency differs from source currency (and both are known).
    val areCurrenciesDifferent = localCurrency.isNotEmpty() && localDestCurrency.isNotEmpty() && localCurrency != localDestCurrency
    
    // Auto-update localDestCurrency if dest account changes
    LaunchedEffect(localDestAccountName, accounts) {
        val account = accounts.find { it.name.equals(localDestAccountName, ignoreCase = true) }
        if (account != null) {
            localDestCurrency = account.currency
        }
    }

    // Clear dest amount if currencies become same
    LaunchedEffect(areCurrenciesDifferent) {
        if (!areCurrenciesDifferent) {
            localDestAmount = ""
            // We don't verify if it matches existingTransfer logic here, relying on state init
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTags.EDIT_TRANSFER_ROOT),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 88.dp)
        ) {
            item {
                Text(
                    if (state.isEditMode) stringResource(R.string.title_edit_transfer) else stringResource(R.string.title_add_transfer),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Date field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { callbacks.onDateClick() }
                        .testTag(TestTags.EDIT_TRANSFER_DATE_FIELD)
                ) {
                    OutlinedTextField(
                        value = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(state.date),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.lbl_date)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Source Account dropdown
                ExposedDropdownMenuBox(
                    expanded = isSourceAccountDropdownExpanded,
                    onExpandedChange = { isSourceAccountDropdownExpanded = !isSourceAccountDropdownExpanded },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_SOURCE_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = localSourceAccountName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.lbl_from)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSourceAccountDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag(TestTags.EDIT_TRANSFER_SOURCE_VALUE)
                            .then(if (showSourceError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                        isError = showSourceError
                    )
                    ExposedDropdownMenu(
                        expanded = isSourceAccountDropdownExpanded,
                        onDismissRequest = { isSourceAccountDropdownExpanded = false }
                    ) {
                        // "Create New..." option
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.option_create_new)) },
                            onClick = {
                                isSourceAccountDropdownExpanded = false
                                callbacks.onCreateNewSourceAccount(localSourceAccountName)
                            },
                            modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_SOURCE_CREATE_NEW)
                        )

                        if (accounts.isNotEmpty()) {
                            HorizontalDivider()
                        }

                        accounts.forEach { accountItem ->
                            DropdownMenuItem(
                                text = { Text(accountItem.name) },
                                onClick = {
                                    localSourceAccountName = accountItem.name
                                    localCurrency = accountItem.currency
                                    showSourceError = false // Clear error on selection
                                    isSourceAccountDropdownExpanded = false
                                    callbacks.onSourceAccountSelect(accountItem)
                                },
                                modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + "source_" + accountItem.id)
                            )
                        }
                    }
                }
                if (showSourceError) {
                    val errorText = if (state.defaultAccountUsed && localSourceAccountName == state.sourceAccountName)
                        stringResource(R.string.err_account_not_found_default)
                    else
                        stringResource(R.string.err_source_account_not_found)
                        
                    Text(
                        errorText,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_ERROR_SOURCE_NOT_FOUND)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Destination Account dropdown
                ExposedDropdownMenuBox(
                    expanded = isDestAccountDropdownExpanded,
                    onExpandedChange = { isDestAccountDropdownExpanded = !isDestAccountDropdownExpanded },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DESTINATION_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = localDestAccountName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.lbl_to)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDestAccountDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag(TestTags.EDIT_TRANSFER_DESTINATION_VALUE)
                            .then(if (showDestError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                        isError = showDestError
                    )
                    ExposedDropdownMenu(
                        expanded = isDestAccountDropdownExpanded,
                        onDismissRequest = { isDestAccountDropdownExpanded = false }
                    ) {
                        // "Create New..." option
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.option_create_new)) },
                            onClick = {
                                isDestAccountDropdownExpanded = false
                                callbacks.onCreateNewDestAccount(localDestAccountName)
                            },
                            modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DEST_CREATE_NEW)
                        )

                        if (accounts.isNotEmpty()) {
                            HorizontalDivider()
                        }

                        accounts.forEach { accountItem ->
                            DropdownMenuItem(
                                text = { Text(accountItem.name) },
                                onClick = {
                                    localDestAccountName = accountItem.name
                                    localDestCurrency = accountItem.currency
                                    showDestError = false // Clear error on selection
                                    isDestAccountDropdownExpanded = false
                                    callbacks.onDestAccountSelect(accountItem)
                                },
                                modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + "dest_" + accountItem.id)
                            )
                        }
                    }
                }
                if (showDestError) {
                    Text(
                        stringResource(R.string.err_dest_account_not_found),
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val focusRequester = remember { FocusRequester() }

                // Auto-focus source amount field when creating a NEW transfer via plus button (empty amount)
                LaunchedEffect(Unit) {
                    if (state.transferId == 0 && state.amount.isEmpty()) {
                        focusRequester.requestFocus()
                    }
                }

                // Source Amount Row (Amount + Currency)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = localAmount,
                        onValueChange = {
                            localAmount = it
                            callbacks.onAmountChange(it)
                        },
                        label = { Text(stringResource(R.string.lbl_source_amount)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (localCurrency.isNotEmpty()) {
                                    Text(
                                        text = localCurrency,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .testTag(TestTags.EDIT_TRANSFER_CURRENCY_VALUE),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            }
                        }
                    )
                }

                // Destination Amount Row (Visible only if currencies differ)
                if (areCurrenciesDifferent) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = localDestAmount,
                            onValueChange = {
                                localDestAmount = it
                                callbacks.onDestAmountChange(it)
                            },
                            label = { Text(stringResource(R.string.lbl_dest_amount)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TestTags.EDIT_TRANSFER_DEST_AMOUNT_FIELD), // Needs new TestTag? Or reuse? Using simplified string for now to avoid compilation error if Tag missing.
                                // Actually, I should add TestTags. For now, I'll assume users wants functionality. 
                                // I'll use a string literal description if TestTag complicates things, or stick with no TestTag for now, 
                                // but TestTags.EDIT_TRANSFER_AMOUNT_FIELD exists. 
                                // I'll add a new test tag or just use modifier for now without tag if I can't edit TestTags.kt easily. 
                                // Wait, I should edit TestTags.kt? I'll just skip tag for now or use strict string.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                if (localDestCurrency.isNotEmpty()) {
                                    Text(
                                        text = localDestCurrency,
                                        modifier = Modifier.padding(end = 12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Comment field
                OutlinedTextField(
                    value = localComment,
                    onValueChange = {
                        localComment = it
                        callbacks.onCommentChange(it)
                    },
                    label = { Text(stringResource(R.string.lbl_comment)) },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_TRANSFER_COMMENT_FIELD)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Floating Buttons
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        // Prevent duplicate saves
                        if (isSaving) return@Button
                        
                        // Validate inputs
                        if (localSourceAccountName.isBlank() || localDestAccountName.isBlank()) {
                            callbacks.onShowSnackbar(context.getString(R.string.err_select_both_accounts))
                            return@Button
                        }
                        
                        // Case-insensitive account validation
                        val resolvedSourceAccount = accounts.find { it.name.equals(localSourceAccountName, ignoreCase = true) }
                        val resolvedDestAccount = accounts.find { it.name.equals(localDestAccountName, ignoreCase = true) }
                        
                        if (resolvedSourceAccount == null) {
                            showSourceError = true
                            callbacks.onShowSnackbar(context.getString(R.string.err_source_account_not_found))
                            return@Button
                        }
                        if (resolvedDestAccount == null) {
                            showDestError = true
                            callbacks.onShowSnackbar(context.getString(R.string.err_dest_account_not_found))
                            return@Button
                        }
                        
                        val parsedAmount = parseTransferMoneyInputContent(localAmount)
                        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
                            callbacks.onShowSnackbar(context.getString(R.string.err_invalid_source_amount))
                            return@Button
                        }
                        
                        // Case-insensitive same-account check using canonical names
                        if (resolvedSourceAccount.name.equals(resolvedDestAccount.name, ignoreCase = true)) {
                            callbacks.onShowSnackbar(context.getString(R.string.err_same_accounts))
                            return@Button
                        }
                        
                        // Multi-currency validation
                        var parsedDestAmount: BigDecimal? = null
                        if (resolvedSourceAccount.currency != resolvedDestAccount.currency) {
                            parsedDestAmount = parseTransferMoneyInputContent(localDestAmount)
                            if (parsedDestAmount == null || parsedDestAmount <= BigDecimal.ZERO) {
                                callbacks.onShowSnackbar(context.getString(R.string.err_invalid_dest_amount))
                                return@Button
                            }
                        }
                        
                        // Clear error states since validation passed
                        showSourceError = false
                        showDestError = false
                        isSaving = true
                        
                        if (state.isEditMode && state.existingTransfer != null) {
                            val updatedTransfer = state.existingTransfer.copy(
                                sourceAccount = resolvedSourceAccount.name, // Use canonical name
                                destinationAccount = resolvedDestAccount.name, // Use canonical name
                                amount = parsedAmount.setScale(2, RoundingMode.HALF_UP),
                                currency = resolvedSourceAccount.currency,
                                destinationAmount = parsedDestAmount?.setScale(2, RoundingMode.HALF_UP),
                                destinationCurrency = resolvedDestAccount.currency,
                                date = state.date,
                                comment = localComment
                            )
                            callbacks.onSave(updatedTransfer)
                        } else {
                            // Create mode - new transfer
                            val newTransfer = TransferHistory(
                                sourceAccount = resolvedSourceAccount.name, // Use canonical name
                                destinationAccount = resolvedDestAccount.name, // Use canonical name
                                amount = parsedAmount.setScale(2, RoundingMode.HALF_UP),
                                currency = resolvedSourceAccount.currency,
                                destinationAmount = parsedDestAmount?.setScale(2, RoundingMode.HALF_UP),
                                destinationCurrency = resolvedDestAccount.currency,
                                date = state.date,
                                comment = localComment
                            )
                            callbacks.onSave(newTransfer)
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_TRANSFER_SAVE)
                ) {
                    Text(stringResource(R.string.btn_save))
                }
                if (state.isEditMode) {
                    // Copy Button
                    Button(
                        onClick = { callbacks.onCopy(state.transferId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag(TestTags.COPY_BUTTON)
                    ) {
                        Text(stringResource(R.string.btn_copy))
                    }

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).padding(start = 8.dp).testTag(TestTags.EDIT_TRANSFER_DELETE)
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && state.existingTransfer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.title_delete_transfer)) },
            text = { Text(stringResource(R.string.msg_delete_transfer_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        callbacks.onDelete(state.existingTransfer)
                        showDeleteDialog = false
                    },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DELETE_CONFIRM)
                ) {
                    Text(stringResource(R.string.btn_yes))
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DELETE_DISMISS)
                ) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        )
    }
}

/**
 * Parses a money input string handling both dot and comma decimal separators.
 */
internal fun parseTransferMoneyInputContent(input: String): BigDecimal? {
    if (input.isBlank()) return null

    val cleaned = input.trim()
    val lastDotIndex = cleaned.lastIndexOf('.')
    val lastCommaIndex = cleaned.lastIndexOf(',')

    val normalizedString = when {
        lastDotIndex == -1 && lastCommaIndex == -1 -> cleaned
        lastCommaIndex == -1 -> {
            val afterDot = cleaned.length - lastDotIndex - 1
            if (cleaned.count { it == '.' } == 1 && afterDot <= 2) {
                cleaned
            } else {
                cleaned.replace(".", "")
            }
        }
        lastDotIndex == -1 -> {
            val afterComma = cleaned.length - lastCommaIndex - 1
            if (cleaned.count { it == ',' } == 1 && afterComma <= 2) {
                cleaned.replace(',', '.')
            } else {
                cleaned.replace(",", "")
            }
        }
        lastDotIndex > lastCommaIndex -> {
            cleaned.replace(",", "")
        }
        else -> {
            cleaned.replace(".", "").replace(',', '.')
        }
    }

    return try {
        BigDecimal(normalizedString).setScale(2, RoundingMode.HALF_UP)
    } catch (e: NumberFormatException) {
        null
    }
}
