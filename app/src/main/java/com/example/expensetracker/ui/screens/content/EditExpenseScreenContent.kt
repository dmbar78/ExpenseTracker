package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Expense
import com.example.expensetracker.ui.TestTags
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

/**
 * State holder for EditExpenseScreen content.
 * This allows tests to provide deterministic state without needing a ViewModel.
 */
data class EditExpenseState(
    val expenseId: Int = 0,
    val amount: String = "",
    val accountName: String = "",
    val category: String = "",
    val currency: String = "",
    val expenseDate: Long = System.currentTimeMillis(),
    val comment: String = "",
    val type: String = "Expense",
    val accountError: Boolean = false,
    val categoryError: Boolean = false,
    val amountError: Boolean = false,
    val existingExpense: Expense? = null
)

/**
 * Callbacks for EditExpenseScreen content.
 * Tests can provide fake implementations to verify behavior.
 */
data class EditExpenseCallbacks(
    val onAmountChange: (String) -> Unit = {},
    val onAccountSelect: (Account) -> Unit = {},
    val onCategorySelect: (Category) -> Unit = {},
    val onCreateNewCategory: (currentCategoryText: String) -> Unit = {},
    val onDateClick: () -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSave: (Expense) -> Unit = {},
    val onDelete: (Expense) -> Unit = {},
    val onValidationFailed: (accountError: Boolean, categoryError: Boolean, amountError: Boolean) -> Unit = { _, _, _ -> }
)

