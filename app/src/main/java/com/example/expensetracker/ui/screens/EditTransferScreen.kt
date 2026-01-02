package com.example.expensetracker.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.TransferHistory
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransferScreen(transferId: Int, viewModel: ExpenseViewModel, navController: NavController) {
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transferId) {
        viewModel.loadTransfer(transferId)
    }

    // This will listen for the signal from the ViewModel to navigate back
    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest {
            navController.popBackStack()
        }
    }

    val transfer by viewModel.selectedTransfer.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()

    var sourceAccountName by remember { mutableStateOf("") }
    var destAccountName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var comment by remember { mutableStateOf("") }

    var isSourceAccountDropdownExpanded by remember { mutableStateOf(false) }
    var isDestAccountDropdownExpanded by remember { mutableStateOf(false) }

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

    LaunchedEffect(transfer) {
        transfer?.let {
            sourceAccountName = it.sourceAccount
            destAccountName = it.destinationAccount
            amount = it.amount.toPlainString()
            currency = it.currency
            date = it.date
            comment = it.comment ?: ""
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("Edit Transfer", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() }) {
                OutlinedTextField(
                    value = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(date),
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
                expanded = isSourceAccountDropdownExpanded,
                onExpandedChange = { isSourceAccountDropdownExpanded = !isSourceAccountDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = sourceAccountName,
                    onValueChange = {},
                    label = { Text("From") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSourceAccountDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isSourceAccountDropdownExpanded,
                    onDismissRequest = { isSourceAccountDropdownExpanded = false }
                ) {
                    accounts.forEach { accountItem ->
                        DropdownMenuItem(
                            text = { Text(accountItem.name) },
                            onClick = {
                                sourceAccountName = accountItem.name
                                currency = accountItem.currency
                                isSourceAccountDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = isDestAccountDropdownExpanded,
                onExpandedChange = { isDestAccountDropdownExpanded = !isDestAccountDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = destAccountName,
                    onValueChange = {},
                    label = { Text("To") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDestAccountDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isDestAccountDropdownExpanded,
                    onDismissRequest = { isDestAccountDropdownExpanded = false }
                ) {
                    accounts.forEach { accountItem ->
                        DropdownMenuItem(
                            text = { Text(accountItem.name) },
                            onClick = {
                                destAccountName = accountItem.name
                                isDestAccountDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth()
            )

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
                        transfer?.let {
                            val parsedAmount = parseTransferMoneyInput(amount) ?: it.amount
                            val updatedTransfer = it.copy(
                                sourceAccount = sourceAccountName,
                                destinationAccount = destAccountName,
                                amount = parsedAmount.setScale(2, RoundingMode.HALF_UP),
                                currency = currency,
                                date = date,
                                comment = comment
                            )
                            viewModel.updateTransfer(updatedTransfer)
                        }
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Save")
                }
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Transfer") },
            text = { Text("Are you sure you want to delete this transfer?") },
            confirmButton = {
                Button(
                    onClick = {
                        transfer?.let { viewModel.deleteTransfer(it) }
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

/**
 * Parses a money input string handling both dot and comma decimal separators.
 * Supports formats like: "1234.56", "1,234.56", "1234,56", "1.234,56"
 * Returns a BigDecimal or null if parsing fails.
 */
private fun parseTransferMoneyInput(input: String): BigDecimal? {
    if (input.isBlank()) return null
    
    val cleaned = input.trim()
    
    // Determine which format is used based on the last separator
    val lastDotIndex = cleaned.lastIndexOf('.')
    val lastCommaIndex = cleaned.lastIndexOf(',')
    
    val normalizedString = when {
        // No separators - just digits
        lastDotIndex == -1 && lastCommaIndex == -1 -> cleaned
        // Only dots - could be decimal or thousand separator
        lastCommaIndex == -1 -> {
            // If there's only one dot and it has 1-2 digits after it, treat as decimal
            val afterDot = cleaned.length - lastDotIndex - 1
            if (cleaned.count { it == '.' } == 1 && afterDot <= 2) {
                cleaned // e.g., "1234.56"
            } else {
                // Multiple dots or > 2 digits after = thousand separators, remove them
                cleaned.replace(".", "") // e.g., "1.234.567" -> "1234567"
            }
        }
        // Only commas - could be decimal or thousand separator
        lastDotIndex == -1 -> {
            // If there's only one comma and it has 1-2 digits after it, treat as decimal
            val afterComma = cleaned.length - lastCommaIndex - 1
            if (cleaned.count { it == ',' } == 1 && afterComma <= 2) {
                cleaned.replace(',', '.') // e.g., "1234,56" -> "1234.56"
            } else {
                // Multiple commas or > 2 digits after = thousand separators, remove them
                cleaned.replace(",", "") // e.g., "1,234,567" -> "1234567"
            }
        }
        // Both dot and comma present
        lastDotIndex > lastCommaIndex -> {
            // Dot is the decimal separator (US format: 1,234.56)
            cleaned.replace(",", "") // Remove thousand separators
        }
        else -> {
            // Comma is the decimal separator (EU format: 1.234,56)
            cleaned.replace(".", "").replace(',', '.')
        }
    }
    
    return try {
        BigDecimal(normalizedString).setScale(2, RoundingMode.HALF_UP)
    } catch (e: NumberFormatException) {
        null
    }
}
