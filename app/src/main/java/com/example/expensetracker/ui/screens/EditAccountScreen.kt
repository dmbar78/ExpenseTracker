package com.example.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(accountId: Int, viewModel: ExpenseViewModel, navController: NavController) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(accountId) {
        viewModel.loadAccount(accountId)
    }

    val account by viewModel.selectedAccount.collectAsState()
    val currencies by viewModel.allCurrencies.collectAsState()

    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var currencyCode by remember { mutableStateOf("") }
    var isCurrencyDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(account) {
        account?.let {
            name = it.name
            balance = it.balance.toPlainString()
            currencyCode = it.currency
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorFlow.collectLatest { message ->
            errorMessage = message
            showErrorDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBackFlow.collectLatest { 
            navController.popBackStack()
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Edit Account", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Account Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = balance,
            onValueChange = { balance = it },
            label = { Text("Balance") },
            modifier = Modifier
                .fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = isCurrencyDropdownExpanded,
            onExpandedChange = { isCurrencyDropdownExpanded = !isCurrencyDropdownExpanded }
        ) {
            OutlinedTextField(
                value = currencies.find { it.code == currencyCode }?.name ?: "",
                onValueChange = {},
                label = { Text("Currency") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCurrencyDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isCurrencyDropdownExpanded,
                onDismissRequest = { isCurrencyDropdownExpanded = false }
            ) {
                currencies.forEach { currencyItem ->
                    DropdownMenuItem(
                        text = { Text(currencyItem.name) },
                        onClick = {
                            currencyCode = currencyItem.code
                            isCurrencyDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    account?.let {
                        val parsedBalance = parseEditAccountMoneyInput(balance) ?: it.balance
                        val updatedAccount = it.copy(
                            name = name,
                            balance = parsedBalance.setScale(2, RoundingMode.HALF_UP),
                            currency = currencyCode
                        )
                        viewModel.updateAccount(updatedAccount)
                    }
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("Save")
            }
            Button(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("Delete")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete this account?") },
            confirmButton = {
                Button(
                    onClick = {
                        account?.let { viewModel.deleteAccount(it) }
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
 * Supports formats like: "1234.56", "1,234.56", "1234,56", "1.234,56"
 * Returns a BigDecimal or null if parsing fails.
 */
private fun parseEditAccountMoneyInput(input: String): BigDecimal? {
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