/**
 * Pure UI content composable for EditExpenseScreen.
 * Accepts state + lists + callbacks, no ViewModel or NavController dependencies.
 * This can be tested in isolation with fake state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreenContent(
    state: EditExpenseState,
    accounts: List<Account>,
    categories: List<Category>,
    callbacks: EditExpenseCallbacks,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    
    // Local mutable state for form fields (copy from state initially)
    var localAmount by remember(state.amount) { mutableStateOf(state.amount) }
    var localAccountName by remember(state.accountName) { mutableStateOf(state.accountName) }
    var localCategory by remember(state.category) { mutableStateOf(state.category) }
    var localCurrency by remember(state.currency) { mutableStateOf(state.currency) }
    var localComment by remember(state.comment) { mutableStateOf(state.comment) }
    var localAccountError by remember(state.accountError) { mutableStateOf(state.accountError) }
    var localCategoryError by remember(state.categoryError) { mutableStateOf(state.categoryError) }
    var localAmountError by remember(state.amountError) { mutableStateOf(state.amountError) }

    LazyColumn(modifier = modifier.padding(16.dp).testTag(TestTags.EDIT_EXPENSE_ROOT)) {
        item {
            val headerText = if (state.expenseId > 0) "Edit ${state.type}" else "Add ${state.type}"
            Text(headerText, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            // Date field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { callbacks.onDateClick() }
                    .testTag(TestTags.EDIT_EXPENSE_DATE_FIELD)
            ) {
                OutlinedTextField(
                    value = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(state.expenseDate),
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

            // Account dropdown
            ExposedDropdownMenuBox(
                expanded = isAccountDropdownExpanded,
                onExpandedChange = { isAccountDropdownExpanded = !isAccountDropdownExpanded },
                modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_ACCOUNT_DROPDOWN)
            ) {
                OutlinedTextField(
                    value = localAccountName,
                    onValueChange = {},
                    label = { Text("Account") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag(TestTags.EDIT_EXPENSE_ACCOUNT_VALUE)
                        .then(if (localAccountError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                    isError = localAccountError
                )
                ExposedDropdownMenu(
                    expanded = isAccountDropdownExpanded,
                    onDismissRequest = { isAccountDropdownExpanded = false }
                ) {
                    accounts.forEach { accountItem ->
                        DropdownMenuItem(
                            text = { Text(accountItem.name) },
                            onClick = {
                                localAccountName = accountItem.name
                                localCurrency = accountItem.currency
                                localAccountError = false
                                isAccountDropdownExpanded = false
                                callbacks.onAccountSelect(accountItem)
                            },
                            modifier = Modifier.testTag(TestTags.ACCOUNT_OPTION_PREFIX + accountItem.id)
                        )
                    }
                }
            }
            if (localAccountError) {
                Text(
                    "Account not found. Please select a valid account.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_ERROR_ACCOUNT_NOT_FOUND)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount field
            OutlinedTextField(
                value = localAmount,
                onValueChange = {
                    localAmount = it
                    localAmountError = false
                    callbacks.onAmountChange(it)
                },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD),
                isError = localAmountError
            )
            if (localAmountError) {
                Text(
                    "Amount cannot be empty.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_ERROR_AMOUNT)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = isCategoryDropdownExpanded,
                onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_CATEGORY_DROPDOWN)
            ) {
                OutlinedTextField(
                    value = localCategory,
                    onValueChange = {},
                    label = { Text("Category") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE)
                        .then(if (localCategoryError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                    isError = localCategoryError
                )
                ExposedDropdownMenu(
                    expanded = isCategoryDropdownExpanded,
                    onDismissRequest = { isCategoryDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Create New...") },
                        onClick = {
                            isCategoryDropdownExpanded = false
                            callbacks.onCreateNewCategory(localCategory)
                        },
                        modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_CATEGORY_CREATE_NEW)
                    )
                    HorizontalDivider()
                    categories.forEach { categoryItem ->
                        DropdownMenuItem(
                            text = { Text(categoryItem.name) },
                            onClick = {
                                localCategory = categoryItem.name
                                localCategoryError = false
                                isCategoryDropdownExpanded = false
                                callbacks.onCategorySelect(categoryItem)
                            },
                            modifier = Modifier.testTag(TestTags.CATEGORY_OPTION_PREFIX + categoryItem.id)
                        )
                    }
                }
            }
            if (localCategoryError) {
                Text(
                    "Category not found. Please select a valid category.",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_ERROR_CATEGORY_NOT_FOUND)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Currency (read-only)
            OutlinedTextField(
                value = localCurrency,
                onValueChange = {},
                label = { Text("Currency") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_EXPENSE_CURRENCY_VALUE),
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
                modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_EXPENSE_COMMENT_FIELD)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        // Validation with case-insensitive matching
                        var isValid = true
                        val resolvedAccount = accounts.find { it.name.equals(localAccountName, ignoreCase = true) }
                        val resolvedCategory = categories.find { it.name.equals(localCategory, ignoreCase = true) }
                        val newAccountError = localAccountName.isBlank() || resolvedAccount == null
                        val newCategoryError = localCategory.isBlank() || resolvedCategory == null
                        val parsedAmount = parseMoneyInputContent(localAmount)
                        val newAmountError = parsedAmount == null || parsedAmount <= BigDecimal.ZERO

                        if (newAccountError) {
                            localAccountError = true
                            isValid = false
                        }
                        if (newCategoryError) {
                            localCategoryError = true
                            isValid = false
                        }
                        if (newAmountError) {
                            localAmountError = true
                            isValid = false
                        }

                        if (!isValid) {
                            callbacks.onValidationFailed(newAccountError, newCategoryError, newAmountError)
                            return@Button
                        }

                        if (parsedAmount != null && resolvedAccount != null && resolvedCategory != null) {
                            val expenseToSave = if (state.expenseId > 0 && state.existingExpense != null) {
                                state.existingExpense.copy(
                                    account = resolvedAccount.name, // Use canonical name
                                    amount = parsedAmount.setScale(2, RoundingMode.HALF_UP),
                                    category = resolvedCategory.name, // Use canonical name
                                    currency = resolvedAccount.currency,
                                    expenseDate = state.expenseDate,
                                    comment = localComment
                                )
                            } else {
                                Expense(
                                    account = resolvedAccount.name, // Use canonical name
                                    amount = parsedAmount.setScale(2, RoundingMode.HALF_UP),
                                    category = resolvedCategory.name, // Use canonical name
                                    currency = resolvedAccount.currency,
                                    expenseDate = state.expenseDate,
                                    type = state.type,
                                    comment = localComment
                                )
                            }
                            callbacks.onSave(expenseToSave)
                        }
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_EXPENSE_SAVE)
                ) {
                    Text("Save")
                }
                if (state.expenseId > 0) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).padding(start = 8.dp).testTag(TestTags.EDIT_EXPENSE_DELETE)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && state.existingExpense != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${state.type}") },
            text = { Text("Are you sure you want to delete this ${state.type}?") },
            confirmButton = {
                Button(
                    onClick = {
                        callbacks.onDelete(state.existingExpense)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

/**
 * Parses a money input string handling both dot and comma decimal separators.
 */
internal fun parseMoneyInputContent(input: String): BigDecimal? {
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
