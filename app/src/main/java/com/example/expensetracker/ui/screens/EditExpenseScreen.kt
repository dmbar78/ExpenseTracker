package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Expense
import com.example.expensetracker.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseScreen(
    expenseId: Int,
    viewModel: ExpenseViewModel,
    navController: NavController,
    initialAccountName: String? = null,
    initialAmount: Double? = null,
    initialCategoryName: String? = null,
    initialType: String? = null,
    initialAccountError: Boolean = false,
    initialCategoryError: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }

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
    var amount by rememberSaveable { mutableStateOf(initialAmount?.toString() ?: "") }
    var accountName by rememberSaveable { mutableStateOf(initialAccountName ?: "") }
    var category by rememberSaveable { mutableStateOf(initialCategoryName ?: "") }
    var currency by rememberSaveable { mutableStateOf("") }
    var expenseDate by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var comment by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(initialType ?: "Expense") }
    
    // Error states using rememberSaveable
    var accountError by rememberSaveable { mutableStateOf(initialAccountError) }
    var categoryError by rememberSaveable { mutableStateOf(initialCategoryError) }
    var amountError by rememberSaveable { mutableStateOf(false) }

    var isAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            calendar.set(year, month, dayOfMonth)
            expenseDate = calendar.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

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
                amount = it.amount.toString()
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

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            val headerText = if (expenseId > 0) "Edit $type" else "Add $type"
            Text(headerText, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() }) {
                OutlinedTextField(
                    value = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(expenseDate),
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

            ExposedDropdownMenuBox(
                expanded = isAccountDropdownExpanded,
                onExpandedChange = { isAccountDropdownExpanded = !isAccountDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = {},
                    label = { Text("Account") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAccountDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .then(if (accountError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                    isError = accountError
                )
                ExposedDropdownMenu(
                    expanded = isAccountDropdownExpanded,
                    onDismissRequest = { isAccountDropdownExpanded = false }
                ) {
                    accounts.forEach { accountItem ->
                        DropdownMenuItem(
                            text = { Text(accountItem.name) },
                            onClick = {
                                accountName = accountItem.name
                                currency = accountItem.currency
                                accountError = false // Clear error on selection
                                isAccountDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            if (accountError) {
                Text("Account not found. Please select a valid account.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { 
                    amount = it
                    amountError = false
                },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                isError = amountError
            )
            if (amountError) {
                Text("Amount cannot be empty.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = isCategoryDropdownExpanded,
                onExpandedChange = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    label = { Text("Category") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryDropdownExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .then(if (categoryError) Modifier.border(2.dp, Color.Red, RoundedCornerShape(4.dp)) else Modifier),
                    isError = categoryError
                )
                ExposedDropdownMenu(
                    expanded = isCategoryDropdownExpanded,
                    onDismissRequest = { isCategoryDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Create New...") },
                        onClick = {
                            isCategoryDropdownExpanded = false
                            navController.navigate("addCategory?categoryName=$category")
                        }
                    )
                    Divider()
                    categories.forEach { categoryItem ->
                        DropdownMenuItem(
                            text = { Text(categoryItem.name) },
                            onClick = {
                                category = categoryItem.name
                                categoryError = false // Clear error on selection
                                isCategoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }
             if (categoryError) {
                Text("Category not found. Please select a valid category.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = currency,
                onValueChange = {},
                label = { Text("Currency") },
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

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Comment") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        // Validation
                        var isValid = true
                        if (accountName.isBlank() || accounts.find { it.name == accountName } == null) {
                            accountError = true
                            isValid = false
                        }
                        if (category.isBlank() || categories.find { it.name == category } == null) {
                            categoryError = true
                            isValid = false
                        }
                        if (amount.toDoubleOrNull() == null || amount.toDouble() <= 0) {
                            amountError = true
                            isValid = false
                        }

                        if (isValid) {
                            val expenseToSave = if (expenseId > 0 && expense != null) {
                                expense!!.copy(
                                    account = accountName,
                                    amount = amount.toDouble(),
                                    category = category,
                                    currency = currency,
                                    expenseDate = expenseDate,
                                    comment = comment
                                )
                            } else {
                                // Create new expense
                                 Expense(
                                    account = accountName,
                                    amount = amount.toDouble(),
                                    category = category,
                                    currency = currency,
                                    expenseDate = expenseDate,
                                    type = type, // Use current type
                                    comment = comment
                                )
                            }
                            
                            if (expenseId > 0) {
                                viewModel.updateExpense(expenseToSave)
                            } else {
                                viewModel.insertExpense(expenseToSave)
                            }
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Save")
                }
                if (expenseId > 0) {
                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete $type") }, // Dynamic deletion title
            text = { Text("Are you sure you want to delete this $type?") }, // Dynamic deletion text
            confirmButton = {
                Button(
                    onClick = {
                        expense?.let { viewModel.deleteExpense(it) }
                        showDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}
