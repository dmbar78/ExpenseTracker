package com.example.expensetracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.expensetracker.data.Currency
import com.example.expensetracker.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExpenseViewModel,
    navController: NavController
) {
    val currencies by viewModel.allCurrencies.collectAsState()
    val defaultCurrencyCode by viewModel.defaultCurrencyCode.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var selectedCurrency by remember(defaultCurrencyCode) { mutableStateOf(defaultCurrencyCode) }
    
    // State for the EUR pivot rate prompt
    var showPivotRateDialog by remember { mutableStateOf(false) }
    var pivotRateInput by remember { mutableStateOf("") }
    var pendingCurrencyCode by remember { mutableStateOf("") }
    var isCheckingPivot by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Default Currency setting
        SettingsItem(
            title = "Default currency",
            value = defaultCurrencyCode,
            onClick = { 
                selectedCurrency = defaultCurrencyCode
                showCurrencyPicker = true 
            }
        )
    }
    
    // Currency picker dialog
    if (showCurrencyPicker) {
        DefaultCurrencyPickerDialog(
            currencies = currencies,
            selectedCurrencyCode = selectedCurrency,
            isCheckingPivot = isCheckingPivot,
            onCurrencySelected = { newCode ->
                // When selection changes, check if EUR pivot exists
                if (newCode != selectedCurrency && newCode != "EUR") {
                    isCheckingPivot = true
                    scope.launch {
                        val pivotExists = viewModel.ensureEurPivotExists(newCode)
                        isCheckingPivot = false
                        if (pivotExists) {
                            selectedCurrency = newCode
                        } else {
                            // No pivot available - prompt for manual entry
                            pendingCurrencyCode = newCode
                            pivotRateInput = ""
                            showPivotRateDialog = true
                        }
                    }
                } else {
                    selectedCurrency = newCode
                }
            },
            onConfirm = {
                viewModel.setDefaultCurrency(selectedCurrency)
                showCurrencyPicker = false
            },
            onDismiss = { showCurrencyPicker = false }
        )
    }
    
    // EUR pivot rate entry dialog
    if (showPivotRateDialog) {
        EurPivotRateDialog(
            currencyCode = pendingCurrencyCode,
            rateInput = pivotRateInput,
            onRateInputChange = { pivotRateInput = it },
            onConfirm = {
                val rate = parseRateInput(pivotRateInput)
                if (rate != null && rate > BigDecimal.ZERO) {
                    scope.launch {
                        viewModel.setManualEurPivot(pendingCurrencyCode, rate)
                        selectedCurrency = pendingCurrencyCode
                    }
                    showPivotRateDialog = false
                    pivotRateInput = ""
                }
            },
            onDismiss = {
                // Cancel - keep previous selection
                showPivotRateDialog = false
                pivotRateInput = ""
            }
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "($value)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultCurrencyPickerDialog(
    currencies: List<Currency>,
    selectedCurrencyCode: String,
    isCheckingPivot: Boolean,
    onCurrencySelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Default Currency") },
        text = {
            Column {
                Text(
                    text = "Choose the currency to use for totals and conversions.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Currency dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isCheckingPivot) expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCurrencyCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = {
                            if (isCheckingPivot) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isCheckingPivot
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded && !isCheckingPivot,
                        onDismissRequest = { expanded = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("${currency.code} - ${currency.name}") },
                                onClick = {
                                    expanded = false
                                    onCurrencySelected(currency.code)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isCheckingPivot
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EurPivotRateDialog(
    currencyCode: String,
    rateInput: String,
    onRateInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isValidRate = remember(rateInput) {
        val rate = parseRateInput(rateInput)
        rate != null && rate > BigDecimal.ZERO
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Exchange Rate") },
        text = {
            Column {
                Text(
                    text = "No exchange rate available for $currencyCode. Please enter the EUR to $currencyCode rate:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = onRateInputChange,
                    label = { Text("1 EUR = ? $currencyCode") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = rateInput.isNotBlank() && !isValidRate,
                    supportingText = if (rateInput.isNotBlank() && !isValidRate) {
                        { Text("Please enter a valid rate greater than 0") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValidRate
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Parse rate input handling both dot and comma decimal separators.
 */
private fun parseRateInput(input: String): BigDecimal? {
    if (input.isBlank()) return null
    return try {
        val normalized = input.trim().replace(",", ".")
        BigDecimal(normalized)
    } catch (e: NumberFormatException) {
        null
    }
}
