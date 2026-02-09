package com.example.expensetracker.ui.screens.content

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Account
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.Keyword
import com.example.expensetracker.ui.TestTags
import kotlinx.coroutines.launch
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
    val existingExpense: Expense? = null,
    val selectedKeywordIds: Set<Int> = emptySet(),
    val defaultAccountUsed: Boolean = false,
    // Debt fields
    val isDebt: Boolean = false,
    val relatedDebtId: Int? = null, // If paying a debt, this is the debt ID
    val debtId: Int? = null, // If this is a debt, this is its ID
    val debtPayments: List<Expense> = emptyList(), // History of payments if this is a debt
    val debtPaidAmount: BigDecimal = BigDecimal.ZERO, // Total paid amount if this is a debt
    val debtPaymentConvertedAmounts: Map<Int, BigDecimal> = emptyMap() // Helper to show converted amounts
)

/**
 * Callbacks for EditExpenseScreen content.
 * Tests can provide fake implementations to verify behavior.
 */
data class EditExpenseCallbacks(
    val onAmountChange: (String) -> Unit = {},
    val onAccountSelect: (Account) -> Unit = {},
    val onCreateNewAccount: (currentAccountText: String) -> Unit = {},
    val onCategorySelect: (Category) -> Unit = {},
    val onCreateNewCategory: (currentCategoryText: String) -> Unit = {},
    val onDateClick: () -> Unit = {},
    val onCommentChange: (String) -> Unit = {},
    val onSave: (Expense) -> Unit = {},
    val onSaveWithKeywords: (Expense, Set<Int>) -> Unit = { _, _ -> },
    val onSaveDebt: (Expense, Set<Int>, Boolean) -> Unit = { _, _, _ -> }, // New save callback with debt flag
    val onDelete: (Expense) -> Unit = {},
    val onValidationFailed: (accountError: Boolean, categoryError: Boolean, amountError: Boolean) -> Unit = { _, _, _ -> },
    val onCreateKeyword: suspend (String) -> Long = { 0L },
    val onDebtCheckedChange: (Boolean) -> Unit = {},
    val onPaymentClick: (Expense) -> Unit = {},
    val onAddPaymentClick: () -> Unit = {},
    val onAddExistingPaymentClick: () -> Unit = {},
    val onRemovePayment: (Expense) -> Unit = {},
    val onShowSnackbar: (String) -> Unit = {},
    val onHideKeyboard: () -> Unit = {},
    val onCopy: (Int) -> Unit = {}
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
    keywords: List<Keyword>,
    callbacks: EditExpenseCallbacks,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }
    var isKeywordDropdownExpanded by remember { mutableStateOf(false) }
    var showCreateKeywordDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Local mutable state for form fields (copy from state initially)
    var localAmount by remember(state.amount) { mutableStateOf(state.amount) }
    var localAccountName by remember(state.accountName) { mutableStateOf(state.accountName) }
    var localCategory by remember(state.category) { mutableStateOf(state.category) }
    var localCurrency by remember(state.currency) { mutableStateOf(state.currency) }
    var localComment by remember(state.comment) { mutableStateOf(state.comment) }
    var localAccountError by remember(state.accountError, state.defaultAccountUsed) { mutableStateOf(state.accountError || state.defaultAccountUsed) }
    var localCategoryError by remember(state.categoryError) { mutableStateOf(state.categoryError) }
    var localAmountError by remember(state.amountError) { mutableStateOf(state.amountError) }
    
    // Keyword state
    var localSelectedKeywordIds by remember(state.selectedKeywordIds) { mutableStateOf(state.selectedKeywordIds) }
    var keywordQuery by remember { mutableStateOf("") }
    var newKeywordName by remember { mutableStateOf("") }

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding() // Resize list when keyboard opens
                .testTag(TestTags.EDIT_EXPENSE_ROOT),
            // Bottom padding to clear the floating buttons (approx 88dp) + navigation bars
            contentPadding = PaddingValues(
                start = 16.dp, 
                end = 16.dp, 
                top = 0.dp, 
                bottom = 88.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item {
                val headerText = if (state.expenseId > 0) "Edit ${state.type}" else "Add ${state.type}"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(headerText, style = MaterialTheme.typography.headlineSmall)
                    
                    // Debt Checkbox
                    // 1. Not visible if this is a child payment (has relatedDebtId)
                    // 2. Editable if: New Record OR (History empty AND paid amount == 0)
                    if (state.relatedDebtId == null) {
                        val isEditable = state.expenseId == 0 || (state.debtPayments.isEmpty() && state.debtPaidAmount <= BigDecimal.ZERO)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = isEditable) {
                                callbacks.onDebtCheckedChange(!state.isDebt)
                            }
                        ) {
                            Checkbox(
                                checked = state.isDebt,
                                onCheckedChange = { callbacks.onDebtCheckedChange(it) },
                                enabled = isEditable
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Debt")
                        }
                    }
                }
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
                        // "Create New..." option
                        DropdownMenuItem(
                            text = { Text("Create New…") },
                            onClick = {
                                isAccountDropdownExpanded = false
                                callbacks.onCreateNewAccount(localAccountName)
                            },
                            modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_ACCOUNT_CREATE_NEW)
                        )

                        if (accounts.isNotEmpty()) {
                            HorizontalDivider()
                        }

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
                    val errorText = if (state.defaultAccountUsed && localAccountName == state.accountName)
                        "Account not found. Default account is used. You can change it from the menu."
                    else
                        "Account not found. Please select a valid account."

                    Text(
                        errorText,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.EDIT_EXPENSE_AMOUNT_FIELD),
                    isError = localAmountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        if (localCurrency.isNotEmpty()) {
                            Text(
                                text = localCurrency,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .testTag(TestTags.EDIT_EXPENSE_CURRENCY_VALUE),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                        onValueChange = {
                            localCategory = it
                            localCategoryError = false
                            isCategoryDropdownExpanded = true
                            // Do not call onCategorySelect here to avoid cursor jumps due to state recombination
                        },
                        label = { Text("Category") },
                        // readOnly = true, // Removed to allow typing
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                            .testTag(TestTags.EDIT_EXPENSE_CATEGORY_VALUE)
                            .then(if (localCategoryError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                        isError = localCategoryError,
                    )

                    // Filter categories (Cached)
                    val filteredCategories = remember(localCategory, categories) {
                        categories.filter {
                            it.name.contains(localCategory, ignoreCase = true)
                        }.sortedBy { it.name }
                    }

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
                        
                        filteredCategories.forEach { categoryItem ->
                            DropdownMenuItem(
                                text = { Text(categoryItem.name) },
                                onClick = {
                                    localCategory = categoryItem.name
                                    localCategoryError = false
                                    isCategoryDropdownExpanded = false
                                    callbacks.onCategorySelect(categoryItem)
                                    callbacks.onHideKeyboard()
                                },
                                modifier = Modifier.testTag(TestTags.CATEGORY_OPTION_PREFIX + categoryItem.id)
                            )
                        }

                        if (filteredCategories.isEmpty() && localCategory.isNotEmpty()) {
                             DropdownMenuItem(
                                text = { Text("(No match found)") },
                                onClick = { },
                                enabled = false
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

                Spacer(modifier = Modifier.height(8.dp))

                // Keywords section
                Text("Keywords", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                
                // Selected keywords chips
                if (localSelectedKeywordIds.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        keywords.filter { it.id in localSelectedKeywordIds }.forEach { keyword ->
                            KeywordChip(
                                label = keyword.name,
                                onRemove = { localSelectedKeywordIds = localSelectedKeywordIds - keyword.id }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Keywords dropdown with search
                ExposedDropdownMenuBox(
                    expanded = isKeywordDropdownExpanded,
                    onExpandedChange = { isKeywordDropdownExpanded = !isKeywordDropdownExpanded },
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_KEYWORD_DROPDOWN)
                ) {
                    OutlinedTextField(
                        value = keywordQuery,
                        onValueChange = { 
                            keywordQuery = it
                            isKeywordDropdownExpanded = true 
                        },
                        label = { Text("Search or add keywords") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isKeywordDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                            .testTag(TestTags.EDIT_EXPENSE_KEYWORD_SEARCH),
                    )
                    ExposedDropdownMenu(
                        expanded = isKeywordDropdownExpanded,
                        onDismissRequest = { isKeywordDropdownExpanded = false }
                    ) {
                        // "Create New..." option
                        DropdownMenuItem(
                            text = { Text("Create New…") },
                            onClick = {
                                newKeywordName = keywordQuery
                                showCreateKeywordDialog = true
                            },
                            modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_NEW)
                        )
                        
                        // Build filtered + sorted keyword list: selected first, then matching by query (Cached)
                        val trimmedKeywordQuery = keywordQuery.trim()
                        val filteredKeywords = remember(keywords, trimmedKeywordQuery, localSelectedKeywordIds) {
                            keywords
                                .filter { trimmedKeywordQuery.isBlank() || it.name.contains(trimmedKeywordQuery, ignoreCase = true) }
                                .sortedWith(compareByDescending<Keyword> { it.id in localSelectedKeywordIds }.thenBy { it.name })
                        }
                        
                        if (filteredKeywords.isNotEmpty()) {
                            HorizontalDivider()
                        }
                        
                        filteredKeywords.forEach { keyword ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = keyword.id in localSelectedKeywordIds,
                                            onCheckedChange = null // Handled by row click
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(keyword.name)
                                    }
                                },
                                onClick = {
                                    localSelectedKeywordIds = if (keyword.id in localSelectedKeywordIds) {
                                        localSelectedKeywordIds - keyword.id
                                    } else {
                                        localSelectedKeywordIds + keyword.id
                                    }
                                    callbacks.onHideKeyboard()
                                    keywordQuery = ""
                                    // Don't close dropdown on selection
                                },
                                modifier = Modifier.testTag(TestTags.KEYWORD_OPTION_PREFIX + keyword.id)
                            )
                        }
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
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_EXPENSE_COMMENT_FIELD)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Payment History for Debt
                if (state.isDebt) {
                    Text(
                        "Payment History (Paid: ${state.debtPaidAmount.setScale(2, RoundingMode.HALF_UP)} ${state.currency})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.debtPayments.isEmpty()) {
                        Text(
                            "No payments yet",
                            style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.debtPayments.forEach { payment ->
                             Surface(
                                 modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { callbacks.onPaymentClick(payment) },
                                 shape = RoundedCornerShape(8.dp),
                                 color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                             ) {
                                 Row(
                                     modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                         val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                         Text(
                                             text = dateFormat.format(Date(payment.expenseDate)),
                                             style = MaterialTheme.typography.bodyMedium
                                         )
                                     
                                     
                                     // Currency conversion display
                                     Column(horizontalAlignment = Alignment.End) {
                                         Text(
                                             text = "${payment.amount} ${payment.currency}",
                                             style = MaterialTheme.typography.bodyLarge,
                                             fontWeight = FontWeight.Bold
                                         )
                                         
                                         // Show converted amount if currency differs
                                         if (payment.currency != state.currency) {
                                             val converted = state.debtPaymentConvertedAmounts[payment.id]
                                             if (converted != null) {
                                                  Text(
                                                     text = "(≈ ${converted.setScale(2, RoundingMode.HALF_UP)} ${state.currency})",
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                                  )
                                             }
                                         }
                                     }
                                     
                                     // Remove Button
                                     IconButton(
                                         onClick = { callbacks.onRemovePayment(payment) },
                                         modifier = Modifier.testTag("removePaymentButton_${payment.id}")
                                     ) {
                                         Icon(
                                             imageVector = Icons.Default.Remove,
                                             contentDescription = "Remove Payment",
                                             tint = MaterialTheme.colorScheme.error
                                         )
                                     }
                                 }
                             }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Add Payment Button
                    // Logic: If Debt is Expense, add Income; if Debt is Income, add Expense
                    // The '+' Button here is specific to this debt.
                    // Actually, the requirement says "The '+' button should open EditExpenseScreen of type Income if a debt is created from an expense..."
                    // Is this the main FAB or a button here?
                    // "Make the 'Debt' text clickable... The '+' button should open..."
                    // It implies a way to add payment. A dedicated button here is clear.
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { 
                                if (state.expenseId > 0 && state.debtId != null) {
                                    callbacks.onAddPaymentClick() 
                                } else {
                                    val msg = "Save ${state.type.lowercase(Locale.getDefault())} before creating the payment!"
                                    callbacks.onShowSnackbar(msg)
                                }
                            },
                            modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("addPaymentButton")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add New")
                        }

                        Button(
                            onClick = {
                                if (state.expenseId > 0 && state.debtId != null) {
                                    callbacks.onAddExistingPaymentClick()
                                } else {
                                    val msg = "Save ${state.type.lowercase(Locale.getDefault())} before adding payments!"
                                    callbacks.onShowSnackbar(msg)
                                }
                            },
                            modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("addExistingPaymentButton")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Existing")
                        }
                    }
                }

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
                        
                        // Validation with case-insensitive matching
                        var isValid = true
                        val resolvedAccount = accounts.find { it.name.equals(localAccountName, ignoreCase = true) }
                        val resolvedCategory = categories.find { it.name.equals(localCategory, ignoreCase = true) }
                        val newAccountError = localAccountName.isBlank() || resolvedAccount == null
                        val newCategoryError = localCategory.isBlank() || resolvedCategory == null
                        val parsedAmount = parseMoneyInput(localAmount)
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
                            // Clear error states since validation passed
                            localAccountError = false
                            localCategoryError = false
                            localAmountError = false
                            isSaving = true
                            
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
                            callbacks.onSaveDebt(expenseToSave, localSelectedKeywordIds, state.isDebt)
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(TestTags.EDIT_EXPENSE_SAVE)
                ) {
                    Text("Save")
                }
                if (state.expenseId > 0) {
                    Button(
                        onClick = { callbacks.onCopy(state.expenseId) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag(TestTags.COPY_BUTTON)
                    ) {
                        Text("Copy")
                    }
                    
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
        val hasRelatedPayments = state.debtPayments.isNotEmpty()
        val title = if (hasRelatedPayments) "Delete Record?" else "Delete ${state.type}"
        val message = if (hasRelatedPayments) {
            "There're related payments. If you delete this record, they will be also deleted. Are you sure?"
        } else {
            "Are you sure you want to delete this ${state.type}?"
        }
        
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        callbacks.onDelete(state.existingExpense)
                        showDeleteDialog = false
                    },
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_DELETE_CONFIRM)
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_DELETE_DISMISS)
                ) {
                    Text("No")
                }
            }
        )
    }
    
    // Create keyword dialog
    if (showCreateKeywordDialog) {
        AlertDialog(
            onDismissRequest = { showCreateKeywordDialog = false },
            title = { Text("Create Keyword") },
            text = {
                OutlinedTextField(
                    value = newKeywordName,
                    onValueChange = { newKeywordName = it },
                    label = { Text("Keyword name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(TestTags.EDIT_EXPENSE_KEYWORD_NEW_NAME)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeywordName.isNotBlank()) {
                            coroutineScope.launch {
                                val newId = callbacks.onCreateKeyword(newKeywordName.trim())
                                if (newId > 0) {
                                    localSelectedKeywordIds = localSelectedKeywordIds + newId.toInt()
                                }
                                newKeywordName = ""
                                keywordQuery = ""
                                showCreateKeywordDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_CONFIRM)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = { 
                        showCreateKeywordDialog = false
                        newKeywordName = ""
                    },
                    modifier = Modifier.testTag(TestTags.EDIT_EXPENSE_KEYWORD_CREATE_DISMISS)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Chip displaying a selected keyword with a remove button.
 */
@Composable
private fun KeywordChip(
    label: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove keyword",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Parses a money input string handling both dot and comma decimal separators.
 */
internal fun parseMoneyInput(input: String): BigDecimal? {
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
