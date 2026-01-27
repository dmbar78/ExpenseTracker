package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.ui.TestTags
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
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
    val date: Long = System.currentTimeMillis(),
    val comment: String = "",
    val existingTransfer: TransferHistory? = null,
    val isEditMode: Boolean = transferId > 0,
    val sourceAccountError: Boolean = false,
    val destAccountError: Boolean = false
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
    val onDateClick: () -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSave: (TransferHistory) -> Unit = {},
    val onDelete: (TransferHistory) -> Unit = {},
    val onShowSnackbar: (String) -> Unit = {} // Callback to show snackbar messages (owned by wrapper)
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
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSourceAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isDestAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var localSourceAccountName by remember(state.sourceAccountName) { mutableStateOf(state.sourceAccountName) }
    var localDestAccountName by remember(state.destAccountName) { mutableStateOf(state.destAccountName) }
    var localAmount by remember(state.amount) { mutableStateOf(state.amount) }
    var localCurrency by remember(state.currency) { mutableStateOf(state.currency) }
    var localComment by remember(state.comment) { mutableStateOf(state.comment) }
    
    // Track error states locally so they can be cleared on selection
    var showSourceError by remember(state.sourceAccountError) { mutableStateOf(state.sourceAccountError) }
    var showDestError by remember(state.destAccountError) { mutableStateOf(state.destAccountError) }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTags.EDIT_TRANSFER_ROOT),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp)
        ) {
            item {
                Text(
                    if (state.isEditMode) "Edit Transfer" else "Add Transfer",
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
                        label = { Text("Date") },
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
                        label = { Text("From") },
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
                            text = { Text("Create New…") },
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
                    Text(
                        "Source account not found. Please select a valid account.",
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
                        label = { Text("To") },
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
                            text = { Text("Create New…") },
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
                        "Destination account not found. Please select a valid account.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_ERROR_DEST_NOT_FOUND)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Amount field
                OutlinedTextField(
                    value = localAmount,
                    onValueChange = {
                        localAmount = it
                        callbacks.onAmountChange(it)
                    },
                    label = { Text("Amount") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.EDIT_TRANSFER_AMOUNT_FIELD),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Currency (read-only)
                OutlinedTextField(
                    value = localCurrency,
                    onValueChange = {},
                    label = { Text("Currency") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_TRANSFER_CURRENCY_VALUE),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Comment field
                OutlinedTextField(
                    value = localComment,
                    onValueChange = {
                        localComment = it
                        callbacks.onCommentChange(it)
                    },
                    label = { Text("Comment") },
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
                            callbacks.onShowSnackbar("Please select both accounts.")
                            return@Button
                        }
                        
                        // Case-insensitive account validation
                        val resolvedSourceAccount = accounts.find { it.name.equals(localSourceAccountName, ignoreCase = true) }
                        val resolvedDestAccount = accounts.find { it.name.equals(localDestAccountName, ignoreCase = true) }
                        
                        if (resolvedSourceAccount == null) {
                            showSourceError = true
                            callbacks.onShowSnackbar("Source account not found. Please select a valid account.")
                            return@Button
                        }
                        if (resolvedDestAccount == null) {
                            showDestError = true
                            callbacks.onShowSnackbar("Destination account not found. Please select a valid account.")
                            return@Button
                        }
                        
                        val parsedAmount = parseTransferMoneyInputContent(localAmount)
                        if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
                            callbacks.onShowSnackbar("Please enter a valid amount.")
                            return@Button
                        }
                        
                        // Case-insensitive same-account check using canonical names
                        if (resolvedSourceAccount.name.equals(resolvedDestAccount.name, ignoreCase = true)) {
                            callbacks.onShowSnackbar("Source and destination accounts must be different.")
                            return@Button
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
                                date = state.date,
                                comment = localComment
                            )
                            callbacks.onSave(newTransfer)
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_TRANSFER_SAVE)
                ) {
                    Text("Save")
                }
                if (state.isEditMode) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).padding(start = 8.dp).testTag(TestTags.EDIT_TRANSFER_DELETE)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && state.existingTransfer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transfer") },
            text = { Text("Are you sure you want to delete this transfer?") },
            confirmButton = {
                Button(
                    onClick = {
                        callbacks.onDelete(state.existingTransfer)
                        showDeleteDialog = false
                    },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DELETE_CONFIRM)
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag(TestTags.EDIT_TRANSFER_DELETE_DISMISS)
                ) {
                    Text("No")
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
